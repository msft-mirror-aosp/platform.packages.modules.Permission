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

import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.os.Binder;
import android.provider.DeviceConfig;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetyCenterStatus.RefreshStatus;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.SafetyCenterConfigReader.Broadcast;

import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class to store the state of a refresh of safety sources, if any is ongoing.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterRefreshTracker {
    private static final String TAG = "SafetyCenterRefreshTrac";
    private static final String PROPERTY_UNTRACKED_SOURCES = "safety_center_untracked_sources";

    @NonNull private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    @Nullable
    // TODO(b/229060064): Should we allow one refresh at a time per UserProfileGroup rather than
    //  one global refresh?
    private RefreshInProgress mRefreshInProgress = null;

    /**
     * Creates a {@link SafetyCenterRefreshTracker} using the given {@link
     * SafetyCenterConfigReader}.
     */
    SafetyCenterRefreshTracker(@NonNull SafetyCenterConfigReader safetyCenterConfigReader) {
        mSafetyCenterConfigReader = safetyCenterConfigReader;
    }

    private int mRefreshCounter = 0;

    /**
     * Reports that a new refresh is in progress and returns the broadcast id associated with this
     * refresh.
     */
    @NonNull
    String reportRefreshInProgress(
            @RefreshReason int refreshReason, @NonNull UserProfileGroup userProfileGroup) {
        if (mRefreshInProgress != null) {
            Log.w(TAG, "Replacing an ongoing refresh");
        }

        List<Broadcast> broadcasts = mSafetyCenterConfigReader.getBroadcasts();
        String refreshBroadcastId =
                Objects.hash(refreshReason, broadcasts, userProfileGroup) + "_" + mRefreshCounter++;
        Log.v(
                TAG,
                "Starting a new refresh with refreshReason:"
                        + refreshReason
                        + " refreshBroadcastId:"
                        + refreshBroadcastId);

        mRefreshInProgress =
                new RefreshInProgress(
                        refreshBroadcastId,
                        refreshReason,
                        userProfileGroup,
                        getUntrackedSourceIds());

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);
            List<String> profileParentSourceIds =
                    broadcast.getSourceIdsForProfileParent(refreshReason);
            for (int j = 0; j < profileParentSourceIds.size(); j++) {
                String profileParentSourceId = profileParentSourceIds.get(j);
                mRefreshInProgress.addSourceRefreshInFlight(
                        SafetySourceKey.of(
                                profileParentSourceId, userProfileGroup.getProfileParentUserId()));
            }
            List<String> managedProfilesSourceIds =
                    broadcast.getSourceIdsForManagedProfiles(refreshReason);
            for (int j = 0; j < managedProfilesSourceIds.size(); j++) {
                String managedProfilesSourceId = managedProfilesSourceIds.get(j);
                int[] managedRunningProfilesUserIds =
                        userProfileGroup.getManagedRunningProfilesUserIds();
                for (int k = 0; k < managedRunningProfilesUserIds.length; k++) {
                    int managedRunningProfileUserId = managedRunningProfilesUserIds[k];
                    mRefreshInProgress.addSourceRefreshInFlight(
                            SafetySourceKey.of(
                                    managedProfilesSourceId, managedRunningProfileUserId));
                }
            }
        }

        return refreshBroadcastId;
    }

    /** Returns the current refresh status. */
    @RefreshStatus
    int getRefreshStatus() {
        if (mRefreshInProgress == null) {
            return SafetyCenterStatus.REFRESH_STATUS_NONE;
        }

        if (mRefreshInProgress.getReason() == REFRESH_REASON_RESCAN_BUTTON_CLICK) {
            return SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS;
        }
        return SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS;
    }

    /**
     * Reports that a source has completed its refresh, and returns whether this caused the refresh
     * to complete.
     *
     * <p>If a source calls {@code reportSafetySourceError}, then this method is also used to mark
     * the refresh as completed.
     */
    boolean reportSourceRefreshCompleted(
            @NonNull String sourceId, @NonNull String refreshBroadcastId, @UserIdInt int userId) {
        if (!checkMethodValid("reportSourceRefreshCompleted", refreshBroadcastId)) {
            return false;
        }

        mRefreshInProgress.markSourceRefreshAsComplete(SafetySourceKey.of(sourceId, userId));

        if (!mRefreshInProgress.isComplete()) {
            return false;
        }

        mRefreshInProgress = null;
        return true;
    }

    /**
     * Clears any ongoing refresh in progress.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    // TODO(b/229188900): Should we stop any scheduled broadcasts from going out?
    void clearRefresh() {
        if (mRefreshInProgress != null) {
            clearRefresh(mRefreshInProgress.getId());
        } else {
            Log.v(TAG, "Clear refresh called but no refresh in progress");
        }
    }

    /**
     * Clears the refresh in progress with the given id, and returns whether it was ongoing.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    // TODO(b/229188900): Should we stop any scheduled broadcasts from going out?
    boolean clearRefresh(@NonNull String refreshBroadcastId) {
        if (!checkMethodValid("clearRefresh", refreshBroadcastId)) {
            return false;
        }

        Log.v(TAG, "Clearing refresh with refreshBroadcastId:" + refreshBroadcastId);
        mRefreshInProgress = null;
        return true;
    }

    /**
     * Clears any ongoing refresh in progress for the given user.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    // TODO(b/229188900): Should we stop any scheduled broadcasts from going out?
    void clearRefreshForUser(@UserIdInt int userId) {
        if (mRefreshInProgress != null) {
            if (mRefreshInProgress.getUserProfileGroup().getProfileParentUserId() == userId) {
                clearRefresh();
            } else {
                mRefreshInProgress.clearForUser(userId);
                if (mRefreshInProgress.isComplete()) {
                    mRefreshInProgress = null;
                }
            }
        } else {
            Log.v(TAG, "Clear refresh for user called but no refresh in progress");
        }
    }

    private boolean checkMethodValid(
            @NonNull String methodName, @NonNull String refreshBroadcastId) {
        if (mRefreshInProgress == null || !mRefreshInProgress.getId().equals(refreshBroadcastId)) {
            Log.w(
                    TAG,
                    methodName
                            + " called for invalid refresh broadcast id: "
                            + refreshBroadcastId
                            + "; no such refresh in"
                            + " progress");
            return false;
        }
        return true;
    }

    /**
     * Returns the IDs of sources that should not be tracked, for example because they are
     * mid-rollout. Broadcasts are still sent to these sources.
     */
    private static ArraySet<String> getUntrackedSourceIds() {
        String untrackedSourcesConfigString = getUntrackedSourcesConfigString();
        String[] untrackedSourcesList = untrackedSourcesConfigString.split(",");
        return new ArraySet<>(untrackedSourcesList);
    }

    private static String getUntrackedSourcesConfigString() {
        // This call requires the READ_DEVICE_CONFIG permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return DeviceConfig.getString(
                    DeviceConfig.NAMESPACE_PRIVACY, PROPERTY_UNTRACKED_SOURCES, "");
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /** Class representing the state of a refresh in progress. */
    private static final class RefreshInProgress {
        @NonNull private final String mId;
        @RefreshReason private final int mReason;
        @NonNull private final UserProfileGroup mUserProfileGroup;
        @NonNull private final ArraySet<String> mUntrackedSourcesIds;

        private final ArraySet<SafetySourceKey> mSourceRefreshInFlight = new ArraySet<>();

        /** Creates a {@link RefreshInProgress}. */
        RefreshInProgress(
                @NonNull String id,
                @RefreshReason int reason,
                @NonNull UserProfileGroup userProfileGroup,
                @NonNull ArraySet<String> untrackedSourceIds) {
            mId = id;
            mReason = reason;
            mUserProfileGroup = userProfileGroup;
            mUntrackedSourcesIds = untrackedSourceIds;
        }

        /**
         * Returns the id of the {@link RefreshInProgress}, which corresponds to the {@link
         * android.safetycenter.SafetyCenterManager#EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID} used
         * in the refresh.
         */
        @NonNull
        String getId() {
            return mId;
        }

        /** Returns the {@link RefreshReason} that was given for this {@link RefreshInProgress}. */
        @RefreshReason
        int getReason() {
            return mReason;
        }

        /** Returns the {@link UserProfileGroup} for which there is a {@link RefreshInProgress}. */
        @NonNull
        UserProfileGroup getUserProfileGroup() {
            return mUserProfileGroup;
        }

        private void addSourceRefreshInFlight(@NonNull SafetySourceKey safetySourceKey) {
            boolean tracked = isTracked(safetySourceKey);
            if (tracked) {
                mSourceRefreshInFlight.add(safetySourceKey);
            }
            Log.v(
                    TAG,
                    "Refresh started for sourceId:"
                            + safetySourceKey.getSourceId()
                            + " userId:"
                            + safetySourceKey.getUserId()
                            + " with refreshBroadcastId:"
                            + mId
                            + " & tracking:"
                            + tracked
                            + " , now "
                            + mSourceRefreshInFlight.size()
                            + " tracked sources in flight.");
        }

        private void markSourceRefreshAsComplete(@NonNull SafetySourceKey safetySourceKey) {
            mSourceRefreshInFlight.remove(safetySourceKey);
            boolean tracked = isTracked(safetySourceKey);
            Log.v(
                    TAG,
                    "Refresh completed for sourceId:"
                            + safetySourceKey.getSourceId()
                            + " userId:"
                            + safetySourceKey.getUserId()
                            + " with refreshBroadcastId:"
                            + mId
                            + " & tracking:"
                            + tracked
                            + ", "
                            + mSourceRefreshInFlight.size()
                            + " tracked sources still in flight.");
        }

        private boolean isTracked(SafetySourceKey safetySourceKey) {
            return !mUntrackedSourcesIds.contains(safetySourceKey.getSourceId());
        }

        private void clearForUser(@UserIdInt int userId) {
            // Loop in reverse index order to be able to remove entries while iterating.
            for (int i = mSourceRefreshInFlight.size() - 1; i >= 0; i--) {
                SafetySourceKey sourceKey = mSourceRefreshInFlight.valueAt(i);
                if (sourceKey.getUserId() == userId) {
                    mSourceRefreshInFlight.removeAt(i);
                }
            }
        }

        private boolean isComplete() {
            return mSourceRefreshInFlight.isEmpty();
        }
    }
}
