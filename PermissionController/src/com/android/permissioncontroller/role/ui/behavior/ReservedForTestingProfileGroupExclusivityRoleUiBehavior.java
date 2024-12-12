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

package com.android.permissioncontroller.role.ui.behavior;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.role.ui.TwoTargetPreference;
import com.android.role.controller.model.Role;
import com.android.role.controller.util.UserUtils;

import java.util.List;

public class ReservedForTestingProfileGroupExclusivityRoleUiBehavior implements RoleUiBehavior {
    @Override
    public void preparePreferenceAsUser(@NonNull Role role, @NonNull TwoTargetPreference preference,
            @NonNull List<ApplicationInfo> applicationInfos, @NonNull UserHandle user,
            @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        if (!applicationInfos.isEmpty()) {
            preparePreferenceInternal(preference.asPreference(), applicationInfos.get(0),
                    false, userContext);
        }
    }

    @Override
    public void prepareApplicationPreferenceAsUser(@NonNull Role role,
            @NonNull Preference preference, @NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        preparePreferenceInternal(preference, applicationInfo, true, userContext);
    }

    private void preparePreferenceInternal(@NonNull Preference preference,
            @NonNull ApplicationInfo applicationInfo, boolean setTitle, @NonNull Context context) {
        String title = Utils.getFullAppLabel(applicationInfo, context) + "@"
                + UserHandle.getUserHandleForUid(applicationInfo.uid).getIdentifier();
        if (setTitle) {
            preference.setTitle(title);
        } else {
            preference.setSummary(title);
        }
    }
}
