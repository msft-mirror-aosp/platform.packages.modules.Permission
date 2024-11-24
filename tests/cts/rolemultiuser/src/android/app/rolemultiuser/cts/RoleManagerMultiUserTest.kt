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
package android.app.rolemultiuser.cts

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.test.filters.SdkSuppress
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.workProfile
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasPrivateProfile
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireRunNotOnSecondaryUser
import com.android.bedstead.multiuser.privateProfile
import com.android.bedstead.multiuser.secondaryUser
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL
import com.android.bedstead.permissions.CommonPermissions.MANAGE_DEFAULT_APPLICATIONS
import com.android.bedstead.permissions.CommonPermissions.MANAGE_ROLE_HOLDERS
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeFalse
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
@RunWith(BedsteadJUnit4::class)
class RoleManagerMultiUserTest {
    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL, MANAGE_ROLE_HOLDERS)
    @Test
    @Throws(Exception::class)
    fun cannotGetActiveUserForNonCrossUserRole() {
        assertThrows(IllegalArgumentException::class.java) {
            roleManager.getActiveUserForRole(RoleManager.ROLE_SYSTEM_ACTIVITY_RECOGNIZER)
        }
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureDoesNotHavePermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    @Throws(Exception::class)
    fun cannotGetActiveUserForRoleWithoutInteractAcrossUserPermission() {
        assertThrows(SecurityException::class.java) {
            roleManager.getActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME)
        }
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @EnsureDoesNotHavePermission(MANAGE_ROLE_HOLDERS, MANAGE_DEFAULT_APPLICATIONS)
    @Test
    @Throws(Exception::class)
    fun cannotGetActiveUserForRoleWithoutManageRoleAndManageDefaultApplicationsPermission() {
        assertThrows(SecurityException::class.java) {
            roleManager.getActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME)
        }
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL, MANAGE_ROLE_HOLDERS)
    @Test
    @Throws(Exception::class)
    fun cannotSetActiveUserForNonCrossUserRole() {
        assertThrows(IllegalArgumentException::class.java) {
            roleManager.setActiveUserForRole(
                RoleManager.ROLE_SYSTEM_ACTIVITY_RECOGNIZER,
                Process.myUserHandle(),
                0,
            )
        }
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureDoesNotHavePermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    @Throws(Exception::class)
    fun cannotSetActiveUserForRoleWithoutInteractAcrossUserPermission() {
        assertThrows(SecurityException::class.java) {
            roleManager.setActiveUserForRole(
                PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME,
                Process.myUserHandle(),
                0,
            )
        }
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @EnsureDoesNotHavePermission(MANAGE_ROLE_HOLDERS, MANAGE_DEFAULT_APPLICATIONS)
    @Test
    @Throws(Exception::class)
    fun cannotSetActiveUserForRoleWithoutManageRoleAndManageDefaultApplicationsPermission() {
        assertThrows(SecurityException::class.java) {
            roleManager.setActiveUserForRole(
                PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME,
                Process.myUserHandle(),
                0,
            )
        }
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL, MANAGE_ROLE_HOLDERS)
    @Test
    @Throws(Exception::class)
    fun cannotSetActiveUserForRoleToNonExistentUser() {
        val targetActiveUser = users().nonExisting().userHandle()
        roleManager.setActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME, targetActiveUser, 0)

        assertThat(roleManager.getActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME))
            .isNotEqualTo(targetActiveUser)
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL, MANAGE_ROLE_HOLDERS)
    @EnsureHasPrivateProfile(installInstrumentedApp = OptionalBoolean.TRUE)
    @Test
    @Throws(Exception::class)
    fun cannotSetActiveUserForRoleToPrivateProfileUser() {
        val targetActiveUser = deviceState.privateProfile().userHandle()
        roleManager.setActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME, targetActiveUser, 0)

        assertThat(roleManager.getActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME))
            .isNotEqualTo(targetActiveUser)
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL, MANAGE_ROLE_HOLDERS)
    @EnsureHasAdditionalUser(installInstrumentedApp = OptionalBoolean.TRUE)
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    @Test
    @Throws(Exception::class)
    fun cannotSetActiveUserForRoleToUserNotInProfileGroup() {
        val targetActiveUser = deviceState.secondaryUser().userHandle()
        roleManager.setActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME, targetActiveUser, 0)

        assertThat(roleManager.getActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME))
            .isNotEqualTo(targetActiveUser)
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL, MANAGE_ROLE_HOLDERS)
    @EnsureDoesNotHavePermission(MANAGE_DEFAULT_APPLICATIONS)
    @EnsureHasWorkProfile(installInstrumentedApp = OptionalBoolean.TRUE)
    @Test
    @Throws(Exception::class)
    fun setAndGetActiveUserForRoleSetCurrentUserWithManageRoleHoldersPermission() {
        assumeFalse(
            "setActiveUser not supported for private profile",
            users().current().type().name() == PRIVATE_PROFILE_TYPE_NAME,
        )

        val targetActiveUser = users().current().userHandle()
        roleManager.setActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME, targetActiveUser, 0)
        assertThat(roleManager.getActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME))
            .isEqualTo(targetActiveUser)
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL, MANAGE_DEFAULT_APPLICATIONS)
    @EnsureDoesNotHavePermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasWorkProfile(installInstrumentedApp = OptionalBoolean.TRUE)
    @Test
    @Throws(Exception::class)
    fun setAndGetActiveUserForRoleSetCurrentUserWithManageDefaultApplicationPermission() {
        assumeFalse(
            "setActiveUser not supported for private profile",
            users().current().type().name() == PRIVATE_PROFILE_TYPE_NAME,
        )

        val targetActiveUser = users().current().userHandle()
        roleManager.setActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME, targetActiveUser, 0)
        assertThat(roleManager.getActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME))
            .isEqualTo(targetActiveUser)
    }

    @RequireFlagsEnabled(com.android.permission.flags.Flags.FLAG_CROSS_USER_ROLE_ENABLED)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL, MANAGE_ROLE_HOLDERS)
    @EnsureHasWorkProfile(installInstrumentedApp = OptionalBoolean.TRUE)
    @Test
    @Throws(Exception::class)
    fun setAndGetActiveUserForRoleSetWorkProfile() {
        val targetActiveUser = deviceState.workProfile().userHandle()
        roleManager.setActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME, targetActiveUser, 0)

        assertThat(roleManager.getActiveUserForRole(PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME))
            .isEqualTo(targetActiveUser)
    }

    companion object {
        private const val PROFILE_GROUP_EXCLUSIVITY_ROLE_NAME =
            RoleManager.ROLE_RESERVED_FOR_TESTING_PROFILE_GROUP_EXCLUSIVITY
        private const val PRIVATE_PROFILE_TYPE_NAME = "android.os.usertype.profile.PRIVATE"
        private val context: Context = context().instrumentedContext()
        private val roleManager: RoleManager = context.getSystemService(RoleManager::class.java)

        @JvmField @ClassRule @Rule val deviceState = DeviceState()
    }
}
