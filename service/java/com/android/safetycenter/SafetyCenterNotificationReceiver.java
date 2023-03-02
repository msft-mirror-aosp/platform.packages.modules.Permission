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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

/**
 * A Context-registered {@link BroadcastReceiver} that handles intents sent via Safety Center
 * notifications e.g. when a notification is dismissed.
 *
 * <p>Use {@link #register(Context)} to register this receiver with the correct {@link IntentFilter}
 * and use the {@link #newNotificationDismissedIntent(Context, SafetyCenterIssueKey)} factory method
 * to create new intents for this receiver.
 */
@RequiresApi(TIRAMISU)
final class SafetyCenterNotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "SafetyCenterNR";

    private static final String ACTION_NOTIFICATION_DISMISSED =
            "com.android.safetycenter.action.NOTIFICATION_DISMISSED";
    private static final String EXTRA_ISSUE_KEY = "com.android.safetycenter.extra.ISSUE_KEY";

    private static final int REQUEST_CODE_UNUSED = 0;

    /**
     * Creates a {@code PendingIntent} for a broadcast {@code Intent} which will start this receiver
     * and cause it to handle a notification dismissal event.
     */
    @NonNull
    static PendingIntent newNotificationDismissedIntent(
            @NonNull Context context, @NonNull SafetyCenterIssueKey issueKey) {
        String issueKeyString = SafetyCenterIds.encodeToString(issueKey);
        Intent intent = new Intent(ACTION_NOTIFICATION_DISMISSED);
        intent.putExtra(EXTRA_ISSUE_KEY, issueKeyString);
        intent.setIdentifier(issueKeyString);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntentFactory.getNonProtectedSystemOnlyBroadcastPendingIntent(
                context, REQUEST_CODE_UNUSED, intent, flags);
    }

    @Nullable
    private static SafetyCenterIssueKey getIssueKeyExtra(@NonNull Intent intent) {
        String issueKeyString = intent.getStringExtra(EXTRA_ISSUE_KEY);
        if (issueKeyString == null) {
            Log.w(TAG, "Received notification dismissed broadcast with null issue key extra");
            return null;
        }
        try {
            return SafetyCenterIds.issueKeyFromString(issueKeyString);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Could not decode the issue key extra", e);
            return null;
        }
    }

    @NonNull private final SafetyCenterIssueCache mIssueCache;
    @NonNull private final Object mApiLock;

    SafetyCenterNotificationReceiver(
            @NonNull SafetyCenterIssueCache issueCache, @NonNull Object apiLock) {
        mIssueCache = issueCache;
        mApiLock = apiLock;
    }

    /**
     * Register this receiver in the given {@link Context} with an {@link IntentFilter} that matches
     * any intents created by this class' static factory methods.
     *
     * @see #newNotificationDismissedIntent(Context, SafetyCenterIssueKey)
     */
    void register(@NonNull Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NOTIFICATION_DISMISSED);
        context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        if (!SafetyCenterFlags.getSafetyCenterEnabled()
                || !SafetyCenterFlags.getNotificationsEnabled()) {
            return;
        }

        Log.d(TAG, "Received broadcast with action " + intent.getAction());
        String action = intent.getAction();
        if (action == null) {
            Log.w(TAG, "Received broadcast with null action!");
            return;
        }

        if (ACTION_NOTIFICATION_DISMISSED.equals(action)) {
            onNotificationDismissed(intent);
        } else {
            Log.w(TAG, "Received broadcast with unrecognized action: " + action);
        }
    }

    private void onNotificationDismissed(@NonNull Intent intent) {
        SafetyCenterIssueKey issueKey = getIssueKeyExtra(intent);
        if (issueKey == null) {
            return;
        }
        synchronized (mApiLock) {
            mIssueCache.dismissNotification(issueKey);
        }
    }
}
