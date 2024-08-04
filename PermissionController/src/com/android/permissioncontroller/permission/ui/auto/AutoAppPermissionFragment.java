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

package com.android.permissioncontroller.permission.ui.auto;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__GRANT_FINE_LOCATION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__REVOKE_FINE_LOCATION;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_INTERACTED;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_RESULT;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.SensorPrivacyManager;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.res.TypedArrayUtils;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.TwoStatePreference;

import com.android.car.ui.AlertDialogBuilder;
import com.android.modules.utils.build.SdkLevel;
import com.android.permission.flags.Flags;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.auto.AutoSettingsFrameFragment;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ChangeRequest;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModelFactory;
import com.android.permissioncontroller.permission.ui.v33.AdvancedConfirmDialogArgs;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.LocationUtils;
import com.android.permissioncontroller.permission.utils.PackageRemovalMonitor;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.RestrictedLockUtils;

import kotlin.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Settings related to a particular permission for the given app. */
public class AutoAppPermissionFragment extends AutoSettingsFrameFragment
        implements AppPermissionViewModel.ConfirmDialogShowingFragment {

    private static final String LOG_TAG = "AppPermissionFragment";
    private static final long POST_DELAY_MS = 20;
    private static final String BLOCKED_APP_PREF_KEY = "blocked_app";
    private static final String REQUIRED_APP_PREF_KEY = "required_app";

    @NonNull
    private TwoStatePreference mAllowPermissionPreference;
    @NonNull
    private TwoStatePreference mAlwaysPermissionPreference;
    @NonNull
    private TwoStatePreference mForegroundOnlyPermissionPreference;
    @NonNull
    private TwoStatePreference mDenyPermissionPreference;

    @NonNull
    private TwoStatePreference mToggleFineLocationPreference;
    @NonNull
    private AutoTwoTargetPreference mDetailsPreference;

    @NonNull
    private AppPermissionViewModel mViewModel;
    @NonNull
    private String mPackageName;
    @NonNull
    private String mPermGroupName;
    @NonNull
    private UserHandle mUser;
    @NonNull
    private String mPackageLabel;
    @NonNull
    private String mPermGroupLabel;
    private Drawable mPackageIcon;

    private SensorPrivacyManager mSensorPrivacyManager;
    private Collection<String> mAutomotiveLocationBypassAllowlist;
    private List<String> mCameraPrivacyAllowlist;

    /**
     * Listens for changes to the app the permission is currently getting granted to. {@code null}
     * when unregistered.
     */
    @Nullable
    private PackageRemovalMonitor mPackageRemovalMonitor;

    /**
     * Returns a new {@link AutoAppPermissionFragment}.
     *
     * @param packageName the package name for which the permission is being changed
     * @param permName    the name of the permission being changed
     * @param groupName   the name of the permission group being changed
     * @param userHandle  the user for which the permission is being changed
     */
    @NonNull
    public static AutoAppPermissionFragment newInstance(@NonNull String packageName,
            @NonNull String permName, @Nullable String groupName, @NonNull UserHandle userHandle,
            @NonNull long sessionId) {
        AutoAppPermissionFragment fragment = new AutoAppPermissionFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        if (groupName == null) {
            arguments.putString(Intent.EXTRA_PERMISSION_NAME, permName);
        } else {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermGroupName == null) {
            mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        mPackageLabel = BidiFormatter.getInstance().unicodeWrap(
                KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(), mPackageName,
                        mUser));
        mPermGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(getContext(),
                mPermGroupName).toString();
        mPackageIcon = KotlinUtils.INSTANCE.getBadgedPackageIcon(getActivity().getApplication(),
                mPackageName, mUser);
        setHeaderLabel(
                requireContext().getString(R.string.app_permission_title, mPermGroupLabel));
        if (SdkLevel.isAtLeastV()) {
            mSensorPrivacyManager = requireContext().getSystemService(SensorPrivacyManager.class);
            mCameraPrivacyAllowlist = mSensorPrivacyManager.getCameraPrivacyAllowlist();
            if (Flags.addBannersToPrivacySensitiveAppsForAaos()) {
                mAutomotiveLocationBypassAllowlist =
                        LocationUtils.getAutomotiveLocationBypassAllowlist(requireContext());
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PreferenceScreen screen = getPreferenceScreen();
        screen.addPreference(
                AutoPermissionsUtils.createHeaderPreference(requireContext(),
                        mPackageIcon, mPackageName, mPackageLabel));

        // Add permissions selector preferences.
        PreferenceGroup permissionSelector = new PreferenceCategory(requireContext());
        permissionSelector.setTitle(
                getString(R.string.app_permission_header, mPermGroupLabel));
        screen.addPreference(permissionSelector);

        mAllowPermissionPreference = new SelectedPermissionPreference(requireContext());
        mAllowPermissionPreference.setTitle(R.string.app_permission_button_allow);
        permissionSelector.addPreference(mAllowPermissionPreference);

        mAlwaysPermissionPreference = new SelectedPermissionPreference(requireContext());
        mAlwaysPermissionPreference.setTitle(R.string.app_permission_button_allow_always);
        permissionSelector.addPreference(mAlwaysPermissionPreference);

        mForegroundOnlyPermissionPreference = new SelectedPermissionPreference(requireContext());
        mForegroundOnlyPermissionPreference.setTitle(
                R.string.app_permission_button_allow_foreground);
        permissionSelector.addPreference(mForegroundOnlyPermissionPreference);

        mDenyPermissionPreference = new SelectedPermissionPreference(requireContext());
        mDenyPermissionPreference.setTitle(R.string.app_permission_button_deny);
        permissionSelector.addPreference(mDenyPermissionPreference);


        Log.i(LOG_TAG, "enableCoarseFineLocationPromptForAaos flag set to: "
                + Flags.enableCoarseFineLocationPromptForAaos());
        if (Flags.enableCoarseFineLocationPromptForAaos()) {
            mToggleFineLocationPreference = new SelectedSwitchPermissionPreference(
                    requireContext());
            mToggleFineLocationPreference.setTitle(R.string.app_permission_location_accuracy);
            mToggleFineLocationPreference.setSummary(
                    R.string.app_permission_location_accuracy_subtitle);
            permissionSelector.addPreference(mToggleFineLocationPreference);

            mToggleFineLocationPreference.setOnPreferenceClickListener(v -> {
                if (mToggleFineLocationPreference.isChecked()) {
                    requestChange(ChangeRequest.GRANT_FINE_LOCATION,
                            APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__GRANT_FINE_LOCATION);
                } else {
                    requestChange(ChangeRequest.REVOKE_FINE_LOCATION,
                            APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__REVOKE_FINE_LOCATION);
                }
                return true;
            });
        }

        mAllowPermissionPreference.setOnPreferenceClickListener(v -> {
            checkOnlyOneButtonOverride(AppPermissionViewModel.ButtonType.ALLOW);
            setResult(GrantPermissionsViewHandler.GRANTED_ALWAYS);
            requestChange(ChangeRequest.GRANT_FOREGROUND,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW);
            return true;
        });
        mAlwaysPermissionPreference.setOnPreferenceClickListener(v -> {
            checkOnlyOneButtonOverride(AppPermissionViewModel.ButtonType.ALLOW_ALWAYS);
            setResult(GrantPermissionsViewHandler.GRANTED_ALWAYS);
            requestChange(ChangeRequest.GRANT_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS);
            return true;
        });
        mForegroundOnlyPermissionPreference.setOnPreferenceClickListener(v -> {
            checkOnlyOneButtonOverride(AppPermissionViewModel.ButtonType.ALLOW_FOREGROUND);
            setResult(GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY);
            requestChange(ChangeRequest.GRANT_FOREGROUND_ONLY,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND);
            return true;
        });
        mDenyPermissionPreference.setOnPreferenceClickListener(v -> {
            checkOnlyOneButtonOverride(AppPermissionViewModel.ButtonType.DENY);
            setResult(GrantPermissionsViewHandler.DENIED);
            requestChange(ChangeRequest.REVOKE_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY);
            return true;
        });

        mDetailsPreference = new AutoTwoTargetPreference(requireContext());
        // If the details are updated, setDetail will update the visibility
        mDetailsPreference.setVisible(false);
        screen.addPreference(mDetailsPreference);
    }

    @Override
    public void onStart() {
        super.onStart();
        Activity activity = requireActivity();

        // Get notified when the package is removed.
        mPackageRemovalMonitor = new PackageRemovalMonitor(requireContext(), mPackageName) {
            @Override
            public void onPackageRemoved() {
                Log.w(LOG_TAG, mPackageName + " was uninstalled");
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
            }
        };
        mPackageRemovalMonitor.register();

        // Check if the package was removed while this activity was not started.
        try {
            activity.createPackageContextAsUser(mPackageName, /* flags= */ 0,
                    mUser).getPackageManager().getPackageInfo(mPackageName,
                    /* flags= */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(LOG_TAG, mPackageName + " was uninstalled while this activity was stopped", e);
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        }

        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        AppPermissionViewModelFactory factory = new AppPermissionViewModelFactory(
                getActivity().getApplication(), mPackageName, mPermGroupName, mUser, sessionId);
        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionViewModel.class);
        mViewModel.getButtonStateLiveData().observe(this, this::setRadioButtonsState);
        mViewModel.getDetailResIdLiveData().observe(this, this::setDetail);
        mViewModel.getShowAdminSupportLiveData().observe(this, this::setAdminSupportDetail);
        if (SdkLevel.isAtLeastV()) {
            if (Manifest.permission_group.CAMERA.equals(mPermGroupName)) {
                mViewModel.getSensorStatusLiveData().observe(this, this::setSensorStatus);
            }
            if (Flags.addBannersToPrivacySensitiveAppsForAaos()) {
                if (Manifest.permission_group.LOCATION.equals(mPermGroupName)) {
                    mViewModel.getSensorStatusLiveData().observe(this, this::setSensorStatus);
                }
                if (Manifest.permission_group.MICROPHONE.equals(mPermGroupName)) {
                    mViewModel.getSensorStatusLiveData().observe(this, this::setSensorStatus);
                }
            }
        }

        if (Flags.enableCoarseFineLocationPromptForAaos()) {
            mViewModel.getButtonStateLiveData().observe(this, buttonState -> {
                AppPermissionViewModel.ButtonState locationAccuracyState = buttonState.get(
                        AppPermissionViewModel.ButtonType.LOCATION_ACCURACY);
                mToggleFineLocationPreference.setVisible(locationAccuracyState.isShown());
                mToggleFineLocationPreference.setChecked(locationAccuracyState.isChecked());
                mToggleFineLocationPreference.setEnabled(locationAccuracyState.isEnabled());
            });
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mPackageRemovalMonitor != null) {
            mPackageRemovalMonitor.unregister();
            mPackageRemovalMonitor = null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private void setSensorStatus(Boolean sensorStatus) {
        Boolean isRequiredApp = null;
        Boolean isRequiredAppCard = null;
        if (Manifest.permission_group.CAMERA.equals(mPermGroupName)) {
            int state = mSensorPrivacyManager.getSensorPrivacyState(
                    SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE,
                    SensorPrivacyManager.Sensors.CAMERA);
            isRequiredApp = mCameraPrivacyAllowlist.contains(mPackageName);
            isRequiredAppCard =
                    state == SensorPrivacyManager.StateTypes.ENABLED_EXCEPT_ALLOWLISTED_APPS
                            && isRequiredApp;
        } else if (Manifest.permission_group.LOCATION.equals(mPermGroupName)) {
            isRequiredApp = mAutomotiveLocationBypassAllowlist.contains(mPackageName);
            isRequiredAppCard =
                    isRequiredApp && LocationUtils.isAutomotiveLocationBypassEnabled(
                            getPreferenceManager().getContext());
        } else if (Manifest.permission_group.MICROPHONE.equals(mPermGroupName)) {
            isRequiredApp = false;
            isRequiredAppCard = false;
        }

        if (isRequiredApp != null && isRequiredAppCard != null) {
            if (sensorStatus) {
                setSensorCard(isRequiredAppCard, isRequiredApp);
            } else {
                removeSensorCard(isRequiredAppCard);
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private void setSensorCard(boolean isRequiredAppCard, boolean isRequiredApp) {
        if (isRequiredAppCard) {
            setRequiredAppCard();
        } else {
            setBlockedAppCard(isRequiredApp);
        }
    }

    private void setRequiredAppCard() {
        AutoCardViewPreference sensorCard = findPreference(REQUIRED_APP_PREF_KEY);
        if (sensorCard == null) {
            sensorCard = createRequiredAppCard();
            if (getPreferenceScreen() != null) {
                getPreferenceScreen().addPreference(sensorCard);
            }
        }
        sensorCard.setVisible(true);
    }

    private void setBlockedAppCard(boolean isRequiredApp) {
        AutoCardViewPreference sensorCard = findPreference(BLOCKED_APP_PREF_KEY);
        if (sensorCard == null) {
            sensorCard = createBlockedAppCard(isRequiredApp);
            if (getPreferenceScreen() != null) {
                getPreferenceScreen().addPreference(sensorCard);
            }
        }
        sensorCard.setVisible(true);
    }

    private AutoCardViewPreference createRequiredAppCard() {
        Context context = getPreferenceManager().getContext();
        AutoCardViewPreference sensorCard = new AutoCardViewPreference(context);
        sensorCard.setKey(REQUIRED_APP_PREF_KEY);
        sensorCard.setIcon(KotlinUtils.INSTANCE.getPermGroupIcon(context, mPermGroupName));
        sensorCard.setTitle(context.getString(R.string.automotive_required_app_title));
        sensorCard.setSummary(context.getString(R.string.automotive_required_app_summary));
        sensorCard.setVisible(true);
        sensorCard.setOrder(-1);
        return sensorCard;
    }

    private AutoCardViewPreference createBlockedAppCard(boolean isRequiredApp) {
        Context context = getPreferenceManager().getContext();

        AutoCardViewPreference sensorCard = new AutoCardViewPreference(context);
        sensorCard.setKey(BLOCKED_APP_PREF_KEY);
        sensorCard.setIcon(Utils.getBlockedIcon(mPermGroupName));
        sensorCard.setTitle(context.getString(Utils.getBlockedTitleAutomotive(mPermGroupName)));
        if (isRequiredApp) {
            sensorCard.setSummary(context.getString(
                    R.string.automotive_blocked_required_app_summary));
        } else {
            sensorCard.setSummary(context.getString(
                    R.string.automotive_blocked_infotainment_app_summary));
        }
        sensorCard.setVisible(true);
        sensorCard.setOrder(-1);
        return sensorCard;
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private void removeSensorCard(boolean isRequiredAppCard) {
        if (isRequiredAppCard) {
            removeRequiredAppCard();
        } else {
            removeBlockedAppCard();
        }
    }

    private void removeRequiredAppCard() {
        AutoCardViewPreference sensorCard = findPreference(REQUIRED_APP_PREF_KEY);
        if (sensorCard != null) {
            sensorCard.setVisible(false);
        }
    }

    private void removeBlockedAppCard() {
        AutoCardViewPreference sensorCard = findPreference(BLOCKED_APP_PREF_KEY);
        if (sensorCard != null) {
            sensorCard.setVisible(false);
        }
    }

    @Override
    public void showConfirmDialog(ChangeRequest changeRequest, int messageId,
            int buttonPressed, boolean oneTime) {
        Bundle args = new Bundle();

        args.putInt(ConfirmDialog.MSG, messageId);
        args.putSerializable(ConfirmDialog.CHANGE_REQUEST, changeRequest);
        args.putSerializable(ConfirmDialog.BUTTON, buttonPressed);

        ConfirmDialog confirmDialog = new ConfirmDialog();
        confirmDialog.setArguments(args);
        confirmDialog.setTargetFragment(this, 0);
        confirmDialog.show(requireFragmentManager().beginTransaction(),
                ConfirmDialog.class.getName());
    }

    private void setResult(@GrantPermissionsViewHandler.Result int result) {
        Intent intent = new Intent()
                .putExtra(EXTRA_RESULT_PERMISSION_INTERACTED,
                        requireArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME))
                .putExtra(EXTRA_RESULT_PERMISSION_RESULT, result);
        requireActivity().setResult(Activity.RESULT_OK, intent);
    }

    private void setRadioButtonsState(
            Map<AppPermissionViewModel.ButtonType, AppPermissionViewModel.ButtonState> states) {
        setButtonState(mAllowPermissionPreference,
                states.get(AppPermissionViewModel.ButtonType.ALLOW));
        setButtonState(mAlwaysPermissionPreference,
                states.get(AppPermissionViewModel.ButtonType.ALLOW_ALWAYS));
        setButtonState(mForegroundOnlyPermissionPreference,
                states.get(AppPermissionViewModel.ButtonType.ALLOW_FOREGROUND));
        setButtonState(mDenyPermissionPreference,
                states.get(AppPermissionViewModel.ButtonType.DENY));
    }

    private void setButtonState(TwoStatePreference button,
            AppPermissionViewModel.ButtonState state) {
        button.setVisible(state.isShown());
        if (state.isShown()) {
            button.setChecked(state.isChecked());
            button.setEnabled(state.isEnabled());
        }
    }

    /**
     * Helper method to handle the UX edge case where the confirmation dialog is shown and two
     * buttons are selected at once. This happens since the Auto UI doesn't use a proper radio
     * group, so there is nothing that enforces that tapping on a button unchecks a previously
     * checked button. Apart from this case, this UI is not necessary since the UI is entirely
     * driven by the ViewModel.
     */
    private void checkOnlyOneButtonOverride(AppPermissionViewModel.ButtonType buttonType) {
        mAllowPermissionPreference.setChecked(
                buttonType == AppPermissionViewModel.ButtonType.ALLOW);
        mAlwaysPermissionPreference.setChecked(
                buttonType == AppPermissionViewModel.ButtonType.ALLOW_ALWAYS);
        mForegroundOnlyPermissionPreference.setChecked(
                buttonType == AppPermissionViewModel.ButtonType.ALLOW_FOREGROUND);
        mDenyPermissionPreference.setChecked(buttonType == AppPermissionViewModel.ButtonType.DENY);
    }

    private void setDetail(Pair<Integer, Integer> detailResIds) {
        if (detailResIds == null) {
            mDetailsPreference.setVisible(false);
            return;
        }
        if (detailResIds.getSecond() != null) {
            mDetailsPreference.setWidgetLayoutResource(R.layout.settings_preference_widget);
            mDetailsPreference.setOnSecondTargetClickListener(
                    v -> showAllPermissions(mPermGroupName));
            mDetailsPreference.setSummary(
                    getString(detailResIds.getFirst(), detailResIds.getSecond()));
        } else {
            mDetailsPreference.setSummary(detailResIds.getFirst());
        }
        mDetailsPreference.setVisible(true);
    }

    /**
     * Show all individual permissions in this group in a new fragment.
     */
    private void showAllPermissions(@NonNull String filterGroup) {
        Fragment frag = AutoAllAppPermissionsFragment.newInstance(mPackageName,
                filterGroup, mUser,
                getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID));
        requireFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("AllPerms")
                .commit();
    }

    private void setAdminSupportDetail(RestrictedLockUtils.EnforcedAdmin admin) {
        if (admin != null) {
            mDetailsPreference.setWidgetLayoutResource(R.layout.info_preference_widget);
            mDetailsPreference.setOnSecondTargetClickListener(v ->
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), admin)
            );
        }
    }

    /**
     * Request to grant/revoke permissions group.
     */
    private void requestChange(ChangeRequest changeRequest,
            int buttonClicked) {
        mViewModel.requestChange(/* setOneTime= */false, /* fragment= */ this,
                /* defaultDeny= */this, changeRequest, buttonClicked);
    }

    /** Preference used to represent apps that can be picked as a default app. */
    private static class SelectedPermissionPreference extends TwoStatePreference {

        SelectedPermissionPreference(Context context) {
            super(context, null,
                    TypedArrayUtils.getAttr(context, androidx.preference.R.attr.preferenceStyle,
                            android.R.attr.preferenceStyle));
            setPersistent(false);
            setLayoutResource(R.layout.car_radio_button_preference);
            setWidgetLayoutResource(R.layout.radio_button_preference_widget);
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);

            RadioButton radioButton = (RadioButton) holder.findViewById(R.id.radio_button);
            radioButton.setChecked(isChecked());
        }
    }

    private static class SelectedSwitchPermissionPreference extends TwoStatePreference {

        SelectedSwitchPermissionPreference(Context context) {
            super(context, null,
                    TypedArrayUtils.getAttr(context, androidx.preference.R.attr.preferenceStyle,
                            android.R.attr.preferenceStyle));
            setPersistent(false);
            setLayoutResource(R.layout.car_switch_button_preference);
            setWidgetLayoutResource(R.layout.switch_button_preference_widget);
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);

            Switch switchButton = (Switch) holder.findViewById(R.id.location_accuracy_switch);
            switchButton.setChecked(isChecked());
        }
    }

    /**
     * A dialog warning the user that they are about to deny a permission that was granted by
     * default.
     *
     * @see #showConfirmDialog(ChangeRequest, int, int, boolean)
     */
    public static class ConfirmDialog extends DialogFragment {
        private static final String MSG = ConfirmDialog.class.getName() + ".arg.msg";
        private static final String CHANGE_REQUEST = ConfirmDialog.class.getName()
                + ".arg.changeRequest";
        private static final String BUTTON = ConfirmDialog.class.getName()
                + ".arg.button";
        private static int sCode = APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW;

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // TODO(b/229024576): This code is duplicated, refactor ConfirmDialog for easier
            // NFF sharing
            boolean isGrantFileAccess = getArguments().getSerializable(CHANGE_REQUEST)
                    == ChangeRequest.GRANT_ALL_FILE_ACCESS;
            boolean isGrantStorageSupergroup = getArguments().getSerializable(CHANGE_REQUEST)
                    == ChangeRequest.GRANT_STORAGE_SUPERGROUP;
            int positiveButtonStringResId = R.string.grant_dialog_button_deny_anyway;
            if (isGrantFileAccess || isGrantStorageSupergroup) {
                positiveButtonStringResId = R.string.grant_dialog_button_allow;
            }
            AutoAppPermissionFragment fragment = (AutoAppPermissionFragment) getTargetFragment();
            return new AlertDialogBuilder(getContext())
                    .setMessage(requireArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel,
                            (dialog, which) -> dialog.cancel())
                    .setPositiveButton(positiveButtonStringResId,
                            (dialog, which) -> {
                                if (isGrantFileAccess) {
                                    fragment.mViewModel.setAllFilesAccess(true);
                                    fragment.mViewModel.requestChange(false, fragment,
                                            fragment, ChangeRequest.GRANT_BOTH, sCode);
                                } else if (isGrantStorageSupergroup) {
                                    fragment.mViewModel.requestChange(false, fragment,
                                            fragment, ChangeRequest.GRANT_BOTH, sCode);
                                } else {
                                    fragment.mViewModel.onDenyAnyWay((ChangeRequest)
                                                    getArguments().getSerializable(CHANGE_REQUEST),
                                            getArguments().getInt(BUTTON),
                                            /* oneTime= */ false);
                                }
                            })
                    .create();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            AutoAppPermissionFragment fragment = (AutoAppPermissionFragment) getTargetFragment();
            fragment.setRadioButtonsState(fragment.mViewModel.getButtonStateLiveData().getValue());
        }
    }

    @Override
    public void showAdvancedConfirmDialog(AdvancedConfirmDialogArgs args) {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                .setIcon(args.getIconId())
                .setMessage(args.getMessageId())
                .setOnCancelListener((DialogInterface dialog) -> {
                    setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
                })
                .setNegativeButton(args.getNegativeButtonTextId(),
                        (DialogInterface dialog, int which) -> {
                            setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
                        })
                .setPositiveButton(args.getPositiveButtonTextId(),
                        (DialogInterface dialog, int which) -> {
                            mViewModel.requestChange(args.getSetOneTime(),
                                    AutoAppPermissionFragment.this, AutoAppPermissionFragment.this,
                                    args.getChangeRequest(), args.getButtonClicked());
                        });
        if (args.getTitleId() != 0) {
            b.setTitle(args.getTitleId());
        }
        b.show();
    }
}
