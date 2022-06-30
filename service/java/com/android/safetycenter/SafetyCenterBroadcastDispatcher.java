/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.safetycenter;

import static android.Manifest.permission.READ_SAFETY_CENTER_STATUS;
import static android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.PowerExemptionManager.REASON_REFRESH_SAFETY_SOURCES;
import static android.os.PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED;
import static android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES;
import static android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_LOCALE_CHANGE;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_REBOOT;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_SAFETY_CENTER_ENABLED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.safetycenter.SafetyCenterManager.RefreshRequestType;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.SafetyCenterConfigReader.Broadcast;

import java.time.Duration;
import java.util.List;

/**
 * A class that dispatches SafetyCenter broadcasts.
 *
 * <p>This class is thread safe as it does not contain any mutable state.
 */
@RequiresApi(TIRAMISU)
final class SafetyCenterBroadcastDispatcher {

    private static final String TAG = "SafetyCenterBroadcastDi";

    /**
     * Device Config flag that determines the time for which an app, upon receiving a Safety Center
     * refresh broadcast, will be placed on a temporary power allowlist allowing it to start a
     * foreground service from the background.
     */
    private static final String PROPERTY_FGS_ALLOWLIST_DURATION_MILLIS =
            "safety_center_refresh_fgs_allowlist_duration_millis";

    /**
     * Default time for which an app, upon receiving a particular broadcast, will be placed on a
     * temporary power allowlist allowing it to start a foreground service from the background.
     */
    private static final Duration FGS_ALLOWLIST_DEFAULT_DURATION = Duration.ofSeconds(20);

    @NonNull private final Context mContext;

    /** Creates a {@link SafetyCenterBroadcastDispatcher} using the given {@link Context}. */
    SafetyCenterBroadcastDispatcher(@NonNull Context context) {
        mContext = context;
    }

    /**
     * Triggers a refresh of safety sources by sending them broadcasts with action {@link
     * SafetyCenterManager#ACTION_REFRESH_SAFETY_SOURCES}.
     */
    void sendRefreshSafetySources(
            @NonNull List<Broadcast> broadcasts,
            @NonNull String broadcastId,
            @RefreshReason int refreshReason,
            @NonNull UserProfileGroup userProfileGroup) {
        BroadcastOptions broadcastOptions = createBroadcastOptions();

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);

            sendRefreshSafetySourcesBroadcast(
                    broadcast, broadcastOptions, refreshReason, userProfileGroup, broadcastId);
        }
    }

    /**
     * Triggers an {@link SafetyCenterManager#ACTION_SAFETY_CENTER_ENABLED_CHANGED} broadcast for
     * all safety sources.
     *
     * <p>This method also sends an implicit broadcast globally (which requires the {@link
     * android.Manifest.permission#READ_SAFETY_CENTER_STATUS} permission).
     */
    // TODO(b/227310195): Consider adding a boolean extra to the intent instead of having clients
    //  rely on SafetyCenterManager#isSafetyCenterEnabled()?
    void sendEnabledChanged(@NonNull List<Broadcast> broadcasts) {
        BroadcastOptions broadcastOptions = createBroadcastOptions();
        List<UserProfileGroup> userProfileGroups =
                UserProfileGroup.getAllUserProfileGroups(mContext);

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);
            Intent broadcastIntent =
                    createEnabledChangedBroadcastIntent(broadcast.getPackageName());

            for (int j = 0; j < userProfileGroups.size(); j++) {
                UserProfileGroup userProfileGroup = userProfileGroups.get(j);

                List<String> profileParentSourceIds =
                        broadcast.getSourceIdsForProfileParent(
                                REFRESH_REASON_SAFETY_CENTER_ENABLED);
                if (!profileParentSourceIds.isEmpty()) {
                    int profileParentUserId = userProfileGroup.getProfileParentUserId();

                    sendBroadcast(
                            broadcastIntent,
                            UserHandle.of(profileParentUserId),
                            SEND_SAFETY_CENTER_UPDATE,
                            broadcastOptions);
                }

                List<String> managedProfilesSourceIds =
                        broadcast.getSourceIdsForManagedProfiles(
                                REFRESH_REASON_SAFETY_CENTER_ENABLED);
                if (!managedProfilesSourceIds.isEmpty()) {
                    int[] managedRunningProfilesUserIds =
                            userProfileGroup.getManagedRunningProfilesUserIds();
                    for (int k = 0; k < managedRunningProfilesUserIds.length; k++) {
                        int managedRunningProfileUserId = managedRunningProfilesUserIds[k];
                        sendBroadcast(
                                broadcastIntent,
                                UserHandle.of(managedRunningProfileUserId),
                                SEND_SAFETY_CENTER_UPDATE,
                                broadcastOptions);
                    }
                }
            }
        }

        sendBroadcast(
                createEnabledChangedBroadcastIntent(),
                UserHandle.SYSTEM,
                READ_SAFETY_CENTER_STATUS,
                null);
    }

    private void sendRefreshSafetySourcesBroadcast(
            @NonNull Broadcast broadcast,
            @NonNull BroadcastOptions broadcastOptions,
            @RefreshReason int refreshReason,
            @NonNull UserProfileGroup userProfileGroup,
            @NonNull String broadcastId) {
        int requestType = toRefreshRequestType(refreshReason);
        List<String> profileParentSourceIds = broadcast.getSourceIdsForProfileParent(refreshReason);
        if (!profileParentSourceIds.isEmpty()) {
            int profileParentUserId = userProfileGroup.getProfileParentUserId();
            Intent broadcastIntent =
                    createRefreshSafetySourcesBroadcastIntent(
                            requestType,
                            broadcast.getPackageName(),
                            profileParentSourceIds,
                            broadcastId);

            sendBroadcast(
                    broadcastIntent,
                    UserHandle.of(profileParentUserId),
                    SEND_SAFETY_CENTER_UPDATE,
                    broadcastOptions);
        }
        List<String> managedProfilesSourceIds =
                broadcast.getSourceIdsForManagedProfiles(refreshReason);
        if (!managedProfilesSourceIds.isEmpty()) {
            int[] managedRunningProfilesUserIds =
                    userProfileGroup.getManagedRunningProfilesUserIds();
            for (int i = 0; i < managedRunningProfilesUserIds.length; i++) {
                int managedRunningProfilesUserId = managedRunningProfilesUserIds[i];
                Intent broadcastIntent =
                        createRefreshSafetySourcesBroadcastIntent(
                                requestType,
                                broadcast.getPackageName(),
                                managedProfilesSourceIds,
                                broadcastId);

                sendBroadcast(
                        broadcastIntent,
                        UserHandle.of(managedRunningProfilesUserId),
                        SEND_SAFETY_CENTER_UPDATE,
                        broadcastOptions);
            }
        }
    }

    private void sendBroadcast(
            @NonNull Intent broadcastIntent,
            @NonNull UserHandle userHandle,
            @NonNull String permission,
            @Nullable BroadcastOptions broadcastOptions) {
        // The following operation requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(
                    broadcastIntent,
                    userHandle,
                    permission,
                    broadcastOptions == null ? null : broadcastOptions.toBundle());
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @NonNull
    private static Intent createEnabledChangedBroadcastIntent(@NonNull String packageName) {
        return createEnabledChangedBroadcastIntent().setPackage(packageName);
    }

    @NonNull
    private static Intent createEnabledChangedBroadcastIntent() {
        return createBroadcastIntent(ACTION_SAFETY_CENTER_ENABLED_CHANGED);
    }

    @NonNull
    private static Intent createRefreshSafetySourcesBroadcastIntent(
            @RefreshRequestType int requestType,
            @NonNull String packageName,
            @NonNull List<String> sourceIdsToRefresh,
            @NonNull String broadcastId) {
        return createBroadcastIntent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, requestType)
                .putExtra(
                        EXTRA_REFRESH_SAFETY_SOURCE_IDS, sourceIdsToRefresh.toArray(new String[0]))
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, broadcastId)
                .setPackage(packageName);
    }

    @NonNull
    private static Intent createBroadcastIntent(@NonNull String intentAction) {
        return new Intent(intentAction).setFlags(FLAG_RECEIVER_FOREGROUND);
    }

    @NonNull
    private static BroadcastOptions createBroadcastOptions() {
        BroadcastOptions broadcastOptions = BroadcastOptions.makeBasic();
        // The following operation requires the START_FOREGROUND_SERVICES_FROM_BACKGROUND
        // and READ_DEVICE_CONFIG permissions.
        final long callingId = Binder.clearCallingIdentity();
        try {
            broadcastOptions.setTemporaryAppAllowlist(
                    getFgsAllowlistDuration().toMillis(),
                    TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                    REASON_REFRESH_SAFETY_SOURCES,
                    "Safety Center is requesting data from safety sources");
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
        return broadcastOptions;
    }

    @RefreshRequestType
    private static int toRefreshRequestType(@RefreshReason int refreshReason) {
        switch (refreshReason) {
            case REFRESH_REASON_RESCAN_BUTTON_CLICK:
                return EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
            case REFRESH_REASON_PAGE_OPEN:
            case REFRESH_REASON_DEVICE_REBOOT:
            case REFRESH_REASON_DEVICE_LOCALE_CHANGE:
            case REFRESH_REASON_SAFETY_CENTER_ENABLED:
            case REFRESH_REASON_OTHER:
                return EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
        }
        throw new IllegalArgumentException("Unexpected refresh reason: " + refreshReason);
    }

    /**
     * Returns the time for which an app, upon receiving a particular broadcast, should be placed on
     * a temporary power allowlist allowing it to start a foreground service from the background.
     */
    private static Duration getFgsAllowlistDuration() {
        return Duration.ofMillis(
                DeviceConfig.getLong(
                        DeviceConfig.NAMESPACE_PRIVACY,
                        PROPERTY_FGS_ALLOWLIST_DURATION_MILLIS,
                        FGS_ALLOWLIST_DEFAULT_DURATION.toMillis()));
    }
}
