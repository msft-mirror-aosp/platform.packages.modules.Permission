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

import android.Manifest
import android.app.AppOpsManager
import android.app.Instrumentation
import android.app.ecm.EnhancedConfirmationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@AppModeFull(reason = "Instant apps cannot install packages")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
@CddTest(requirement = "9.18/C-0-1")
class EnhancedConfirmationManagerTest : BaseUsePermissionTest() {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val ecm by lazy { context.getSystemService(EnhancedConfirmationManager::class.java)!! }
    private val appOpsManager by lazy { context.getSystemService(AppOpsManager::class.java)!! }
    private val packageManager by lazy { context.packageManager }

    @Rule
    @JvmField
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun assumeNotAutoOrTv() {
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        Assume.assumeFalse(packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun helpUrlActionLinkIsNotEmpty() {
        val helpUrl = getPermissionControllerResString(HELP_URL_ECM)
        assertFalse(helpUrl?.length == 0)
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun installedAppStartsWithModeDefault() {
        installPackageWithInstallSourceAndMetadataFromStore(APP_APK_NAME_LATEST)
        eventually {
            runWithShellPermissionIdentity {
                assertEquals(
                    getAppEcmState(context, appOpsManager, APP_PACKAGE_NAME),
                    AppOpsManager.MODE_DEFAULT
                )
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenStoreAppThenIsNotRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromStore(APP_APK_NAME_LATEST)
        runWithShellPermissionIdentity {
            eventually { assertFalse(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenLocalAppThenIsRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromLocalFile(APP_APK_NAME_LATEST)
        runWithShellPermissionIdentity {
            eventually { assertTrue(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenDownloadedThenAppIsRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(APP_APK_NAME_LATEST)
        runWithShellPermissionIdentity {
            eventually { assertTrue(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenExplicitlyRestrictedAppThenIsRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromStore(APP_APK_NAME_LATEST)
        eventually {
            runWithShellPermissionIdentity {
                assertEquals(
                    getAppEcmState(context, appOpsManager, APP_PACKAGE_NAME),
                    AppOpsManager.MODE_DEFAULT
                )
            }
        }
        runWithShellPermissionIdentity {
            eventually { assertFalse(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
            setAppEcmState(context, appOpsManager, APP_PACKAGE_NAME, AppOpsManager.MODE_ERRORED)
            eventually { assertTrue(ecm.isRestricted(APP_PACKAGE_NAME, PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenRestrictedAppThenIsNotRestrictedFromNonProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(APP_APK_NAME_LATEST)
        runWithShellPermissionIdentity {
            eventually { assertFalse(ecm.isRestricted(APP_PACKAGE_NAME, NON_PROTECTED_SETTING)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenRestrictedAppThenClearRestrictionNotAllowedByDefault() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(APP_APK_NAME_LATEST)
        runWithShellPermissionIdentity {
            eventually { assertFalse(ecm.isClearRestrictionAllowed(APP_PACKAGE_NAME)) }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun givenRestrictedAppWhenClearRestrictionThenNotRestrictedFromProtectedSetting() {
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(APP_APK_NAME_LATEST)
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
        installPackageWithInstallSourceAndMetadataFromDownloadedFile(APP_APK_NAME_LATEST)

        val intent = ecm.createRestrictedSettingDialogIntent(APP_PACKAGE_NAME, PROTECTED_SETTING)

        assertNotNull(intent)
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun grantDialogBlocksRestrictedPermissionsOfSameGroupTogether() {
        installPackageWithInstallSourceFromDownloadedFileAndAllowHardRestrictedPerms(
            APP_APK_NAME_LATEST
        )
        val permissionAndExpectedGrantResults =
            arrayOf(
                GROUP_2_PERMISSION_1_RESTRICTED to false,
                GROUP_2_PERMISSION_2_RESTRICTED to false
            )

        requestAppPermissionsAndAssertResult(*permissionAndExpectedGrantResults) {
            clickECMAlertDialogOKButton(waitForWindowTransition = false)
        }
        assertTrue(isClearRestrictionAllowed(APP_PACKAGE_NAME))

        requestAppPermissionsAndAssertResult(
            *permissionAndExpectedGrantResults,
            waitForWindowTransition = false
        ) {
            assertNoEcmDialogShown()
        }
        assertTrue(isClearRestrictionAllowed(APP_PACKAGE_NAME))
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun grantDialogBlocksRestrictedGroupsThenRequestsUnrestrictedGroupsDespiteOutOfOrderRequest() {
        installPackageWithInstallSourceFromDownloadedFileAndAllowHardRestrictedPerms(
            APP_APK_NAME_LATEST
        )

        requestAppPermissionsAndAssertResult(
            GROUP_3_PERMISSION_1_UNRESTRICTED to false,
            GROUP_2_PERMISSION_1_RESTRICTED to false,
            GROUP_3_PERMISSION_2_UNRESTRICTED to false,
            GROUP_2_PERMISSION_2_RESTRICTED to false,
            waitForWindowTransition = false
        ) {
            clickECMAlertDialogOKButton()
            clickPermissionRequestDenyButton()
        }
        assertTrue(isClearRestrictionAllowed(APP_PACKAGE_NAME))

        requestAppPermissionsAndAssertResult(
            GROUP_3_PERMISSION_1_UNRESTRICTED to true,
            GROUP_3_PERMISSION_2_UNRESTRICTED to true,
            waitForWindowTransition = false
        ) {
            clickPermissionRequestAllowForegroundButton()
        }
        assertTrue(isClearRestrictionAllowed(APP_PACKAGE_NAME))
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun grantDialogBlocksRestrictedGroupsThenRequestsUnrestrictedHighPriorityGroups() {
        installPackageWithInstallSourceFromDownloadedFileAndAllowHardRestrictedPerms(
            APP_APK_NAME_LATEST
        )

        requestAppPermissionsAndAssertResult(
            GROUP_3_PERMISSION_1_UNRESTRICTED to true,
            GROUP_2_PERMISSION_1_RESTRICTED to false,
            waitForWindowTransition = false
        ) {
            clickECMAlertDialogOKButton()
            clickPermissionRequestAllowForegroundButton()
        }
        assertTrue(isClearRestrictionAllowed(APP_PACKAGE_NAME))
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @Test
    fun grantDialogBlocksRestrictedGroupsThenRequestsUnrestrictedLowPriorityGroups() {
        installPackageWithInstallSourceFromDownloadedFileAndAllowHardRestrictedPerms(
            APP_APK_NAME_LATEST
        )

        requestAppPermissionsAndAssertResult(
            GROUP_4_PERMISSION_1_UNRESTRICTED to true,
            GROUP_2_PERMISSION_1_RESTRICTED to false,
            waitForWindowTransition = false
        ) {
            clickECMAlertDialogOKButton()
            clickPermissionRequestAllowForegroundButton()
        }
        assertTrue(isClearRestrictionAllowed(APP_PACKAGE_NAME))
    }

    private fun isClearRestrictionAllowed(packageName: String) = callWithShellPermissionIdentity {
        ecm.isClearRestrictionAllowed(packageName)
    }

    private fun assertNoEcmDialogShown() {
        assertNull(
            "expected to not see dialog",
            if (isWatch) {
                waitFindObjectOrNull(
                    By.text(getPermissionControllerString(ECM_ALERT_DIALOG_OK_BUTTON_TEXT)),
                    UNEXPECTED_TIMEOUT_MILLIS.toLong()
                )
            } else {
                waitFindObjectOrNull(
                    By.res(ALERT_DIALOG_OK_BUTTON),
                    UNEXPECTED_TIMEOUT_MILLIS.toLong()
                )
            }
        )
    }

    companion object {
        private const val GROUP_2_PERMISSION_1_RESTRICTED = Manifest.permission.SEND_SMS
        private const val GROUP_2_PERMISSION_2_RESTRICTED = Manifest.permission.READ_SMS
        private const val GROUP_3_PERMISSION_1_UNRESTRICTED =
            Manifest.permission.ACCESS_FINE_LOCATION
        private const val GROUP_3_PERMISSION_2_UNRESTRICTED =
            Manifest.permission.ACCESS_COARSE_LOCATION
        private const val GROUP_4_PERMISSION_1_UNRESTRICTED = Manifest.permission.BODY_SENSORS

        private const val NON_PROTECTED_SETTING = "example_setting_which_is_not_protected"
        private const val PROTECTED_SETTING = "android:bind_accessibility_service"

        @Throws(PackageManager.NameNotFoundException::class)
        private fun setAppEcmState(
            context: Context,
            appOpsManager: AppOpsManager,
            packageName: String,
            mode: Int
        ) =
            appOpsManager.setMode(
                AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS,
                getPackageUid(context, packageName),
                packageName,
                mode
            )

        @Throws(PackageManager.NameNotFoundException::class)
        private fun getAppEcmState(
            context: Context,
            appOpsManager: AppOpsManager,
            packageName: String
        ) =
            appOpsManager.noteOpNoThrow(
                AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS,
                getPackageUid(context, packageName),
                packageName,
                context.attributionTag,
                /* message */ null
            )

        @Throws(PackageManager.NameNotFoundException::class)
        private fun getPackageUid(context: Context, packageName: String) =
            getApplicationInfoAsUser(context, packageName).uid

        @Throws(PackageManager.NameNotFoundException::class)
        private fun getApplicationInfoAsUser(context: Context, packageName: String) =
            packageManager.getApplicationInfoAsUser(
                packageName,
                /* flags */ 0,
                Process.myUserHandle()
            )
    }
}
