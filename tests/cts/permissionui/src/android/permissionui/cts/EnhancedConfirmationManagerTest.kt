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

package android.permissionui.cts

import android.app.AppOpsManager
import android.app.Instrumentation
import android.app.ecm.EnhancedConfirmationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@AppModeFull(reason = "Instant apps cannot install packages")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
class EnhancedConfirmationManagerTest : BaseUsePermissionTest() {

    private val apkName = APP_APK_NAME_31
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val ecm by lazy { context.getSystemService(EnhancedConfirmationManager::class.java)!! }

    @Rule
    @JvmField
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun assumeNotAutoTvOrWear() {
        Assume.assumeFalse(context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        Assume.assumeFalse(
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        )
        Assume.assumeFalse(context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH))
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenStoreAppThenIsNotRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromStore(apkName)
        runWithShellPermissionIdentity {
            eventually { assertFalse(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenLocalAppThenIsRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromLocalFile(apkName)
        runWithShellPermissionIdentity {
            eventually { assertTrue(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenDownloadedThenAppIsRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(apkName)
        runWithShellPermissionIdentity {
            eventually { assertTrue(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenExplicitlyRestrictedAppThenIsRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromStore(apkName)
        runWithShellPermissionIdentity {
            eventually { assertFalse(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
            setAppEcmState(context, APP_PACKAGE_NAME, MODE_ERRORED)
            eventually { assertTrue(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenRestrictedAppThenIsNotRestrictedFromNonProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(apkName)
        runWithShellPermissionIdentity {
            eventually { assertFalse(ecm.isRestricted(APP_PACKAGE_NAME, NON_PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenRestrictedAppThenClearRestrictionNotAllowedByDefault() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(apkName)
        runWithShellPermissionIdentity {
            eventually { assertFalse(ecm.isClearRestrictionAllowed(APP_PACKAGE_NAME)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenRestrictedAppWhenClearRestrictionThenNotRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(apkName)
        runWithShellPermissionIdentity {
            eventually { assertTrue(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
            ecm.setClearRestrictionAllowed(APP_PACKAGE_NAME)
            eventually { assertTrue(ecm.isClearRestrictionAllowed(APP_PACKAGE_NAME)) }
            ecm.clearRestriction(APP_PACKAGE_NAME)
            eventually { assertFalse(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun createRestrictedSettingDialogIntentReturnsIntent() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(apkName)

        val intent = ecm.createRestrictedSettingDialogIntent(APP_PACKAGE_NAME, PROTECTED_SETTING)

        assertNotNull(intent)
    }

    companion object {
        private const val NON_PROTECTED_SETTING = "example_setting_which_is_not_protected"
        private const val PROTECTED_SETTING = "android:bind_accessibility_service"
        private const val MODE_ERRORED = 2

        @Throws(PackageManager.NameNotFoundException::class)
        private fun setAppEcmState(context: Context, packageName: String, mode: Int) {
            val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
            appOpsManager.setMode(
                AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS,
                context.packageManager
                    .getApplicationInfoAsUser(
                        packageName,
                        /* flags */ 0,
                        android.os.Process.myUserHandle()
                    )
                    .uid,
                packageName,
                mode
            )
        }
    }
}
