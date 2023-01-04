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

package android.safetycenter.config;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.annotation.AnyRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;

import androidx.annotation.RequiresApi;

import java.util.Objects;
import java.util.regex.Pattern;

@RequiresApi(TIRAMISU)
final class BuilderUtils {

    private BuilderUtils() {}

    private static void validateAttribute(
            @Nullable Object attribute,
            @NonNull String name,
            boolean required,
            boolean prohibited,
            @Nullable Object defaultValue) {
        if (attribute == null && required) {
            throw new IllegalStateException("Required attribute " + name + " missing");
        }
        boolean nonDefaultValueProvided = !Objects.equals(attribute, defaultValue);
        boolean checkProhibited = prohibited && nonDefaultValueProvided;
        if (attribute != null && checkProhibited) {
            throw new IllegalStateException("Prohibited attribute " + name + " present");
        }
    }

    static void validateAttribute(
            @Nullable Object attribute,
            @NonNull String name,
            boolean required,
            boolean prohibited) {
        validateAttribute(attribute, name, required, prohibited, null);
    }

    static void validateId(
            @Nullable String id,
            @NonNull String name,
            boolean required,
            boolean prohibited) {
        validateAttribute(id, name, required, prohibited, null);
        if (!Pattern.compile("[0-9a-zA-Z_]+").matcher(id).matches()) {
            throw new IllegalStateException("Attribute " + name + " invalid");
        }
    }

    @AnyRes
    static int validateResId(
            @Nullable @AnyRes Integer value,
            @NonNull String name,
            boolean required,
            boolean prohibited) {
        validateAttribute(value, name, required, prohibited, Resources.ID_NULL);
        if (value == null) {
            return Resources.ID_NULL;
        }
        if (required && value == Resources.ID_NULL) {
            throw new IllegalStateException("Required attribute " + name + " invalid");
        }
        return value;
    }

    static int validateIntDef(
            @Nullable Integer value,
            @NonNull String name,
            boolean required,
            boolean prohibited,
            int defaultValue,
            int... validValues) {
        validateAttribute(value, name, required, prohibited, defaultValue);
        if (value == null) {
            return defaultValue;
        }

        boolean found = false;
        for (int i = 0; i < validValues.length; i++) {
            found |= (value == validValues[i]);
        }
        if (!found) {
            throw new IllegalStateException("Attribute " + name + " invalid");
        }
        return value;
    }

    static int validateInteger(
            @Nullable Integer value,
            @NonNull String name,
            boolean required,
            boolean prohibited,
            int defaultValue) {
        validateAttribute(value, name, required, prohibited, defaultValue);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    static boolean validateBoolean(
            @Nullable Boolean value,
            @NonNull String name,
            boolean required,
            boolean prohibited,
            boolean defaultValue) {
        validateAttribute(value, name, required, prohibited, defaultValue);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }
}
