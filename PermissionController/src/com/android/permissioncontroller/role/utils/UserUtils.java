/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.permissioncontroller.role.utils;

import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;

import java.util.List;
import java.util.Objects;

/**
 * Utility methods about user.
 */
public class UserUtils {

    private UserUtils() {}

    /**
     * Get the work profile of current user, if any.
     *
     * @param context the {@code Context} to retrieve system services
     *
     * @return the work profile of current user, or {@code null} if none
     */
    @Nullable
    public static UserHandle getWorkProfile(@NonNull Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        List<UserHandle> profiles = userManager.getUserProfiles();
        UserHandle user = Process.myUserHandle();

        int profilesSize = profiles.size();
        for (int i = 0; i < profilesSize; i++) {
            UserHandle profile = profiles.get(i);

            if (Objects.equals(profile, user)) {
                continue;
            }
            if (!userManager.isManagedProfile(profile.getIdentifier())) {
                continue;
            }
            return profile;
        }
        return null;
    }

    /**
     * Get the private profile of current user, if any.
     *
     * @param context the {@code Context} to retrieve system services
     *
     * @return the private profile of current user, or {@code null} if none
     */
    @Nullable
    public static UserHandle getPrivateProfile(@NonNull Context context) {
        if (!SdkLevel.isAtLeastV()) {
            return null;
        }

        List<UserHandle> profiles = context.getSystemService(UserManager.class).getUserProfiles();
        UserHandle user = Process.myUserHandle();

        int profilesSize = profiles.size();
        for (int i = 0; i < profilesSize; i++) {
            UserHandle profile = profiles.get(i);

            if (Objects.equals(profile, user)) {
                continue;
            }
            if (isPrivateProfile(profile, context)) {
                return profile;
            }
        }
        return null;
    }

    private static boolean isPrivateProfile(@NonNull UserHandle userHandle,
            @NonNull Context context) {
        if (!SdkLevel.isAtLeastV() || !android.os.Flags.allowPrivateProfile()) {
            return false;
        }
        Context userContext = context.createContextAsUser(userHandle, /* flags= */ 0);
        return userContext.getSystemService(UserManager.class).isPrivateProfile();
    }

    /**
     * Create a context for a user.
     *
     * @param context The context to clone
     * @param user The user the new context should be for
     *
     * @return The context for the new user
     */
    @NonNull
    public static Context getUserContext(@NonNull Context context, @NonNull UserHandle user) {
        if (Process.myUserHandle().equals(user)) {
            return context;
        } else {
            return context.createContextAsUser(user, 0);
        }
    }
}
