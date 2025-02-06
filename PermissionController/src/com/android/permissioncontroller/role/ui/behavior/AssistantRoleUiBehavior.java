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

package com.android.permissioncontroller.role.ui.behavior;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.permission.flags.Flags;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.role.ui.RoleApplicationItem;
import com.android.role.controller.model.Role;
import com.android.role.controller.util.SignedPackage;
import com.android.role.controller.util.SignedPackageUtils;

import java.util.List;
import java.util.function.Predicate;

/***
 * Class for UI behavior of Assistant role
 */
public class AssistantRoleUiBehavior implements RoleUiBehavior {

    @Nullable
    @Override
    public Intent getManageIntentAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        boolean isAutomotive =
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        if (isAutomotive) {
            return null;
        }
        return new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
    }

    @NonNull
    @Override
    public Predicate<RoleApplicationItem> getRecommendedApplicationFilter(
            @NonNull Role role, @NonNull Context context) {
        if (Flags.defaultAppsRecommendationEnabled()) {
            List<SignedPackage> signedPackages = SignedPackage.parseList(
                    context.getResources().getString(R.string.config_recommendedAssistants));
            return applicationItem -> SignedPackageUtils.matchesAny(
                    applicationItem.getApplicationInfo(), signedPackages, context);
        } else {
            return RoleUiBehavior.super.getRecommendedApplicationFilter(role, context);
        }
    }

    @Nullable
    @Override
    public CharSequence getConfirmationMessage(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        return context.getString(R.string.assistant_confirmation_message);
    }
}
