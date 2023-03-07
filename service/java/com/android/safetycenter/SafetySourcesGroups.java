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
import android.safetycenter.config.SafetySourcesGroup;

import androidx.annotation.RequiresApi;

/** Static utilities for working with {@link SafetySourcesGroup} objects. */
@RequiresApi(TIRAMISU)
final class SafetySourcesGroups {

    /**
     * Returns a builder with all fields of the original group copied other than {@link
     * SafetySourcesGroup#getSafetySources()}.
     */
    @NonNull
    static SafetySourcesGroup.Builder copyToBuilderWithoutSources(
            @NonNull SafetySourcesGroup group) {
        return new SafetySourcesGroup.Builder()
                .setId(group.getId())
                .setTitleResId(group.getTitleResId())
                .setSummaryResId(group.getSummaryResId())
                .setStatelessIconType(group.getStatelessIconType());
    }

    private SafetySourcesGroups() {}
}
