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
package com.android.permissioncontroller.role.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import kotlin.jvm.functions.Function1;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A function for {@link androidx.lifecycle#map(androidx.lifecycle.LiveData, Function1)}
 * that filters a live data for role list.
 */
public class RoleListFilterFunction implements Function1<List<RoleItem>, List<RoleItem>> {
    private final Predicate<RoleItem> mPredicate;

    public RoleListFilterFunction(@NonNull Predicate<RoleItem> predicate) {
        mPredicate = predicate;
    }

    @NonNull
    @Override
    public List<RoleItem> invoke(@Nullable List<RoleItem> roleItems) {
        List<RoleItem> filteredRoleItems = new ArrayList<>();
        if (roleItems != null) {
            int roleItemsSize = roleItems.size();
            for (int i = 0; i < roleItemsSize; i++) {
                RoleItem roleItem = roleItems.get(i);
                if (mPredicate.test(roleItem)) {
                    filteredRoleItems.add(roleItem);
                }
            }
        }
        return filteredRoleItems;
    }
}
