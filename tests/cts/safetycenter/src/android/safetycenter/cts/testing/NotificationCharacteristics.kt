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

import android.app.Notification
import android.service.notification.StatusBarNotification

/** The characteristic properties of a notification. */
data class NotificationCharacteristics(val title: String, val text: String) {
    companion object {
        private fun isMatch(
            statusBarNotification: StatusBarNotification,
            characteristic: NotificationCharacteristics
        ): Boolean {
            val notif = statusBarNotification.notification
            return notif != null &&
                notif.extras.getString(Notification.EXTRA_TITLE) == characteristic.title &&
                notif.extras.getString(Notification.EXTRA_TEXT) == characteristic.text
        }

        fun areMatching(
            statusBarNotifications: List<StatusBarNotification>,
            characteristics: List<NotificationCharacteristics>
        ): Boolean {
            if (statusBarNotifications.size != characteristics.size) {
                return false
            }
            return statusBarNotifications.zip(characteristics).all { isMatch(it.first, it.second) }
        }
    }
}
