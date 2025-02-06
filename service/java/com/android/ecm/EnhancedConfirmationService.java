/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ecm;

import static android.app.ecm.EnhancedConfirmationManager.REASON_PACKAGE_RESTRICTED;
import static android.app.ecm.EnhancedConfirmationManager.REASON_PHONE_STATE;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.app.ecm.EnhancedConfirmationManager;
import android.app.ecm.IEnhancedConfirmationManager;
import android.app.role.RoleManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.SignedPackage;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.SystemConfigManager;
import android.os.UserHandle;
import android.permission.flags.Flags;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.PhoneLookup;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;
import com.android.permission.util.UserUtils;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Service for ECM (Enhanced Confirmation Mode).
 *
 * @see EnhancedConfirmationManager
 *
 * @hide
 */
@Keep
@FlaggedApi(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@SuppressLint("MissingPermission")
public class EnhancedConfirmationService extends SystemService {
    private static final String LOG_TAG = EnhancedConfirmationService.class.getSimpleName();

    private Map<String, List<byte[]>> mTrustedPackageCertDigests;
    private Map<String, List<byte[]>> mTrustedInstallerCertDigests;
    // A map of call ID to call type
    private final Map<String, Integer> mOngoingCalls = new ArrayMap<>();

    private static final int CALL_TYPE_UNTRUSTED = 0;
    private static final int CALL_TYPE_TRUSTED = 1;
    private static final int CALL_TYPE_EMERGENCY = 1 << 1;
    @IntDef(flag = true, value = {
            CALL_TYPE_UNTRUSTED,
            CALL_TYPE_TRUSTED,
            CALL_TYPE_EMERGENCY
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface CallType {}

    public EnhancedConfirmationService(@NonNull Context context) {
        super(context);
        LocalManagerRegistry.addManager(EnhancedConfirmationManagerLocal.class,
                new EnhancedConfirmationManagerLocalImpl(this));
    }

    private ContentResolver mContentResolver;
    private TelephonyManager mTelephonyManager;

    @GuardedBy("mUserAccessibilityManagers")
    private final Map<Integer, AccessibilityManager> mUserAccessibilityManagers =
            new ArrayMap<>();

    @Override
    public void onStart() {
        Context context = getContext();
        SystemConfigManager systemConfigManager = context.getSystemService(
                SystemConfigManager.class);
        mTrustedPackageCertDigests = toTrustedPackageMap(
                systemConfigManager.getEnhancedConfirmationTrustedPackages());
        mTrustedInstallerCertDigests = toTrustedPackageMap(
                systemConfigManager.getEnhancedConfirmationTrustedInstallers());

        publishBinderService(Context.ECM_ENHANCED_CONFIRMATION_SERVICE, new Stub());
        mContentResolver = getContext().getContentResolver();
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class);
    }

    private Map<String, List<byte[]>> toTrustedPackageMap(Set<SignedPackage> signedPackages) {
        ArrayMap<String, List<byte[]>> trustedPackageMap = new ArrayMap<>();
        for (SignedPackage signedPackage : signedPackages) {
            ArrayList<byte[]> certDigests = (ArrayList<byte[]>) trustedPackageMap.computeIfAbsent(
                    signedPackage.getPackageName(), packageName -> new ArrayList<>(1));
            certDigests.add(signedPackage.getCertificateDigest());
        }
        return trustedPackageMap;
    }

    void addOngoingCall(Call call) {
        if (!Flags.unknownCallPackageInstallBlockingEnabled()) {
            return;
        }
        if (call.getDetails() == null) {
            return;
        }
        mOngoingCalls.put(call.getDetails().getId(), getCallType(call));
    }

    void removeOngoingCall(String callId) {
        if (!Flags.unknownCallPackageInstallBlockingEnabled()) {
            return;
        }
        Integer returned = mOngoingCalls.remove(callId);
        if (returned == null) {
            // TODO b/379941144: Capture a bug report whenever this happens.
        }
    }

    void clearOngoingCalls() {
        mOngoingCalls.clear();
    }

    private @CallType int getCallType(Call call) {
        String number = getPhoneNumber(call);
        try {
            if (number != null && mTelephonyManager.isEmergencyNumber(number)) {
                return CALL_TYPE_EMERGENCY;
            }
        } catch (IllegalStateException | UnsupportedOperationException e) {
            // If either of these are thrown, the telephony service is not available on the current
            // device, either because the device lacks telephony calling, or the telephony service
            // is unavailable.
        }
        if (number != null) {
            return hasContactWithPhoneNumber(number) ? CALL_TYPE_TRUSTED : CALL_TYPE_UNTRUSTED;
        } else {
            return hasContactWithDisplayName(call.getDetails().getCallerDisplayName())
                    ? CALL_TYPE_TRUSTED : CALL_TYPE_UNTRUSTED;
        }
    }

    private String getPhoneNumber(Call call) {
        Uri handle = call.getDetails().getHandle();
        if (handle == null || handle.getScheme() == null) {
            return null;
        }
        if (!handle.getScheme().equals(PhoneAccount.SCHEME_TEL)) {
            return null;
        }
        return handle.getSchemeSpecificPart();
    }

    private boolean hasContactWithPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return false;
        }
        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber));
        String[] projection = new String[]{
                PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup._ID
        };
        try (Cursor res = mContentResolver.query(uri, projection, null, null)) {
            return res != null && res.getCount() > 0;
        }
    }

    private boolean hasContactWithDisplayName(String displayName) {
        if (displayName == null) {
            return false;
        }
        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = new String[]{PhoneLookup._ID};
        String selection = StructuredName.DISPLAY_NAME + " = ?";
        String[] selectionArgs = new String[]{displayName};
        try (Cursor res = mContentResolver.query(uri, projection, selection, selectionArgs, null)) {
            return res != null && res.getCount() > 0;
        }
    }

    private boolean hasCallOfType(@CallType int callType) {
        for (int ongoingCallType : mOngoingCalls.values()) {
            if (ongoingCallType == callType) {
                return true;
            }
        }
        return false;
    }

    private class Stub extends IEnhancedConfirmationManager.Stub {

        /** A map of ECM states to their corresponding app op states */
        @Retention(java.lang.annotation.RetentionPolicy.SOURCE)
        @IntDef(prefix = {"ECM_STATE_"}, value = {EcmState.ECM_STATE_NOT_GUARDED,
                EcmState.ECM_STATE_GUARDED, EcmState.ECM_STATE_GUARDED_AND_ACKNOWLEDGED,
                EcmState.ECM_STATE_IMPLICIT})
        private @interface EcmState {
            int ECM_STATE_NOT_GUARDED = AppOpsManager.MODE_ALLOWED;
            int ECM_STATE_GUARDED = AppOpsManager.MODE_ERRORED;
            int ECM_STATE_GUARDED_AND_ACKNOWLEDGED = AppOpsManager.MODE_IGNORED;
            int ECM_STATE_IMPLICIT = AppOpsManager.MODE_DEFAULT;
        }

        private static final ArraySet<String> PER_PACKAGE_PROTECTED_SETTINGS = new ArraySet<>();

        // Settings restricted when an untrusted call is ongoing. These must also be added to
        // PROTECTED_SETTINGS
        private static final ArraySet<String> UNTRUSTED_CALL_RESTRICTED_SETTINGS = new ArraySet<>();

        static {
            // Runtime permissions
            PER_PACKAGE_PROTECTED_SETTINGS.add(Manifest.permission.SEND_SMS);
            PER_PACKAGE_PROTECTED_SETTINGS.add(Manifest.permission.RECEIVE_SMS);
            PER_PACKAGE_PROTECTED_SETTINGS.add(Manifest.permission.READ_SMS);
            PER_PACKAGE_PROTECTED_SETTINGS.add(Manifest.permission.RECEIVE_MMS);
            PER_PACKAGE_PROTECTED_SETTINGS.add(Manifest.permission.RECEIVE_WAP_PUSH);
            PER_PACKAGE_PROTECTED_SETTINGS.add(Manifest.permission.READ_CELL_BROADCASTS);
            PER_PACKAGE_PROTECTED_SETTINGS.add(Manifest.permission_group.SMS);

            PER_PACKAGE_PROTECTED_SETTINGS.add(Manifest.permission.BIND_DEVICE_ADMIN);
            // App ops
            PER_PACKAGE_PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_BIND_ACCESSIBILITY_SERVICE);
            PER_PACKAGE_PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_ACCESS_NOTIFICATIONS);
            PER_PACKAGE_PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW);
            PER_PACKAGE_PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_GET_USAGE_STATS);
            PER_PACKAGE_PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_LOADER_USAGE_STATS);
            // Default application roles.
            PER_PACKAGE_PROTECTED_SETTINGS.add(RoleManager.ROLE_DIALER);
            PER_PACKAGE_PROTECTED_SETTINGS.add(RoleManager.ROLE_SMS);

            if (Flags.unknownCallPackageInstallBlockingEnabled()) {
                // Requesting package installs, limited during phone calls
                UNTRUSTED_CALL_RESTRICTED_SETTINGS.add(
                        AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES);
                UNTRUSTED_CALL_RESTRICTED_SETTINGS.add(
                        AppOpsManager.OPSTR_BIND_ACCESSIBILITY_SERVICE);
            }
        }

        private final @NonNull Context mContext;
        private final String mAttributionTag;
        private final AppOpsManager mAppOpsManager;
        private final PackageManager mPackageManager;

        Stub() {
            Context context = getContext();
            mContext = context;
            mAttributionTag = context.getAttributionTag();
            mAppOpsManager = context.getSystemService(AppOpsManager.class);
            mPackageManager = context.getPackageManager();
        }

        public boolean isRestricted(@NonNull String packageName, @NonNull String settingIdentifier,
                @UserIdInt int userId) {
            return getRestrictionReason(packageName, settingIdentifier, userId) != null;
        }

        public String getRestrictionReason(@NonNull String packageName,
                @NonNull String settingIdentifier,
                @UserIdInt int userId) {
            enforcePermissions("isRestricted", userId);
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return null;
            }

            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");
            Preconditions.checkStringNotEmpty(settingIdentifier,
                    "settingIdentifier cannot be null or empty");

            try {
                if (!isSettingEcmProtected(settingIdentifier)) {
                    return null;
                }
                if (isSettingEcmGuardedForPackage(settingIdentifier, packageName, userId)) {
                    return REASON_PACKAGE_RESTRICTED;
                }
                String globalProtectionReason =
                        getGlobalProtectionReason(settingIdentifier, packageName, userId);
                if (globalProtectionReason != null) {
                    return globalProtectionReason;
                }
                return null;
            } catch (NameNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

        public void clearRestriction(@NonNull String packageName, @UserIdInt int userId) {
            enforcePermissions("clearRestriction", userId);
            if (!UserUtils.isUserExistent(userId, getContext())) {
                return;
            }

            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");

            try {
                int state = getAppEcmState(packageName, userId);
                boolean isAllowed = state == EcmState.ECM_STATE_GUARDED_AND_ACKNOWLEDGED;
                if (!isAllowed) {
                    throw new IllegalStateException("Clear restriction attempted but not allowed");
                }
                setAppEcmState(packageName, EcmState.ECM_STATE_NOT_GUARDED, userId);
                EnhancedConfirmationStatsLogUtils.INSTANCE.logRestrictionCleared(
                        getPackageUid(packageName, userId));
            } catch (NameNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

        public boolean isClearRestrictionAllowed(@NonNull String packageName,
                @UserIdInt int userId) {
            enforcePermissions("isClearRestrictionAllowed", userId);
            if (!UserUtils.isUserExistent(userId, getContext())) {
                return false;
            }

            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");

            try {
                int state = getAppEcmState(packageName, userId);
                return state == EcmState.ECM_STATE_GUARDED_AND_ACKNOWLEDGED;
            } catch (NameNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

        public void setClearRestrictionAllowed(@NonNull String packageName, @UserIdInt int userId) {
            enforcePermissions("setClearRestrictionAllowed", userId);
            if (!UserUtils.isUserExistent(userId, getContext())) {
                return;
            }

            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");

            try {
                if (isPackageEcmGuarded(packageName, userId)) {
                    setAppEcmState(packageName, EcmState.ECM_STATE_GUARDED_AND_ACKNOWLEDGED,
                            userId);
                }
            } catch (NameNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

        private boolean isUntrustedCallOngoing() {
            if (!Flags.unknownCallPackageInstallBlockingEnabled()) {
                return false;
            }

            if (hasCallOfType(CALL_TYPE_EMERGENCY)) {
                // If we have an emergency call, return false always.
                return false;
            }
            return hasCallOfType(CALL_TYPE_UNTRUSTED);
        }

        private void enforcePermissions(@NonNull String methodName, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, /* allowAll= */ false,
                    /* enforceForProfileGroup= */ false, methodName, mContext);
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.MANAGE_ENHANCED_CONFIRMATION_STATES, methodName);
        }

        private boolean isPackageEcmGuarded(@NonNull String packageName, @UserIdInt int userId)
                throws NameNotFoundException {
            ApplicationInfo applicationInfo = getApplicationInfoAsUser(packageName, userId);
            // Always trust allow-listed and pre-installed packages
            if (isAllowlistedPackage(packageName) || isAllowlistedInstaller(packageName)
                    || isPackagePreinstalled(applicationInfo)) {
                return false;
            }

            // If the package already has an explicitly-set state, use that
            @EcmState int ecmState = getAppEcmState(packageName, userId);
            if (ecmState == EcmState.ECM_STATE_GUARDED
                    || ecmState == EcmState.ECM_STATE_GUARDED_AND_ACKNOWLEDGED) {
                return true;
            }
            if (ecmState == EcmState.ECM_STATE_NOT_GUARDED) {
                return false;
            }

            // Otherwise, lazily decide whether the app is considered guarded.
            InstallSourceInfo installSource;
            try {
                installSource = mContext.createContextAsUser(UserHandle.of(userId), 0)
                        .getPackageManager()
                        .getInstallSourceInfo(packageName);
            } catch (NameNotFoundException e) {
                Log.w(LOG_TAG, "Package not found: " + packageName);
                return false;
            }

            // These install sources are always considered dangerous.
            // PackageInstallers that are trusted can use these as a signal that the
            // packages they've installed aren't as trusted as themselves.
            int packageSource = installSource.getPackageSource();
            if (packageSource == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
                    || packageSource == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE) {
                return true;
            }
            String installingPackageName = installSource.getInstallingPackageName();
            ApplicationInfo installingApplicationInfo =
                    getApplicationInfoAsUser(installingPackageName, userId);

            // ECM doesn't consider a transitive chain of trust for install sources.
            // If this package hasn't been explicitly handled by this point
            // then it is exempt from ECM if the immediate parent is a trusted installer
            return !(trustPackagesInstalledViaNonAllowlistedInstallers()
                    || isPackagePreinstalled(installingApplicationInfo)
                    || isAllowlistedInstaller(installingPackageName));
        }

        private boolean isSettingEcmGuardedForPackage(@NonNull String settingIdentifier,
                @NonNull String packageName, @UserIdInt int userId) throws NameNotFoundException {
            if (!PER_PACKAGE_PROTECTED_SETTINGS.contains(settingIdentifier)) {
                return false;
            }
            return isPackageEcmGuarded(packageName, userId);
        }

        private boolean isAllowlistedPackage(String packageName) {
            return isPackageSignedWithAnyOf(packageName,
                    mTrustedPackageCertDigests.get(packageName));
        }

        private boolean isAllowlistedInstaller(String packageName) {
            return isPackageSignedWithAnyOf(packageName,
                    mTrustedInstallerCertDigests.get(packageName));
        }

        private boolean isPackageSignedWithAnyOf(String packageName, List<byte[]> certDigests) {
            if (packageName != null && certDigests != null) {
                for (int i = 0, count = certDigests.size(); i < count; i++) {
                    byte[] trustedCertDigest = certDigests.get(i);
                    if (mPackageManager.hasSigningCertificate(packageName, trustedCertDigest,
                            PackageManager.CERT_INPUT_SHA256)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * @return {@code true} if zero {@code <enhanced-confirmation-trusted-installer>} entries
         * are defined in {@code frameworks/base/data/etc/enhanced-confirmation.xml}; in this case,
         * we treat all installers as trusted.
         */
        private boolean trustPackagesInstalledViaNonAllowlistedInstallers() {
            return mTrustedInstallerCertDigests.isEmpty();
        }

        private boolean isPackagePreinstalled(@Nullable ApplicationInfo applicationInfo) {
            if (applicationInfo == null) {
                return false;
            }
            return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        }

        @SuppressLint("WrongConstant")
        private void setAppEcmState(@NonNull String packageName, @EcmState int ecmState,
                @UserIdInt int userId) throws NameNotFoundException {
            int packageUid = getPackageUid(packageName, userId);
            final long identityToken = Binder.clearCallingIdentity();
            try {
                mAppOpsManager.setMode(AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS, packageUid,
                        packageName, ecmState);
            } finally {
                Binder.restoreCallingIdentity(identityToken);
            }
        }

        private @EcmState int getAppEcmState(@NonNull String packageName, @UserIdInt int userId)
                throws NameNotFoundException {
            int packageUid = getPackageUid(packageName, userId);
            final long identityToken = Binder.clearCallingIdentity();
            try {
                return mAppOpsManager.noteOpNoThrow(AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS,
                        packageUid, packageName, mAttributionTag, /* message */ null);
            } finally {
                Binder.restoreCallingIdentity(identityToken);
            }
        }

        private boolean isSettingEcmProtected(@NonNull String settingIdentifier) {
            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                    || mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                return false;
            }

            if (PER_PACKAGE_PROTECTED_SETTINGS.contains(settingIdentifier)) {
                return true;
            }
            if (UNTRUSTED_CALL_RESTRICTED_SETTINGS.contains(settingIdentifier)) {
                return true;
            }
            // TODO(b/310218979): Add role selections as protected settings
            return false;
        }

        private String getGlobalProtectionReason(@NonNull String settingIdentifier,
                @NonNull String packageName, @UserIdInt int userId) {
            if (UNTRUSTED_CALL_RESTRICTED_SETTINGS.contains(settingIdentifier)
                    && isUntrustedCallOngoing()) {
                if (!AppOpsManager.OPSTR_BIND_ACCESSIBILITY_SERVICE.equals(settingIdentifier)) {
                    return REASON_PHONE_STATE;
                }
                if (!isAccessibilityTool(packageName, userId)) {
                    return REASON_PHONE_STATE;
                }
                return null;
            }
            return null;
        }

        private boolean isAccessibilityTool(@NonNull String packageName, @UserIdInt int userId) {
            AccessibilityManager am;
            synchronized (mUserAccessibilityManagers) {
                if (!mUserAccessibilityManagers.containsKey(userId)) {
                    Context userContext =
                            getContext().createContextAsUser(UserHandle.of(userId), 0);
                    mUserAccessibilityManagers.put(userId, userContext.getSystemService(
                            AccessibilityManager.class));
                }
                am = mUserAccessibilityManagers.get(userId);
            }
            List<AccessibilityServiceInfo> infos = am.getInstalledAccessibilityServiceList();
            for (int i = 0; i < infos.size(); i++) {
                AccessibilityServiceInfo info = infos.get(i);
                String servicePackageName = null;
                if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                    servicePackageName = info.getResolveInfo().serviceInfo.packageName;
                }
                if (packageName.equals(servicePackageName)) {
                    return info.isAccessibilityTool();
                }
            }
            return false;
        }

        @Nullable
        private ApplicationInfo getApplicationInfoAsUser(@Nullable String packageName,
                @UserIdInt int userId) {
            if (packageName == null) {
                Log.w(LOG_TAG, "The packageName should not be null.");
                return null;
            }
            try {
                return mPackageManager.getApplicationInfoAsUser(packageName, /* flags */ 0,
                        UserHandle.of(userId));
            } catch (NameNotFoundException e) {
                Log.w(LOG_TAG, "Package not found: " + packageName, e);
                return null;
            }
        }

        private int getPackageUid(@NonNull String packageName, @UserIdInt int userId)
                throws NameNotFoundException {
            return mPackageManager.getApplicationInfoAsUser(packageName, /* flags */ 0,
                    UserHandle.of(userId)).uid;
        }
    }
}
