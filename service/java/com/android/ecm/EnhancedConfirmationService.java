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

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.app.ecm.EnhancedConfirmationManager;
import android.app.ecm.IEnhancedConfirmationManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.SignedPackage;
import android.os.Binder;
import android.os.Build;
import android.os.SystemConfigManager;
import android.os.UserHandle;
import android.permission.flags.Flags;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.internal.util.Preconditions;
import com.android.permission.util.UserUtils;
import com.android.server.SystemService;

import java.lang.annotation.Retention;
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
public class EnhancedConfirmationService extends SystemService {
    private static final String LOG_TAG = EnhancedConfirmationService.class.getSimpleName();

    private Map<String, List<byte[]>> mTrustedPackageCertDigests;
    private Map<String, List<byte[]>> mTrustedInstallerCertDigests;

    public EnhancedConfirmationService(@NonNull Context context) {
        super(context);
    }

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

        private static final ArraySet<String> PROTECTED_SETTINGS = new ArraySet<>();

        static {
            // Runtime permissions
            PROTECTED_SETTINGS.add(Manifest.permission.SEND_SMS);
            PROTECTED_SETTINGS.add(Manifest.permission.RECEIVE_SMS);
            PROTECTED_SETTINGS.add(Manifest.permission.READ_SMS);
            PROTECTED_SETTINGS.add(Manifest.permission.RECEIVE_MMS);
            PROTECTED_SETTINGS.add(Manifest.permission.RECEIVE_WAP_PUSH);
            PROTECTED_SETTINGS.add(Manifest.permission.READ_CELL_BROADCASTS);
            PROTECTED_SETTINGS.add(Manifest.permission_group.SMS);

            PROTECTED_SETTINGS.add(Manifest.permission.BIND_DEVICE_ADMIN);
            // App ops
            PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_BIND_ACCESSIBILITY_SERVICE);
            PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_ACCESS_NOTIFICATIONS);
            PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW);
            PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_GET_USAGE_STATS);
            PROTECTED_SETTINGS.add(AppOpsManager.OPSTR_LOADER_USAGE_STATS);
            // Default application roles.
            PROTECTED_SETTINGS.add(RoleManager.ROLE_DIALER);
            PROTECTED_SETTINGS.add(RoleManager.ROLE_SMS);
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
            enforcePermissions("isRestricted", userId);
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return false;
            }

            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");
            Preconditions.checkStringNotEmpty(settingIdentifier,
                    "settingIdentifier cannot be null or empty");

            try {
                return isSettingEcmProtected(settingIdentifier) && isPackageEcmGuarded(packageName,
                        userId);
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

        private void enforcePermissions(@NonNull String methodName, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false, methodName, mContext);
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

            if (PROTECTED_SETTINGS.contains(settingIdentifier)) {
                return true;
            }
            // TODO(b/310218979): Add role selections as protected settings
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
