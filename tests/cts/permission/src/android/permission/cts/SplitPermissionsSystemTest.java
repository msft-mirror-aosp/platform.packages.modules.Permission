/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.permission.cts;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_MEDIA_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.Manifest.permission.BODY_SENSORS;
import static android.Manifest.permission.BODY_SENSORS_BACKGROUND;
import static android.Manifest.permission.READ_CALL_LOG;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_MEDIA_AUDIO;
import static android.Manifest.permission.READ_MEDIA_IMAGES;
import static android.Manifest.permission.READ_MEDIA_VIDEO;
import static android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;
import static android.Manifest.permission.WRITE_CALL_LOG;
import static android.Manifest.permission.WRITE_CONTACTS;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.health.connect.HealthPermissions;
import android.os.Build;
import android.permission.PermissionManager;
import android.permission.PermissionManager.SplitPermissionInfo;
import android.permission.flags.Flags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
public class SplitPermissionsSystemTest {

    private static final int NO_TARGET = Build.VERSION_CODES.CUR_DEVELOPMENT + 1;

    private List<SplitPermissionInfo> mSplitPermissions;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void before() {
        Context context = InstrumentationRegistry.getContext();
        PermissionManager permissionManager = (PermissionManager) context.getSystemService(
                Context.PERMISSION_SERVICE);
        mSplitPermissions = permissionManager.getSplitPermissions();
    }

    @Test
    public void validateAndroidSystem() {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.Q));

        Set<SplitPermissionInfo> seenSplits = new HashSet<>(6);

        for (SplitPermissionInfo split : mSplitPermissions) {
            String splitPermission = split.getSplitPermission();

            // Due to limitation with accessing flag values in tests, BODY_SENSORS relevant splits
            // are handled in its dedicated tests.
            boolean shouldSkip =
                    !splitPermission.startsWith("android")
                            || splitPermission.equals(BODY_SENSORS)
                            || splitPermission.equals(BODY_SENSORS_BACKGROUND);
            if (shouldSkip) {
                continue;
            }

            assertThat(seenSplits).doesNotContain(split);
            seenSplits.add(split);

            List<String> newPermissions = split.getNewPermissions();

            switch (splitPermission) {
                case ACCESS_FINE_LOCATION:
                    // Q declares multiple for ACCESS_FINE_LOCATION, so assert both exist
                    if (newPermissions.contains(ACCESS_COARSE_LOCATION)) {
                        assertSplit(split, NO_TARGET, ACCESS_COARSE_LOCATION);
                    } else {
                        assertSplit(split, Build.VERSION_CODES.Q, ACCESS_BACKGROUND_LOCATION);
                    }
                    break;
                case WRITE_EXTERNAL_STORAGE:
                    if (newPermissions.contains(READ_EXTERNAL_STORAGE)) {
                        assertSplit(split, NO_TARGET, READ_EXTERNAL_STORAGE);
                    } else if (newPermissions.contains(ACCESS_MEDIA_LOCATION)) {
                        assertSplit(split, Build.VERSION_CODES.Q, ACCESS_MEDIA_LOCATION);
                    } else if (newPermissions.contains(READ_MEDIA_AUDIO)) {
                        assertSplit(split, Build.VERSION_CODES.S_V2 + 1, READ_MEDIA_AUDIO);
                    } else if (newPermissions.contains(READ_MEDIA_VIDEO)) {
                        assertSplit(split, Build.VERSION_CODES.S_V2 + 1, READ_MEDIA_VIDEO);
                    } else if (newPermissions.contains(READ_MEDIA_IMAGES)) {
                        assertSplit(split, Build.VERSION_CODES.S_V2 + 1, READ_MEDIA_IMAGES);
                    }
                    break;
                case READ_CONTACTS:
                    assertSplit(split, Build.VERSION_CODES.JELLY_BEAN, READ_CALL_LOG);
                    break;
                case WRITE_CONTACTS:
                    assertSplit(split, Build.VERSION_CODES.JELLY_BEAN, WRITE_CALL_LOG);
                    break;
                case ACCESS_COARSE_LOCATION:
                    assertSplit(split, Build.VERSION_CODES.Q, ACCESS_BACKGROUND_LOCATION);
                    break;
                case READ_EXTERNAL_STORAGE:
                    if (newPermissions.contains(ACCESS_MEDIA_LOCATION)) {
                        assertSplit(split, Build.VERSION_CODES.Q, ACCESS_MEDIA_LOCATION);
                    } else if (newPermissions.contains(READ_MEDIA_AUDIO)) {
                        assertSplit(split, Build.VERSION_CODES.S_V2 + 1, READ_MEDIA_AUDIO);
                    } else if (newPermissions.contains(READ_MEDIA_VIDEO)) {
                        assertSplit(split, Build.VERSION_CODES.S_V2 + 1, READ_MEDIA_VIDEO);
                    } else if (newPermissions.contains(READ_MEDIA_IMAGES)) {
                        assertSplit(split, Build.VERSION_CODES.S_V2 + 1, READ_MEDIA_IMAGES);
                    }
                    break;
                case READ_PRIVILEGED_PHONE_STATE:
                    assertSplit(split, NO_TARGET, READ_PHONE_STATE);
                    break;
                case BLUETOOTH_CONNECT:
                    assertSplit(split, Build.VERSION_CODES.S, BLUETOOTH, BLUETOOTH_ADMIN);
                    break;
                case BLUETOOTH_SCAN:
                    assertSplit(split, Build.VERSION_CODES.S, BLUETOOTH, BLUETOOTH_ADMIN);
                    break;
                case ACCESS_MEDIA_LOCATION:
                case READ_MEDIA_IMAGES:
                case READ_MEDIA_VIDEO:
                    assertSplit(split, READ_MEDIA_VISUAL_USER_SELECTED);
                    break;
            }
        }

        assertEquals(23, seenSplits.size());
    }

    @RequiresFlagsDisabled({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    @Test
    public void
            validateBodySensors_beforeGranularHealthPermissions_isSplitToBodySensorsBackground() {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.Q));

        mSplitPermissions.stream()
                .filter(split -> split.getSplitPermission().equals(BODY_SENSORS))
                .findFirst()
                .ifPresent(
                        split ->
                                assertSplit(
                                        split,
                                        Build.VERSION_CODES.TIRAMISU,
                                        BODY_SENSORS_BACKGROUND));
    }

    @RequiresFlagsEnabled({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    @Test
    public void validateBodySensors_afterGranularHealthPermissions_isSplitToReadHeartRate() {
        // TODO: Change this to Baklava when available.
        assumeTrue(ApiLevelUtil.isAtLeast(36));

        SplitPermissionInfo legacyBodySensorPermissionInfo = null;
        SplitPermissionInfo readHeartRatePermissionInfo = null;
        SplitPermissionInfo bodySensorBackgroundPermissionInfo = null;
        for (SplitPermissionInfo split : mSplitPermissions) {
            if (split.getSplitPermission().equals(BODY_SENSORS)
                    && split.getNewPermissions().contains(BODY_SENSORS_BACKGROUND)) {
                legacyBodySensorPermissionInfo = split;
            } else if (split.getSplitPermission().equals(BODY_SENSORS)
                    && split.getNewPermissions().contains(HealthPermissions.READ_HEART_RATE)) {
                readHeartRatePermissionInfo = split;
            } else if (split.getSplitPermission().equals(BODY_SENSORS_BACKGROUND)) {
                bodySensorBackgroundPermissionInfo = split;
            }
        }
        // Assert BODY_SENSORS is split to BODY_SENSORS_BACKGROUND and READ_HEART_RATE.
        assertSplit(
                legacyBodySensorPermissionInfo,
                Build.VERSION_CODES.TIRAMISU,
                BODY_SENSORS_BACKGROUND);
        assertSplit(readHeartRatePermissionInfo, HealthPermissions.READ_HEART_RATE);
        // Assert BODY_SENSORS_BACKGROUND is split to READ_HEALTH_DATA_IN_BACKGROUND.
        assertSplit(
                bodySensorBackgroundPermissionInfo,
                HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND);
    }

    private void assertSplit(SplitPermissionInfo split, int targetSdk, String... permission) {
        assertThat(split.getNewPermissions()).containsExactlyElementsIn(permission);
        assertThat(split.getTargetSdk()).isEqualTo(targetSdk);
    }

    private void assertSplit(SplitPermissionInfo split, String... permission) {
        assertThat(split.getNewPermissions()).containsExactlyElementsIn(permission);
    }
}
