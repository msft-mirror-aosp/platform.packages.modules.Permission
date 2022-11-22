/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld.v31;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SEE_OTHER_PERMISSIONS_CLICKED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SHOW_SYSTEM_CLICKED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.write;

import android.Manifest;
import android.app.ActionBar;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageViewModelNew;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.settingslib.HelpUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** The main page for the privacy dashboard. */
// TODO(b/257317510): Remove "V2" suffix.
@RequiresApi(Build.VERSION_CODES.S)
public class PermissionUsageV2Fragment extends SettingsWithLargeHeader {

    private static final Map<String, Integer> PERMISSION_GROUP_ORDER =
            Map.of(
                    Manifest.permission_group.LOCATION, 0,
                    Manifest.permission_group.CAMERA, 1,
                    Manifest.permission_group.MICROPHONE, 2);
    private static final int DEFAULT_ORDER = 3;

    // Pie chart in this screen will be the first child.
    // Hence we use PERMISSION_GROUP_ORDER + 1 here.
    private static final int PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT =
            PERMISSION_GROUP_ORDER.size() + 1;
    private static final int EXPAND_BUTTON_ORDER = 999;
    /** Map to represent ordering for permission groups in the permissions usage UI. */
    private static final String KEY_SESSION_ID = "_session_id";

    private static final String SESSION_ID_KEY =
            PermissionUsageV2Fragment.class.getName() + KEY_SESSION_ID;

    private static final int MENU_SHOW_7_DAYS_DATA = Menu.FIRST + 4;
    private static final int MENU_SHOW_24_HOURS_DATA = Menu.FIRST + 5;
    private static final int MENU_REFRESH = Menu.FIRST + 6;

    private PermissionUsageViewModelNew mViewModel;

    private boolean mHasSystemApps;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private MenuItem mShow7DaysDataMenu;
    private MenuItem mShow24HoursDataMenu;
    private boolean mOtherExpanded;

    private PermissionUsageGraphicPreference mGraphic;

    /** Unique Id of a request */
    private long mSessionId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mSessionId = savedInstanceState.getLong(SESSION_ID_KEY);
        } else {
            mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        }

        PermissionUsageViewModelNew.PermissionUsageViewModelFactory factory =
                new PermissionUsageViewModelNew.PermissionUsageViewModelFactory(
                        getActivity().getApplication(), this, new Bundle());
        mViewModel = new ViewModelProvider(this, factory).get(PermissionUsageViewModelNew.class);

        // By default, do not show system app usages.
        mViewModel.updateShowSystem(false);

        // By default, show permission usages for the past 24 hours.
        mViewModel.updateShow7Days(false);

        // Start out with 'other' permissions not expanded.
        mOtherExpanded = false;

        setLoading(true, false);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mViewModel.getPermissionUsagesUiLiveData().observe(this, this::updateUI);
        mViewModel.getShowSystemLiveData().observe(this, this::updateShowSystem);
        mViewModel.getShow7DaysLiveData().observe(this, this::updateShow7Days);
    }

    @Override
    public RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        PreferenceGroupAdapter adapter =
                (PreferenceGroupAdapter) super.onCreateAdapter(preferenceScreen);

        adapter.registerAdapterDataObserver(
                new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onChanged() {
                        updatePreferenceScreenAdvancedTitleAndSummary(preferenceScreen, adapter);
                    }

                    @Override
                    public void onItemRangeInserted(int positionStart, int itemCount) {
                        onChanged();
                    }

                    @Override
                    public void onItemRangeRemoved(int positionStart, int itemCount) {
                        onChanged();
                    }

                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount) {
                        onChanged();
                    }

                    @Override
                    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                        onChanged();
                    }
                });

        updatePreferenceScreenAdvancedTitleAndSummary(preferenceScreen, adapter);
        return adapter;
    }

    private void updatePreferenceScreenAdvancedTitleAndSummary(
            PreferenceScreen preferenceScreen, PreferenceGroupAdapter adapter) {
        int count = adapter.getItemCount();
        if (count == 0) {
            return;
        }

        Preference preference = adapter.getItem(count - 1);

        // This is a hacky way of getting the expand button preference for advanced info
        if (preference.getOrder() == EXPAND_BUTTON_ORDER) {
            mOtherExpanded = false;
            preference.setTitle(R.string.perm_usage_adv_info_title);
            preference.setSummary(preferenceScreen.getSummary());
            preference.setLayoutResource(R.layout.expand_button_with_large_title);
            if (mGraphic != null) {
                mGraphic.setShowOtherCategory(false);
            }
        } else {
            mOtherExpanded = true;
            if (mGraphic != null) {
                mGraphic.setShowOtherCategory(true);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(R.string.permission_usage_title);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mHasSystemApps) {
            mShowSystemMenu =
                    menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE, R.string.menu_show_system);
            mHideSystemMenu =
                    menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE, R.string.menu_hide_system);
        }

        if (KotlinUtils.INSTANCE.is7DayToggleEnabled()) {
            mShow7DaysDataMenu =
                    menu.add(
                            Menu.NONE,
                            MENU_SHOW_7_DAYS_DATA,
                            Menu.NONE,
                            R.string.menu_show_7_days_data);
            mShow24HoursDataMenu =
                    menu.add(
                            Menu.NONE,
                            MENU_SHOW_24_HOURS_DATA,
                            Menu.NONE,
                            R.string.menu_show_24_hours_data);
        }

        HelpUtils.prepareHelpMenuItem(
                getActivity(), menu, R.string.help_permission_usage, getClass().getName());
        MenuItem refresh =
                menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.permission_usage_refresh);
        refresh.setIcon(R.drawable.ic_refresh);
        refresh.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                getActivity().finishAfterTransition();
                return true;
            case MENU_SHOW_SYSTEM:
                write(
                        PERMISSION_USAGE_FRAGMENT_INTERACTION,
                        mSessionId,
                        PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SHOW_SYSTEM_CLICKED);
                mViewModel.updateShowSystem(true);
                break;
            case MENU_HIDE_SYSTEM:
                mViewModel.updateShowSystem(false);
                break;
            case MENU_SHOW_7_DAYS_DATA:
                mViewModel.updateShow7Days(KotlinUtils.INSTANCE.is7DayToggleEnabled());
                break;
            case MENU_SHOW_24_HOURS_DATA:
                mViewModel.updateShow7Days(false);
                break;
            case MENU_REFRESH:
                // TODO(b/257314894): What should happen on refresh?
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public int getEmptyViewString() {
        return R.string.no_permission_usages;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (outState != null) {
            outState.putLong(SESSION_ID_KEY, mSessionId);
        }
    }

    private void updateShowSystem(boolean showSystem) {
        if (mHasSystemApps) {
            mShowSystemMenu.setVisible(!showSystem);
            mHideSystemMenu.setVisible(showSystem);
        }
    }

    private void updateShow7Days(boolean show7Days) {
        if (mShow7DaysDataMenu != null) {
            mShow7DaysDataMenu.setVisible(!show7Days);
        }

        if (mShow24HoursDataMenu != null) {
            mShow24HoursDataMenu.setVisible(show7Days);
        }
    }

    private void updateUI(
            PermissionUsageViewModelNew.PermissionUsagesUiData permissionUsagesUiData) {
        if (getActivity() == null) {
            return;
        }
        Context context = getActivity();

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }
        screen.removeAll();

        if (mOtherExpanded) {
            screen.setInitialExpandedChildrenCount(Integer.MAX_VALUE);
        } else {
            screen.setInitialExpandedChildrenCount(
                    PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT);
        }
        screen.setOnExpandButtonClickListener(() -> {
            write(
                    PERMISSION_USAGE_FRAGMENT_INTERACTION,
                    mSessionId,
                    PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SEE_OTHER_PERMISSIONS_CLICKED);
        });
        boolean displayShowSystemToggle = permissionUsagesUiData.getDisplayShowSystemToggle();
        Map<String, Integer> permissionGroupWithUsageCounts =
                permissionUsagesUiData.getPermissionGroupsWithUsageCount();
        List<Map.Entry<String, Integer>> permissionGroupWithUsageCountsEntries =
                new ArrayList(permissionGroupWithUsageCounts.entrySet());

        permissionGroupWithUsageCountsEntries.sort(Comparator.comparing(
                (Map.Entry<String, Integer> permissionGroupWithUsageCount) ->
                        PERMISSION_GROUP_ORDER.getOrDefault(
                                permissionGroupWithUsageCount.getKey(),
                                DEFAULT_ORDER))
                .thenComparing(
                    (Map.Entry<String, Integer> permissionGroupWithUsageCount) ->
                        KotlinUtils.INSTANCE
                                .getPermGroupLabel(
                                        context,
                                        permissionGroupWithUsageCount
                                                .getKey())
                                .toString()));

        if (mHasSystemApps != displayShowSystemToggle) {
            mHasSystemApps = displayShowSystemToggle;
            getActivity().invalidateOptionsMenu();
        }

        mGraphic =
                new PermissionUsageGraphicPreference(
                        context, permissionUsagesUiData.getShow7DaysUsage());
        screen.addPreference(mGraphic);

        mGraphic.setUsages(permissionGroupWithUsageCounts);

        // Add the preference header.
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        CharSequence advancedInfoSummary =
                getAdvancedInfoSummaryString(context, permissionGroupWithUsageCountsEntries);
        screen.setSummary(advancedInfoSummary);

        addUIContent(
                context,
                permissionGroupWithUsageCountsEntries,
                category,
                permissionUsagesUiData.getShowSystemAppPermissions(),
                permissionUsagesUiData.getShow7DaysUsage());
    }

    private CharSequence getAdvancedInfoSummaryString(
            Context context, List<Map.Entry<String, Integer>> permissionGroupWithUsageCounts) {
        int size = permissionGroupWithUsageCounts.size();
        if (size <= PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT - 1) {
            return "";
        }

        // case for 1 extra item in the advanced info
        if (size == PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT) {
            String permGroupName =
                    permissionGroupWithUsageCounts
                            .get(PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT - 1)
                            .getKey();
            return KotlinUtils.INSTANCE.getPermGroupLabel(context, permGroupName);
        }

        String permGroupName1 =
                permissionGroupWithUsageCounts
                        .get(PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT - 1)
                        .getKey();
        String permGroupName2 =
                permissionGroupWithUsageCounts
                        .get(PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT)
                        .getKey();
        CharSequence permGroupLabel1 =
                KotlinUtils.INSTANCE.getPermGroupLabel(context, permGroupName1);
        CharSequence permGroupLabel2 =
                KotlinUtils.INSTANCE.getPermGroupLabel(context, permGroupName2);

        // case for 2 extra items in the advanced info
        if (size == PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT + 1) {
            return context.getResources()
                    .getString(
                            R.string.perm_usage_adv_info_summary_2_items,
                            permGroupLabel1,
                            permGroupLabel2);
        }

        // case for 3 or more extra items in the advanced info
        int numExtraItems = size - PERMISSION_USAGE_INITIAL_EXPANDED_CHILDREN_COUNT - 1;
        return context.getResources()
                .getString(
                        R.string.perm_usage_adv_info_summary_more_items,
                        permGroupLabel1,
                        permGroupLabel2,
                        numExtraItems);
    }

    /** Use the usages and permApps that are previously constructed to add UI content to the page */
    private void addUIContent(
            Context context,
            List<Map.Entry<String, Integer>> permissionGroupWithUsageCounts,
            PreferenceCategory category,
            boolean showSystem,
            boolean show7Days) {
        for (int i = 0; i < permissionGroupWithUsageCounts.size(); i++) {
            Map.Entry<String, Integer> permissionGroupWithUsageCount =
                    permissionGroupWithUsageCounts.get(i);
            PermissionUsageV2ControlPreference permissionUsagePreference =
                    new PermissionUsageV2ControlPreference(
                            context,
                            permissionGroupWithUsageCount.getKey(),
                            permissionGroupWithUsageCount.getValue(),
                            showSystem,
                            mSessionId,
                            show7Days);
            category.addPreference(permissionUsagePreference);
        }

        setLoading(false, true);
    }
}
