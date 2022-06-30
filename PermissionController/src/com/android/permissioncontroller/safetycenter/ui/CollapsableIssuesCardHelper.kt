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

package com.android.permissioncontroller.safetycenter.ui

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.os.Build
import android.os.Bundle
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceGroup
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY
import com.android.safetycenter.internaldata.SafetyCenterIssueKey
import kotlin.math.max

/**
 * Helper class to hide issue cards if over a predefined limit and handle revealing hidden issue
 * cards when the more issues preference is clicked
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class CollapsableIssuesCardHelper {
    private var isQuickSettingsFragment: Boolean = false
    private var issueCardsExpanded: Boolean = false
    private var focusedSafetyCenterIssueKey: SafetyCenterIssueKey? = null

    fun setFocusedIssueKey(safetyCenterIssueKey: SafetyCenterIssueKey?) {
        focusedSafetyCenterIssueKey = safetyCenterIssueKey
    }

    /**
     * Sets QuickSetting specific state for use to determine correct issue section expansion state
     * as well ass more issues card icon values
     *
     * <p> Note the issueCardsExpanded value set here may be overridden here by calls to
     * restoreState
     *
     * @param isQuickSettingsFragment {@code true} if CollapsableIssuesCardHelper is being used in
     * quick settings fragment
     * @param issueCardsExpanded Whether issue cards should be expanded or not when added to
     * preference screen
     */
    fun setQuickSettingsState(isQuickSettingsFragment: Boolean, issueCardsExpanded: Boolean) {
        this.isQuickSettingsFragment = isQuickSettingsFragment
        this.issueCardsExpanded = issueCardsExpanded
    }

    /** Restore previously saved state from [Bundle] */
    fun restoreState(state: Bundle?) {
        if (state == null) {
            return
        }
        // Apply the previously saved state
        issueCardsExpanded = state.getBoolean(EXPAND_ISSUE_GROUP_SAVED_INSTANCE_STATE_KEY, false)
    }

    /** Save current state to provided [Bundle] */
    fun saveState(outState: Bundle) =
        outState.putBoolean(EXPAND_ISSUE_GROUP_SAVED_INSTANCE_STATE_KEY, issueCardsExpanded)

    /**
     * Add the [IssueCardPreference] managed by this helper to the specified [PreferenceGroup]
     *
     * @param context Current context
     * @param issuesPreferenceGroup Preference group to add preference to
     * @param issueCardPreferences {@link List} of {@link IssueCardPreference} to add to the
     * preference fragment
     */
    fun addIssues(
        context: Context,
        issuesPreferenceGroup: PreferenceGroup,
        issueCardPreferences: List<IssueCardPreference>
    ) {
        val (reorderedIssueCardPreferences, numberOfIssuesToShowWhenCollapsed) =
            maybeReorderFocusedSafetyCenterIssueInList(issueCardPreferences)
        val moreIssuesCardPreference =
            createMoreIssuesCardPreference(
                context,
                issuesPreferenceGroup,
                reorderedIssueCardPreferences,
                numberOfIssuesToShowWhenCollapsed)
        addIssuesToPreferenceGroupAndSetVisibility(
            issuesPreferenceGroup,
            reorderedIssueCardPreferences,
            moreIssuesCardPreference,
            numberOfIssuesToShowWhenCollapsed,
            issueCardsExpanded)
    }

    data class ReorderedSafetyCenterIssueList(
        val issueCardPreferences: List<IssueCardPreference>,
        val numberOfIssuesToShowWhenCollapsed: Int
    )
    private fun maybeReorderFocusedSafetyCenterIssueInList(
        issueCardPreferences: List<IssueCardPreference>
    ): ReorderedSafetyCenterIssueList {
        focusedSafetyCenterIssueKey?.let { focusedIssueKey ->
            val mutablePreferencesList = issueCardPreferences.toMutableList()
            val focusedIssueCardPreference: IssueCardPreference? =
                findAndRemovePreferenceInList(focusedIssueKey, mutablePreferencesList)

            // If focused issue preference found, place at/near top of list and return new list and
            // correct number of issue to show while collapsed
            focusedIssueCardPreference?.let { issueCardPreference ->
                val focusedIssuePlacement =
                    getFocusedIssuePlacement(issueCardPreference, mutablePreferencesList)
                mutablePreferencesList.add(focusedIssuePlacement.index, issueCardPreference)
                return ReorderedSafetyCenterIssueList(
                    mutablePreferencesList.toList(),
                    focusedIssuePlacement.numberForShownIssuesCollapsed)
            }
        }

        return ReorderedSafetyCenterIssueList(
            issueCardPreferences, DEFAULT_NUMBER_SHOWN_ISSUES_COLLAPSED)
    }

    private fun findAndRemovePreferenceInList(
        focusedIssueKey: SafetyCenterIssueKey,
        issueCardPreferences: MutableList<IssueCardPreference>
    ): IssueCardPreference? {
        issueCardPreferences.forEachIndexed { index, issueCardPreference ->
            if (focusedIssueKey == issueCardPreference.issueKey) {
                // Remove focused issue from current placement in list and exit loop
                issueCardPreferences.removeAt(index)
                return issueCardPreference
            }
        }

        return null
    }

    /** Defines indices and number of shown issues for use when prioritizing focused issues */
    private enum class FocusedIssuePlacement(
        val index: Int,
        val numberForShownIssuesCollapsed: Int
    ) {
        FOCUSED_ISSUE_INDEX_0(0, DEFAULT_NUMBER_SHOWN_ISSUES_COLLAPSED),
        FOCUSED_ISSUE_INDEX_1(1, DEFAULT_NUMBER_SHOWN_ISSUES_COLLAPSED + 1)
    }

    private fun getFocusedIssuePlacement(
        issueCardPreference: IssueCardPreference,
        issueCardPreferenceList: List<IssueCardPreference>
    ): FocusedIssuePlacement {
        return if (issueCardPreferenceList.isEmpty() ||
            issueCardPreferenceList[0].severityLevel <= issueCardPreference.severityLevel) {
            FocusedIssuePlacement.FOCUSED_ISSUE_INDEX_0
        } else {
            FocusedIssuePlacement.FOCUSED_ISSUE_INDEX_1
        }
    }

    private fun createMoreIssuesCardPreference(
        context: Context,
        issuesPreferenceGroup: PreferenceGroup,
        issueCardPreferences: List<IssueCardPreference>,
        numberOfIssuesToShowWhenCollapsed: Int
    ): MoreIssuesCardPreference {
        val prefIconResourceId =
            if (isQuickSettingsFragment) R.drawable.ic_chevron_right else R.drawable.ic_expand_more
        val numberOfHiddenIssue: Int =
            getNumberOfHiddenIssues(issueCardPreferences, numberOfIssuesToShowWhenCollapsed)
        val firstHiddenIssueSeverityLevel: Int =
            getFirstHiddenIssueSeverityLevel(
                issueCardPreferences, numberOfIssuesToShowWhenCollapsed)

        return MoreIssuesCardPreference(
            context, prefIconResourceId, numberOfHiddenIssue, firstHiddenIssueSeverityLevel) {
            if (isQuickSettingsFragment) {
                goToSafetyCenter(context)
            } else {
                expand(issuesPreferenceGroup)
            }
            true
        }
    }

    private fun expand(issuesPreferenceGroup: PreferenceGroup) {
        if (issueCardsExpanded) {
            return
        }

        val numberOfPreferences = issuesPreferenceGroup.preferenceCount
        for (i in 0 until numberOfPreferences) {
            when (val preference = issuesPreferenceGroup.getPreference(i)) {
                // IssueCardPreference can all be visible now
                is IssueCardPreference -> preference.isVisible = true
                // MoreIssuesCardPreference must be hidden after expansion of issues
                is MoreIssuesCardPreference -> preference.isVisible = false
                // Other types are undefined, no-op
                else -> continue
            }
        }
        issueCardsExpanded = true
    }

    private fun goToSafetyCenter(context: Context) {
        // Navigate to Safety center with issues expanded
        val safetyCenterIntent = Intent(ACTION_SAFETY_CENTER)
        safetyCenterIntent.putExtra(EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY, true)
        context.startActivity(safetyCenterIntent)
    }

    companion object {
        private const val EXPAND_ISSUE_GROUP_SAVED_INSTANCE_STATE_KEY =
            "expand_issue_group_saved_instance_state_key"
        private const val DEFAULT_NUMBER_SHOWN_ISSUES_COLLAPSED = 1

        private fun getNumberOfHiddenIssues(
            issueCardPreferences: List<IssueCardPreference>,
            numberOfIssuesToShowWhenCollapsed: Int
        ): Int = max(0, issueCardPreferences.size - numberOfIssuesToShowWhenCollapsed)

        private fun getFirstHiddenIssueSeverityLevel(
            issueCardPreferences: List<IssueCardPreference>,
            numberOfIssuesToShowWhenCollapsed: Int
        ): Int {
            // Index of first hidden issue (zero based) is equal to number of shown issues when
            // collapsed
            val indexOfFirstHiddenIssue: Int = numberOfIssuesToShowWhenCollapsed
            val firstHiddenIssue: IssueCardPreference? =
                issueCardPreferences.getOrNull(indexOfFirstHiddenIssue)
            // If no first hidden issue, default to ISSUE_SEVERITY_LEVEL_OK
            return firstHiddenIssue?.severityLevel ?: ISSUE_SEVERITY_LEVEL_OK
        }

        private fun addIssuesToPreferenceGroupAndSetVisibility(
            issuesPreferenceGroup: PreferenceGroup,
            issueCardPreferences: List<IssueCardPreference>,
            moreIssuesCardPreference: MoreIssuesCardPreference,
            numberOfIssuesToShowWhenCollapsed: Int,
            issueCardsExpanded: Boolean
        ) {
            // Index of first hidden issue (zero based) is equal to number of shown issues when
            // collapsed
            val indexOfFirstHiddenIssue: Int = numberOfIssuesToShowWhenCollapsed
            issueCardPreferences.forEachIndexed { index, issueCardPreference ->
                if (index == indexOfFirstHiddenIssue && !issueCardsExpanded) {
                    issuesPreferenceGroup.addPreference(moreIssuesCardPreference)
                }
                issueCardPreference.isVisible =
                    index < indexOfFirstHiddenIssue || issueCardsExpanded
                issuesPreferenceGroup.addPreference(issueCardPreference)
            }
        }
    }
}
