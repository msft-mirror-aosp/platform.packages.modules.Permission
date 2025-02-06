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

package android.permission.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.OnPermissionsChangedListener;
import android.permission.flags.Flags;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "Instant apps cannot access properties of other apps")
@RunWith(AndroidJUnit4ClassRunner.class)
public class PermissionUpdateListenerTest {
    private static final String LOG_TAG = PermissionUpdateListenerTest.class.getSimpleName();
    private static final String APK =
            "/data/local/tmp/cts-permission/"
                    + "CtsAppThatRequestsCalendarContactsBodySensorCustomPermission.apk";
    private static final String PACKAGE_NAME =
            "android.permission.cts.appthatrequestcustompermission";
    private static final String PERMISSION_NAME = "android.permission.RECORD_AUDIO";
    private static final int TIMEOUT = 10000;

    private final Context mDefaultContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private final PackageManager mPackageManager = mDefaultContext.getPackageManager();

    private int mTestAppUid;

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            "android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS",
            Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
            Manifest.permission.REVOKE_RUNTIME_PERMISSIONS);

    @Before
    public void setup() throws PackageManager.NameNotFoundException, InterruptedException {
        runShellCommandOrThrow("pm install " + APK);
        // permission change events are generated for a package install, the wait helps prevent
        // those permission change events interfere with the test.
        SystemUtil.waitForBroadcasts();
        Thread.sleep(1000);
        mTestAppUid = mPackageManager.getPackageUid(PACKAGE_NAME, 0);
    }

    @After
    public void cleanup() {
        runShellCommand("pm uninstall " + PACKAGE_NAME);
    }

    @Test
    public void testGrantPermissionInvokesOldCallback() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        OnPermissionsChangedListener permissionsChangedListener =
                uid -> {
                    if (mTestAppUid == uid) {
                        countDownLatch.countDown();
                    }
                };

        mPackageManager.addOnPermissionsChangeListener(permissionsChangedListener);
        mPackageManager.grantRuntimePermission(PACKAGE_NAME, PERMISSION_NAME,
                mDefaultContext.getUser());
        countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        mPackageManager.removeOnPermissionsChangeListener(permissionsChangedListener);

        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    public void testVirtualDeviceGrantPermissionNotifyListener() throws InterruptedException {
        VirtualDevice virtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        Context deviceContext = mDefaultContext.createDeviceContext(virtualDevice.getDeviceId());
        testGrantPermissionNotifyListener(deviceContext, virtualDevice.getPersistentDeviceId());
    }

    @Test
    public void testDefaultDeviceGrantPermissionNotifyListener() throws InterruptedException {
        testGrantPermissionNotifyListener(
                mDefaultContext, VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT);
    }

    private void testGrantPermissionNotifyListener(
            Context context, String expectedDeviceId) throws InterruptedException {
        final PackageManager packageManager = context.getPackageManager();
        TestOnPermissionsChangedListener permissionsChangedListener =
                new TestOnPermissionsChangedListener(1);
        packageManager.addOnPermissionsChangeListener(permissionsChangedListener);
        packageManager.grantRuntimePermission(PACKAGE_NAME, PERMISSION_NAME,
                mDefaultContext.getUser());

        permissionsChangedListener.waitForPermissionChangedCallbacks();
        packageManager.removeOnPermissionsChangeListener(permissionsChangedListener);

        String deviceId = permissionsChangedListener.getNotifiedDeviceId(mTestAppUid);
        assertThat(deviceId).isEqualTo(expectedDeviceId);
    }

    @Test
    public void testDefaultDeviceRevokePermissionNotifyListener() throws InterruptedException {
        testRevokePermissionNotifyListener(
                mDefaultContext, VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    public void testVirtualDeviceRevokePermissionNotifyListener() throws InterruptedException {
        VirtualDevice virtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        Context deviceContext = mDefaultContext.createDeviceContext(virtualDevice.getDeviceId());
        testRevokePermissionNotifyListener(
                deviceContext, virtualDevice.getPersistentDeviceId());
    }

    private void testRevokePermissionNotifyListener(
            Context context, String expectedDeviceId) throws InterruptedException {
        final PackageManager packageManager = context.getPackageManager();
        TestOnPermissionsChangedListener permissionsChangedListener =
                new TestOnPermissionsChangedListener(1);
        packageManager.grantRuntimePermission(PACKAGE_NAME, PERMISSION_NAME,
                mDefaultContext.getUser());
        packageManager.addOnPermissionsChangeListener(permissionsChangedListener);
        packageManager.revokeRuntimePermission(PACKAGE_NAME, PERMISSION_NAME,
                mDefaultContext.getUser());

        permissionsChangedListener.waitForPermissionChangedCallbacks();
        packageManager.removeOnPermissionsChangeListener(permissionsChangedListener);

        String deviceId = permissionsChangedListener.getNotifiedDeviceId(mTestAppUid);
        assertThat(deviceId).isEqualTo(expectedDeviceId);
    }

    @Test
    public void testDefaultDeviceUpdatePermissionFlagsNotifyListener() throws InterruptedException {
        testUpdatePermissionFlagsNotifyListener(
                mDefaultContext, VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    public void testVirtualDeviceUpdatePermissionFlagsNotifyListener() throws InterruptedException {
        VirtualDevice virtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        Context deviceContext = mDefaultContext.createDeviceContext(virtualDevice.getDeviceId());
        testUpdatePermissionFlagsNotifyListener(
                deviceContext, virtualDevice.getPersistentDeviceId());
    }

    private void testUpdatePermissionFlagsNotifyListener(
            Context context, String expectedDeviceId) throws InterruptedException {
        TestOnPermissionsChangedListener permissionsChangedListener =
                new TestOnPermissionsChangedListener(1);
        final PackageManager packageManager = context.getPackageManager();
        packageManager.addOnPermissionsChangeListener(permissionsChangedListener);
        int flag = PackageManager.FLAG_PERMISSION_USER_SET;
        packageManager.updatePermissionFlags(PERMISSION_NAME, PACKAGE_NAME, flag, flag,
                mDefaultContext.getUser());
        permissionsChangedListener.waitForPermissionChangedCallbacks();
        packageManager.removeOnPermissionsChangeListener(permissionsChangedListener);

        String deviceId = permissionsChangedListener.getNotifiedDeviceId(mTestAppUid);
        assertThat(deviceId).isEqualTo(expectedDeviceId);
    }

    private class TestOnPermissionsChangedListener
            implements OnPermissionsChangedListener {
        // map of uid and persistentDeviceID
        private final Map<Integer, String> mUidDeviceIdsMap = new ConcurrentHashMap<>();
        private final CountDownLatch mCountDownLatch;

        TestOnPermissionsChangedListener(int expectedCallbackCount) {
            mCountDownLatch = new CountDownLatch(expectedCallbackCount);
        }

        @Override
        public void onPermissionsChanged(int uid) {
            // ignored when we implement the new callback.
        }

        @Override
        public void onPermissionsChanged(int uid, String deviceId) {
            if (uid == mTestAppUid) {
                mCountDownLatch.countDown();
                mUidDeviceIdsMap.put(uid, deviceId);
            }
        }

        String getNotifiedDeviceId(int uid) {
            return mUidDeviceIdsMap.get(uid);
        }

        void waitForPermissionChangedCallbacks() throws InterruptedException {
            mCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertThat(mCountDownLatch.getCount()).isEqualTo(0);
        }
    }
}
