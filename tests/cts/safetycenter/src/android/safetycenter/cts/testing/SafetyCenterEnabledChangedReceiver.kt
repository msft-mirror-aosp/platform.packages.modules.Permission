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

package android.safetycenter.cts.testing

import android.Manifest.permission.READ_SAFETY_CENTER_STATUS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.runBlockingWithTimeout
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import android.safetycenter.cts.testing.WaitForBroadcastIdle.waitForBroadcastIdle
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED

/** Broadcast receiver used for testing broadcasts sent when the SafetyCenter flag changes. */
class SafetyCenterEnabledChangedReceiver(private val context: Context) : BroadcastReceiver() {

    private val safetyCenterEnabledChangedChannel = Channel<Boolean>(UNLIMITED)

    init {
        context.registerReceiver(this, IntentFilter(ACTION_SAFETY_CENTER_ENABLED_CHANGED))
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            throw IllegalArgumentException("Received null intent")
        }

        if (intent.action != ACTION_SAFETY_CENTER_ENABLED_CHANGED) {
            throw IllegalArgumentException("Received intent with action: ${intent.action}")
        }

        val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

        runBlockingWithTimeout {
            safetyCenterEnabledChangedChannel.send(safetyCenterManager.isSafetyCenterEnabled)
        }
    }

    fun setSafetyCenterEnabledWithReceiverPermissionAndWait(
        value: Boolean,
        timeout: Duration = TIMEOUT_LONG
    ) =
        callWithShellPermissionIdentity(
            { setSafetyCenterEnabledWithoutReceiverPermissionAndWait(value, timeout) },
            READ_SAFETY_CENTER_STATUS)

    fun setSafetyCenterEnabledWithoutReceiverPermissionAndWait(
        value: Boolean,
        timeout: Duration
    ): Boolean {
        SafetyCenterFlags.isEnabled = value
        if (timeout < TIMEOUT_LONG) {
            context.waitForBroadcastIdle()
        }
        return receiveSafetyCenterEnabledChanged(timeout)
    }

    fun unregister() {
        context.unregisterReceiver(this)
        safetyCenterEnabledChangedChannel.cancel()
    }

    private fun receiveSafetyCenterEnabledChanged(timeout: Duration = TIMEOUT_LONG): Boolean =
        runBlockingWithTimeout(timeout) { safetyCenterEnabledChangedChannel.receive() }
}
