/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.permissioncontroller.wear.permission.components.material3

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExpandableState
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.foundation.expandableItems
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.scrollTransform
import com.android.permissioncontroller.wear.permission.components.AnnotatedText
import com.android.permissioncontroller.wear.permission.components.ListScopeWrapper
import com.android.permissioncontroller.wear.permission.components.material2.Wear2Scaffold
import com.android.permissioncontroller.wear.permission.components.rememberDrawablePainter
import com.android.permissioncontroller.wear.permission.components.theme.ResourceHelper
import com.android.permissioncontroller.wear.permission.components.theme.WearPermissionMaterialUIVersion
import com.android.permissioncontroller.wear.permission.components.theme.WearPermissionMaterialUIVersion.MATERIAL2_5
import com.android.permissioncontroller.wear.permission.components.theme.WearPermissionTheme

private class TransformingScopeConverter(private val scope: TransformingLazyColumnScope) :
    ListScopeWrapper {
    override fun item(key: Any?, contentType: Any?, content: @Composable () -> Unit) {
        // TODO:https://buganizer.corp.google.com/issues/389093588.
        scope.item { Box(modifier = Modifier.scrollTransform(this)) { content() } }
    }

    override fun expandableItems(
        state: ExpandableState,
        count: Int,
        key: ((Int) -> Any)?,
        itemContent: @Composable (BoxScope.(Int) -> Unit),
    ) {
        throw Exception("Expandable Items are not implemented on TLC Yet. Use SLC.")
    }
}

private class ScalingScopeConverter(private val scope: ScalingLazyListScope) : ListScopeWrapper {
    override fun item(key: Any?, contentType: Any?, content: @Composable () -> Unit) {
        scope.item { content() }
    }

    override fun expandableItems(
        state: ExpandableState,
        count: Int,
        key: ((Int) -> Any)?,
        itemContent: @Composable (BoxScope.(Int) -> Unit),
    ) {
        scope.expandableItems(state, count, key, itemContent)
    }
}

/**
 * This component is wrapper on material scaffold component. It helps with time text, scroll
 * indicator and standard list elements like title, icon and subtitle.
 */
@Composable
fun WearPermissionScaffold(
    materialUIVersion: WearPermissionMaterialUIVersion = ResourceHelper.materialUIVersionInSettings,
    asScalingList: Boolean = false,
    showTimeText: Boolean,
    title: String?,
    subtitle: CharSequence?,
    image: Any?,
    isLoading: Boolean,
    content: ListScopeWrapper.() -> Unit,
    titleTestTag: String? = null,
    subtitleTestTag: String? = null,
) {

    if (materialUIVersion == MATERIAL2_5) {
        Wear2Scaffold(
            showTimeText,
            title,
            subtitle,
            image,
            isLoading,
            { content.invoke(ScalingScopeConverter(this)) },
            titleTestTag,
            subtitleTestTag,
        )
    } else {
        WearPermissionScaffoldInternal(
            asScalingList = asScalingList,
            showTimeText = showTimeText,
            title = title,
            subtitle = subtitle,
            image = image,
            isLoading = isLoading,
            content = content,
            titleTestTag = titleTestTag,
            subtitleTestTag = subtitleTestTag,
        )
    }
}

@Composable
private fun WearPermissionScaffoldInternal(
    asScalingList: Boolean = false,
    showTimeText: Boolean,
    title: String?,
    subtitle: CharSequence?,
    image: Any?,
    isLoading: Boolean,
    content: ListScopeWrapper.() -> Unit,
    titleTestTag: String? = null,
    subtitleTestTag: String? = null,
) {

    val scalingListState = rememberScalingLazyListState()
    val transformingLazyColumnState = rememberTransformingLazyColumnState()
    val listState = if (asScalingList) scalingListState else transformingLazyColumnState
    val scrollInfoProvider =
        if (asScalingList) ScrollInfoProvider(scalingListState)
        else ScrollInfoProvider(transformingLazyColumnState)
    val positionIndicator =
        if (asScalingList) wearPermissionScrollIndicator(!isLoading, scalingListState)
        else wearPermissionScrollIndicator(!isLoading, transformingLazyColumnState)

    WearPermissionTheme(version = WearPermissionMaterialUIVersion.MATERIAL3) {
        AppScaffold(timeText = wearPermissionTimeText(showTimeText && !isLoading)) {
            ScreenScaffold(
                scrollInfoProvider = scrollInfoProvider,
                scrollIndicator = positionIndicator,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumnView(
                            asScalingList = asScalingList,
                            showTimeText = showTimeText,
                            listState = listState,
                            title = title,
                            subtitle = subtitle,
                            image = image,
                            content = content,
                            titleTestTag = titleTestTag,
                            subtitleTestTag = subtitleTestTag,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.LazyColumnView(
    asScalingList: Boolean = false,
    showTimeText: Boolean,
    listState: ScrollableState,
    title: String?,
    subtitle: CharSequence?,
    image: Any?,
    content: ListScopeWrapper.() -> Unit,
    titleTestTag: String? = null,
    subtitleTestTag: String? = null,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val paddingDefaults =
        WearPermissionScaffoldPaddingDefaults(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
    val painterImage = image?.let { painterFromImage(image = image) }
    val scrollContentPadding =
        if (showTimeText) {
            paddingDefaults.scrollContentPadding
        } else {
            paddingDefaults.scrollContentPaddingForDialogs(painterImage == null)
        }

    fun BoxScope.scrollingViewContent(scopeWrapper: ListScopeWrapper) {
        with(scopeWrapper) {
            iconItem(
                painter = painterImage,
                modifier = Modifier.size(IconButtonDefaults.LargeIconSize),
            )
            titleItem(
                text = title,
                testTag = titleTestTag,
                contentPaddingValues = paddingDefaults.titlePaddingValues(subtitle == null),
            )
            subtitleItem(
                text = subtitle,
                testTag = subtitleTestTag,
                modifier =
                    Modifier.align(Alignment.Center).padding(paddingDefaults.subTitlePaddingValues),
            )
            content()
        }
    }

    if (asScalingList) {
        ScalingLazyColumn(
            contentPadding = scrollContentPadding,
            state = listState as ScalingLazyListState,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            content = { scrollingViewContent(ScalingScopeConverter(this)) },
        )
    } else {
        TransformingLazyColumn(
            contentPadding = scrollContentPadding,
            state = listState as TransformingLazyColumnState,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            content = { scrollingViewContent(TransformingScopeConverter(this)) },
        )
    }
}

private fun wearPermissionTimeText(showTime: Boolean): @Composable () -> Unit {
    return if (showTime) {
        { TimeText { time() } }
    } else {
        {}
    }
}

private fun wearPermissionScrollIndicator(
    showIndicator: Boolean,
    columnState: TransformingLazyColumnState,
): @Composable (BoxScope.() -> Unit)? {
    return if (showIndicator) {
        { ScrollIndicator(modifier = Modifier.align(Alignment.CenterEnd), state = columnState) }
    } else {
        null
    }
}

private fun wearPermissionScrollIndicator(
    showIndicator: Boolean,
    columnState: ScalingLazyListState,
): @Composable (BoxScope.() -> Unit)? {
    return if (showIndicator) {
        { ScrollIndicator(modifier = Modifier.align(Alignment.CenterEnd), state = columnState) }
    } else {
        null
    }
}

@Composable
private fun painterFromImage(image: Any?): Painter? {
    return when (image) {
        is Int -> painterResource(id = image)
        is Drawable -> rememberDrawablePainter(image)
        else -> null
    }
}

private fun Modifier.optionalTestTag(tag: String?): Modifier {
    if (tag == null) {
        return this
    }
    return this then testTag(tag)
}

private fun ListScopeWrapper.iconItem(painter: Painter?, modifier: Modifier = Modifier) =
    painter?.let {
        item {
            val iconColor = WearPermissionButtonStyle.Secondary.material3ButtonColors().iconColor
            Image(
                painter = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier,
                colorFilter = ColorFilter.tint(iconColor),
            )
        }
    }

private fun ListScopeWrapper.titleItem(
    text: String?,
    testTag: String?,
    contentPaddingValues: PaddingValues,
    modifier: Modifier = Modifier,
) =
    text?.let {
        item(contentType = "header") {
            ListHeader(
                modifier = modifier.requiredHeightIn(1.dp), // We do not want default min height
                contentPadding = contentPaddingValues,
            ) {
                Text(
                    text = it,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.optionalTestTag(testTag),
                    style = MaterialTheme.typography.titleLarge.copy(hyphens = Hyphens.Auto),
                )
            }
        }
    }

private fun ListScopeWrapper.subtitleItem(
    text: CharSequence?,
    testTag: String?,
    modifier: Modifier = Modifier,
) =
    text?.let {
        item {
            AnnotatedText(
                text = it,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                modifier = modifier.optionalTestTag(testTag),
                shouldCapitalize = true,
            )
        }
    }
