/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.permissionmultidevice.cts

import android.Manifest
import android.app.Instrumentation
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM
import android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ACTION_REQUEST_PERMISSIONS
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.permission.flags.Flags
import android.permissionmultidevice.cts.PackageManagementUtils.installPackage
import android.permissionmultidevice.cts.PackageManagementUtils.uninstallPackage
import android.permissionmultidevice.cts.UiAutomatorUtils.click
import android.permissionmultidevice.cts.UiAutomatorUtils.findTextForView
import android.permissionmultidevice.cts.UiAutomatorUtils.waitFindObject
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.Display
import android.virtualdevice.cts.common.VirtualDeviceRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
class DeviceAwarePermissionGrantTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val defaultDeviceContext = instrumentation.targetContext
    private lateinit var mVirtualDeviceManager: VirtualDeviceManager
    private lateinit var mVirtualDevice: VirtualDeviceManager.VirtualDevice
    private lateinit var mVirtualDisplay: VirtualDisplay
    private lateinit var mDeviceDisplayName: String

    @get:Rule var mVirtualDeviceRule = VirtualDeviceRule.createDefault()

    @Before
    fun setup() {
        assumeFalse(PermissionUtils.isAutomotive(defaultDeviceContext))
        assumeFalse(PermissionUtils.isTv(defaultDeviceContext))
        assumeFalse(PermissionUtils.isWatch(defaultDeviceContext))

        installPackage(APP_APK_PATH_STREAMING)
        mVirtualDeviceManager =
            defaultDeviceContext.getSystemService(VirtualDeviceManager::class.java)!!
        mVirtualDevice =
            mVirtualDeviceRule.createManagedVirtualDevice(
                VirtualDeviceParams.Builder()
                    .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                    .build()
            )

        val displayConfig =
            VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder(
                    DISPLAY_WIDTH,
                    DISPLAY_HEIGHT
                )
                .setFlags(
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                )
                .build()

        mVirtualDisplay =
            mVirtualDeviceRule.createManagedVirtualDisplay(mVirtualDevice, displayConfig)!!
        mDeviceDisplayName =
            mVirtualDeviceManager.getVirtualDevice(mVirtualDevice.deviceId)!!.displayName.toString()
    }

    @After
    fun cleanup() {
        uninstallPackage(APP_PACKAGE_NAME, requireSuccess = false)
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun onHostDevice_requestPermissionForHostDevice_shouldGrantPermission() {
        testGrantPermissionForDevice(
            Display.DEFAULT_DISPLAY,
            DEVICE_ID_DEFAULT,
            false,
            "",
            expectPermissionGrantedOnDefaultDevice = true,
            expectPermissionGrantedOnRemoteDevice = false
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun onHostDevice_requestPermissionForRemoteDevice_shouldGrantPermission() {
        testGrantPermissionForDevice(
            Display.DEFAULT_DISPLAY,
            mVirtualDevice.deviceId,
            true,
            mDeviceDisplayName,
            expectPermissionGrantedOnDefaultDevice = false,
            expectPermissionGrantedOnRemoteDevice = true
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun onRemoteDevice_requestPermissionForHostDevice_shouldShowWarningDialog() {
        requestPermissionOnDevice(mVirtualDisplay.display.displayId, DEVICE_ID_DEFAULT)

        val displayId = mVirtualDisplay.display.displayId
        waitFindObject(By.displayId(displayId).textContains("Permission request suppressed"))
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun onRemoteDevice_requestPermissionForRemoteDevice_shouldGrantPermission() {
        testGrantPermissionForDevice(
            mVirtualDisplay.display.displayId,
            mVirtualDevice.deviceId,
            true,
            mDeviceDisplayName,
            expectPermissionGrantedOnDefaultDevice = false,
            expectPermissionGrantedOnRemoteDevice = true
        )
    }

    private fun testGrantPermissionForDevice(
        displayId: Int,
        targetDeviceId: Int,
        showDeviceName: Boolean,
        expectedDeviceNameInDialog: String,
        expectPermissionGrantedOnDefaultDevice: Boolean,
        expectPermissionGrantedOnRemoteDevice: Boolean
    ) {
        // Assert no permission granted to either default device or virtual device
        assertAppHasPermissionForDevice(DEVICE_ID_DEFAULT, false)
        assertAppHasPermissionForDevice(mVirtualDevice.deviceId, false)

        requestPermissionOnDevice(displayId, targetDeviceId)
        mVirtualDeviceRule.waitAndAssertActivityResumed(getPermissionDialogComponentName())

        if (showDeviceName) {
            assertPermissionMessageContainsDeviceName(displayId, expectedDeviceNameInDialog)
        }

        SystemUtil.eventually { click(By.displayId(displayId).res(ALLOW_BUTTON)) }

        assertAppHasPermissionForDevice(DEVICE_ID_DEFAULT, expectPermissionGrantedOnDefaultDevice)
        assertAppHasPermissionForDevice(
            mVirtualDevice.deviceId,
            expectPermissionGrantedOnRemoteDevice
        )
    }

    private fun requestPermissionOnDevice(displayId: Int, targetDeviceId: Int) {
        val intent =
            Intent()
                .setComponent(
                    ComponentName(APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.RequestPermissionActivity")
                )
                .putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_DEVICE_ID, targetDeviceId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        mVirtualDeviceRule.sendIntentToDisplay(intent, displayId)
    }

    private fun assertPermissionMessageContainsDeviceName(displayId: Int, deviceName: String) {
        waitFindObject(By.displayId(displayId).res(PERMISSION_MESSAGE_ID))
        val text = findTextForView(By.displayId(displayId).res(PERMISSION_MESSAGE_ID))
        Truth.assertThat(text).contains(deviceName)
    }

    private fun assertAppHasPermissionForDevice(deviceId: Int, expectPermissionGranted: Boolean) {
        val checkPermissionResult =
            defaultDeviceContext
                .createDeviceContext(deviceId)
                .packageManager
                .checkPermission(DEVICE_AWARE_PERMISSION, APP_PACKAGE_NAME)

        if (expectPermissionGranted) {
            Assert.assertEquals(PackageManager.PERMISSION_GRANTED, checkPermissionResult)
        } else {
            Assert.assertEquals(PackageManager.PERMISSION_DENIED, checkPermissionResult)
        }
    }

    private fun getPermissionDialogComponentName(): ComponentName {
        val intent = Intent(ACTION_REQUEST_PERMISSIONS)
        intent.setPackage(defaultDeviceContext.packageManager.getPermissionControllerPackageName())
        return intent.resolveActivity(defaultDeviceContext.packageManager)
    }

    companion object {
        const val APK_DIRECTORY = "/data/local/tmp/cts-permissionmultidevice"
        const val APP_APK_PATH_STREAMING = "${APK_DIRECTORY}/CtsAccessRemoteDeviceCamera.apk"
        const val APP_PACKAGE_NAME = "android.permissionmultidevice.cts.accessremotedevicecamera"
        const val PERMISSION_MESSAGE_ID = "com.android.permissioncontroller:id/permission_message"
        const val ALLOW_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button"
        const val DEVICE_ID_DEFAULT = 0
        const val PERSISTENT_DEVICE_ID_DEFAULT = VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT
        const val DEVICE_AWARE_PERMISSION = Manifest.permission.CAMERA
        private const val DISPLAY_HEIGHT = 1920
        private const val DISPLAY_WIDTH = 1080
    }
}
