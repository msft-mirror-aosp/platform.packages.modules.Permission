/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.role.controller.model;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.permission.flags.Flags;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.modules.utils.build.SdkLevel;
import com.android.role.controller.behavior.BrowserRoleBehavior;
import com.android.role.controller.util.ResourceUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Parser for {@link Role} definitions.
 */
@VisibleForTesting
public class RoleParser {

    /**
     * Function to retrieve the roles.xml resource from a context
     */
    public static volatile Function<Context, XmlResourceParser> sGetRolesXml;

    private static final String LOG_TAG = RoleParser.class.getSimpleName();

    private static final String TAG_ROLES = "roles";
    private static final String TAG_PERMISSION_SET = "permission-set";
    private static final String TAG_PERMISSION = "permission";
    private static final String TAG_ROLE = "role";
    private static final String TAG_REQUIRED_COMPONENTS = "required-components";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_RECEIVER = "receiver";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_DATA = "data";
    private static final String TAG_META_DATA = "meta-data";
    private static final String TAG_PERMISSIONS = "permissions";
    private static final String TAG_APP_OP_PERMISSIONS = "app-op-permissions";
    private static final String TAG_APP_OP_PERMISSION = "app-op-permission";
    private static final String TAG_APP_OPS = "app-ops";
    private static final String TAG_APP_OP = "app-op";
    private static final String TAG_PREFERRED_ACTIVITIES = "preferred-activities";
    private static final String TAG_PREFERRED_ACTIVITY = "preferred-activity";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_ALLOW_BYPASSING_QUALIFICATION =
            "allowBypassingQualification";
    private static final String ATTRIBUTE_BEHAVIOR = "behavior";
    private static final String ATTRIBUTE_DEFAULT_HOLDERS = "defaultHolders";
    private static final String ATTRIBUTE_DESCRIPTION = "description";
    private static final String ATTRIBUTE_EXCLUSIVE = "exclusive";
    private static final String ATTRIBUTE_EXCLUSIVITY = "exclusivity";
    private static final String ATTRIBUTE_FALL_BACK_TO_DEFAULT_HOLDER = "fallBackToDefaultHolder";
    private static final String ATTRIBUTE_FEATURE_FLAG = "featureFlag";
    private static final String ATTRIBUTE_LABEL = "label";
    private static final String ATTRIBUTE_MAX_SDK_VERSION = "maxSdkVersion";
    private static final String ATTRIBUTE_MIN_SDK_VERSION = "minSdkVersion";
    private static final String ATTRIBUTE_ONLY_GRANT_WHEN_ADDED = "onlyGrantWhenAdded";
    private static final String ATTRIBUTE_OVERRIDE_USER_WHEN_GRANTING = "overrideUserWhenGranting";
    private static final String ATTRIBUTE_QUERY_FLAGS = "queryFlags";
    private static final String ATTRIBUTE_REQUEST_TITLE = "requestTitle";
    private static final String ATTRIBUTE_REQUEST_DESCRIPTION = "requestDescription";
    private static final String ATTRIBUTE_REQUESTABLE = "requestable";
    private static final String ATTRIBUTE_SEARCH_KEYWORDS = "searchKeywords";
    private static final String ATTRIBUTE_SHORT_LABEL = "shortLabel";
    private static final String ATTRIBUTE_SHOW_NONE = "showNone";
    private static final String ATTRIBUTE_STATIC = "static";
    private static final String ATTRIBUTE_SYSTEM_ONLY = "systemOnly";
    private static final String ATTRIBUTE_UI_BEHAVIOR = "uiBehavior";
    private static final String ATTRIBUTE_VISIBLE = "visible";
    private static final String ATTRIBUTE_FLAGS = "flags";
    private static final String ATTRIBUTE_MIN_TARGET_SDK_VERSION = "minTargetSdkVersion";
    private static final String ATTRIBUTE_OPTIONAL_MIN_SDK_VERSION = "optionalMinSdkVersion";
    private static final String ATTRIBUTE_PERMISSION = "permission";
    private static final String ATTRIBUTE_PROHIBITED = "prohibited";
    private static final String ATTRIBUTE_VALUE = "value";
    private static final String ATTRIBUTE_SCHEME = "scheme";
    private static final String ATTRIBUTE_MIME_TYPE = "mimeType";
    private static final String ATTRIBUTE_MAX_TARGET_SDK_VERSION = "maxTargetSdkVersion";
    private static final String ATTRIBUTE_MODE = "mode";

    private static final String BEHAVIOR_PACKAGE_NAME = BrowserRoleBehavior.class.getPackage()
            .getName();

    private static final String MODE_NAME_ALLOWED = "allowed";
    private static final String MODE_NAME_IGNORED = "ignored";
    private static final String MODE_NAME_ERRORED = "errored";
    private static final String MODE_NAME_DEFAULT = "default";
    private static final String MODE_NAME_FOREGROUND = "foreground";
    private static final ArrayMap<String, Integer> sModeNameToMode = new ArrayMap<>();
    static {
        sModeNameToMode.put(MODE_NAME_ALLOWED, AppOpsManager.MODE_ALLOWED);
        sModeNameToMode.put(MODE_NAME_IGNORED, AppOpsManager.MODE_IGNORED);
        sModeNameToMode.put(MODE_NAME_ERRORED, AppOpsManager.MODE_ERRORED);
        sModeNameToMode.put(MODE_NAME_DEFAULT, AppOpsManager.MODE_DEFAULT);
        sModeNameToMode.put(MODE_NAME_FOREGROUND, AppOpsManager.MODE_FOREGROUND);
    }

    private static final String EXCLUSIVITY_NONE = "none";
    private static final String EXCLUSIVITY_USER = "user";
    private static final String EXCLUSIVITY_PROFILE_GROUP = "profileGroup";

    private static final Supplier<Boolean> sFeatureFlagFallback = () -> false;

    private static final ArrayMap<Class<?>, Class<?>> sPrimitiveToWrapperClass = new ArrayMap<>();
    static {
        sPrimitiveToWrapperClass.put(Boolean.TYPE, Boolean.class);
        sPrimitiveToWrapperClass.put(Byte.TYPE, Byte.class);
        sPrimitiveToWrapperClass.put(Character.TYPE, Character.class);
        sPrimitiveToWrapperClass.put(Short.TYPE, Short.class);
        sPrimitiveToWrapperClass.put(Integer.TYPE, Integer.class);
        sPrimitiveToWrapperClass.put(Long.TYPE, Long.class);
        sPrimitiveToWrapperClass.put(Float.TYPE, Float.class);
        sPrimitiveToWrapperClass.put(Double.TYPE, Double.class);
        sPrimitiveToWrapperClass.put(Void.TYPE, Void.class);
    }

    @NonNull
    private final Context mContext;

    private final boolean mThrowOnError;

    public RoleParser(@NonNull Context context) {
        this(context, false);
    }

    @VisibleForTesting
    public RoleParser(@NonNull Context context, boolean throwOnError) {
        mContext = context;
        mThrowOnError = throwOnError;
    }

    /**
     * Parse the roles defined in {@code roles.xml}.
     *
     * @return a map from role name to {@link Role} instances
     */
    @NonNull
    public ArrayMap<String, Role> parse() {
        Pair<ArrayMap<String, PermissionSet>, ArrayMap<String, Role>> xml = parseRolesXml();
        if (xml == null) {
            return new ArrayMap<>();
        }
        return xml.second;
    }

    @Nullable
    @VisibleForTesting
    public Pair<ArrayMap<String, PermissionSet>, ArrayMap<String, Role>> parseRolesXml() {
        try (XmlResourceParser parser = getRolesXml()) {
            return parseXml(parser);
        } catch (XmlPullParserException | IOException e) {
            throwOrLogMessage("Unable to parse roles.xml", e);
            return null;
        }
    }

    /**
     * Retrieves the roles.xml resource from a context
     */
    private XmlResourceParser getRolesXml() {
        if (SdkLevel.isAtLeastV() && Flags.systemServerRoleControllerEnabled()) {
            Resources resources = ResourceUtils.getPermissionControllerResources(mContext);
            int resourceId = resources.getIdentifier("roles", "xml",
                    ResourceUtils.RESOURCE_PACKAGE_NAME_PERMISSION_CONTROLLER);
            return resources.getXml(resourceId);
        } else {
            return sGetRolesXml.apply(mContext);
        }
    }

    @Nullable
    private Pair<ArrayMap<String, PermissionSet>, ArrayMap<String, Role>> parseXml(
            @NonNull XmlResourceParser parser) throws IOException, XmlPullParserException {
        Pair<ArrayMap<String, PermissionSet>, ArrayMap<String, Role>> xml = null;

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            if (parser.getName().equals(TAG_ROLES)) {
                if (xml != null) {
                    throwOrLogMessage("Duplicate <roles>");
                    skipCurrentTag(parser);
                    continue;
                }
                xml = parseRoles(parser);
            } else {
                throwOrLogForUnknownTag(parser);
                skipCurrentTag(parser);
            }
        }

        if (xml == null) {
            throwOrLogMessage("Missing <roles>");
        }
        return xml;
    }

    @NonNull
    private Pair<ArrayMap<String, PermissionSet>, ArrayMap<String, Role>> parseRoles(
            @NonNull XmlResourceParser parser) throws IOException, XmlPullParserException {
        ArrayMap<String, PermissionSet> permissionSets = new ArrayMap<>();
        ArrayMap<String, Role> roles = new ArrayMap<>();

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            switch (parser.getName()) {
                case TAG_PERMISSION_SET: {
                    PermissionSet permissionSet = parsePermissionSet(parser);
                    if (permissionSet == null) {
                        continue;
                    }
                    validateNoDuplicateElement(permissionSet.getName(), permissionSets.keySet(),
                            "permission set");
                    permissionSets.put(permissionSet.getName(), permissionSet);
                    break;
                }
                case TAG_ROLE: {
                    Role role = parseRole(parser, permissionSets);
                    if (role == null) {
                        continue;
                    }
                    validateNoDuplicateElement(role.getName(), roles.keySet(), "role");
                    roles.put(role.getName(), role);
                    break;
                }
                default:
                    throwOrLogForUnknownTag(parser);
                    skipCurrentTag(parser);
            }
        }

        return new Pair<>(permissionSets, roles);
    }

    @Nullable
    private PermissionSet parsePermissionSet(@NonNull XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        String name = requireAttributeValue(parser, ATTRIBUTE_NAME, TAG_PERMISSION_SET);
        if (name == null) {
            skipCurrentTag(parser);
            return null;
        }

        Supplier<Boolean> featureFlag = getAttributeMethodValue(parser, ATTRIBUTE_FEATURE_FLAG,
                boolean.class, sFeatureFlagFallback, TAG_PERMISSION_SET);

        int minSdkVersion = getAttributeIntValue(parser, ATTRIBUTE_MIN_SDK_VERSION,
                Build.VERSION_CODES.BASE);
        int optionalMinSdkVersion = getAttributeIntValue(parser, ATTRIBUTE_OPTIONAL_MIN_SDK_VERSION,
                minSdkVersion);

        List<Permission> permissions = new ArrayList<>();

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            if (parser.getName().equals(TAG_PERMISSION)) {
                Permission permission = parsePermission(parser, TAG_PERMISSION);
                if (permission == null) {
                    continue;
                }
                Supplier<Boolean> mergedFeatureFlag =
                        mergeFeatureFlags(permission.getFeatureFlag(), featureFlag);
                int mergedMinSdkVersion = Math.max(permission.getMinSdkVersion(), minSdkVersion);
                int mergedOptionalMinSdkVersion = Math.max(permission.getOptionalMinSdkVersion(),
                        optionalMinSdkVersion);
                permission = permission.withFeatureFlagAndSdkVersions(mergedFeatureFlag,
                        mergedMinSdkVersion, mergedOptionalMinSdkVersion);
                validateNoDuplicateElement(permission, permissions, "permission");
                permissions.add(permission);
            } else {
                throwOrLogForUnknownTag(parser);
                skipCurrentTag(parser);
            }
        }

        return new PermissionSet(name, permissions);
    }

    @Nullable
    private Permission parsePermission(@NonNull XmlResourceParser parser,
            @NonNull String tagName) throws IOException, XmlPullParserException {
        String name = requireAttributeValue(parser, ATTRIBUTE_NAME, tagName);
        if (name == null) {
            skipCurrentTag(parser);
            return null;
        }

        Supplier<Boolean> featureFlag = getAttributeMethodValue(parser, ATTRIBUTE_FEATURE_FLAG,
                boolean.class, sFeatureFlagFallback, TAG_PERMISSION);

        int minSdkVersion = getAttributeIntValue(parser, ATTRIBUTE_MIN_SDK_VERSION,
                Build.VERSION_CODES.BASE);
        int optionalMinSdkVersion = getAttributeIntValue(parser, ATTRIBUTE_OPTIONAL_MIN_SDK_VERSION,
                minSdkVersion);

        return new Permission(name, featureFlag, minSdkVersion, optionalMinSdkVersion);
    }

    @Nullable
    private Role parseRole(@NonNull XmlResourceParser parser,
            @NonNull ArrayMap<String, PermissionSet> permissionSets) throws IOException,
            XmlPullParserException {
        String name = requireAttributeValue(parser, ATTRIBUTE_NAME, TAG_ROLE);
        if (name == null) {
            skipCurrentTag(parser);
            return null;
        }

        boolean allowBypassingQualification = getAttributeBooleanValue(parser,
                ATTRIBUTE_ALLOW_BYPASSING_QUALIFICATION, false);

        String behaviorClassSimpleName = getAttributeValue(parser, ATTRIBUTE_BEHAVIOR);
        RoleBehavior behavior;
        if (behaviorClassSimpleName != null) {
            String behaviorClassName = BEHAVIOR_PACKAGE_NAME + '.' + behaviorClassSimpleName;
            try {
                behavior = (RoleBehavior) Class.forName(behaviorClassName).newInstance();
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                throwOrLogMessage("Unable to instantiate behavior: " + behaviorClassName, e);
                skipCurrentTag(parser);
                return null;
            }
        } else {
            behavior = null;
        }

        String defaultHoldersResourceName = getAttributeValue(parser, ATTRIBUTE_DEFAULT_HOLDERS);

        int descriptionResource = getAttributeResourceValue(parser, ATTRIBUTE_DESCRIPTION, 0);

        boolean visible = getAttributeBooleanValue(parser, ATTRIBUTE_VISIBLE, true);
        Integer labelResource;
        Integer shortLabelResource;
        if (visible) {
            if (descriptionResource == 0) {
                skipCurrentTag(parser);
                return null;
            }

            labelResource = requireAttributeResourceValue(parser, ATTRIBUTE_LABEL, 0, TAG_ROLE);
            if (labelResource == null) {
                skipCurrentTag(parser);
                return null;
            }

            shortLabelResource = requireAttributeResourceValue(parser, ATTRIBUTE_SHORT_LABEL, 0,
                    TAG_ROLE);
            if (shortLabelResource == null) {
                skipCurrentTag(parser);
                return null;
            }
        } else {
            labelResource = 0;
            shortLabelResource = 0;
        }

        int exclusivity;
        if (com.android.permission.flags.Flags.crossUserRoleEnabled()) {
            String exclusivityName = requireAttributeValue(parser, ATTRIBUTE_EXCLUSIVITY, TAG_ROLE);
            if (exclusivityName == null) {
                skipCurrentTag(parser);
                return null;
            }
            switch (exclusivityName) {
                case EXCLUSIVITY_NONE:
                    exclusivity = Role.EXCLUSIVITY_NONE;
                    break;
                case EXCLUSIVITY_USER:
                    exclusivity = Role.EXCLUSIVITY_USER;
                    break;
                case EXCLUSIVITY_PROFILE_GROUP:
                    // TODO(b/372743073): change to isAtLeastB once available
                    // EXCLUSIVITY_PROFILE behavior only available for B+
                    // fallback to default of EXCLUSIVITY_USER
                    exclusivity = SdkLevel.isAtLeastV()
                            ? Role.EXCLUSIVITY_PROFILE_GROUP
                            : Role.EXCLUSIVITY_USER;
                    break;
                default:
                    throwOrLogMessage("Invalid value for \"exclusivity\" on <role>: " + name
                            + ", exclusivity: " + exclusivityName);
                    skipCurrentTag(parser);
                    return null;
            }
        } else {
            Boolean exclusive =
                    requireAttributeBooleanValue(parser, ATTRIBUTE_EXCLUSIVE, true, TAG_ROLE);
            if (exclusive == null) {
                skipCurrentTag(parser);
                return null;
            }
            exclusivity = exclusive ? Role.EXCLUSIVITY_USER : Role.EXCLUSIVITY_NONE;
        }


        boolean fallBackToDefaultHolder = getAttributeBooleanValue(parser,
                ATTRIBUTE_FALL_BACK_TO_DEFAULT_HOLDER, false);

        Supplier<Boolean> featureFlag = getAttributeMethodValue(parser, ATTRIBUTE_FEATURE_FLAG,
                boolean.class, sFeatureFlagFallback, TAG_ROLE);

        int maxSdkVersion = getAttributeIntValue(parser, ATTRIBUTE_MAX_SDK_VERSION,
                Build.VERSION_CODES.CUR_DEVELOPMENT);
        int minSdkVersion = getAttributeIntValue(parser, ATTRIBUTE_MIN_SDK_VERSION,
                Build.VERSION_CODES.BASE);
        if (minSdkVersion > maxSdkVersion) {
            throwOrLogMessage("minSdkVersion " + minSdkVersion
                    + " cannot be greater than maxSdkVersion " + maxSdkVersion + " for role: "
                    + name);
            skipCurrentTag(parser);
            return null;
        }

        boolean onlyGrantWhenAdded = getAttributeBooleanValue(parser,
                ATTRIBUTE_ONLY_GRANT_WHEN_ADDED, false);

        boolean overrideUserWhenGranting = getAttributeBooleanValue(parser,
                ATTRIBUTE_OVERRIDE_USER_WHEN_GRANTING, false);

        boolean requestable = getAttributeBooleanValue(parser, ATTRIBUTE_REQUESTABLE, visible);
        Integer requestDescriptionResource;
        Integer requestTitleResource;
        if (requestable) {
            requestDescriptionResource = requireAttributeResourceValue(parser,
                    ATTRIBUTE_REQUEST_DESCRIPTION, 0, TAG_ROLE);
            if (requestDescriptionResource == null) {
                skipCurrentTag(parser);
                return null;
            }

            requestTitleResource = requireAttributeResourceValue(parser, ATTRIBUTE_REQUEST_TITLE, 0,
                    TAG_ROLE);
            if (requestTitleResource == null) {
                skipCurrentTag(parser);
                return null;
            }
        } else {
            requestDescriptionResource = 0;
            requestTitleResource = 0;
        }

        int searchKeywordsResource = getAttributeResourceValue(parser, ATTRIBUTE_SEARCH_KEYWORDS,
                0);

        boolean showNone = getAttributeBooleanValue(parser, ATTRIBUTE_SHOW_NONE, false);
        if (showNone && exclusivity == Role.EXCLUSIVITY_NONE) {
            throwOrLogMessage("showNone=\"true\" is invalid for a non-exclusive role: " + name);
            skipCurrentTag(parser);
            return null;
        }

        boolean statik = getAttributeBooleanValue(parser, ATTRIBUTE_STATIC, false);
        if (statik && (visible || requestable)) {
            throwOrLogMessage("static=\"true\" is invalid for a visible or requestable role: "
                    + name);
            skipCurrentTag(parser);
            return null;
        }

        boolean systemOnly = getAttributeBooleanValue(parser, ATTRIBUTE_SYSTEM_ONLY, false);

        String uiBehaviorName = getAttributeValue(parser, ATTRIBUTE_UI_BEHAVIOR);

        List<RequiredComponent> requiredComponents = null;
        List<Permission> permissions = null;
        List<Permission> appOpPermissions = null;
        List<AppOp> appOps = null;
        List<PreferredActivity> preferredActivities = null;

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            switch (parser.getName()) {
                case TAG_REQUIRED_COMPONENTS:
                    if (requiredComponents != null) {
                        throwOrLogMessage("Duplicate <required-components> in role: " + name);
                        skipCurrentTag(parser);
                        continue;
                    }
                    requiredComponents = parseRequiredComponents(parser);
                    break;
                case TAG_PERMISSIONS:
                    if (permissions != null) {
                        throwOrLogMessage("Duplicate <permissions> in role: " + name);
                        skipCurrentTag(parser);
                        continue;
                    }
                    permissions = parsePermissions(parser, permissionSets);
                    break;
                case TAG_APP_OP_PERMISSIONS:
                    if (appOpPermissions != null) {
                        throwOrLogMessage("Duplicate <app-op-permissions> in role: " + name);
                        skipCurrentTag(parser);
                        continue;
                    }
                    appOpPermissions = parseAppOpPermissions(parser);
                    break;
                case TAG_APP_OPS:
                    if (appOps != null) {
                        throwOrLogMessage("Duplicate <app-ops> in role: " + name);
                        skipCurrentTag(parser);
                        continue;
                    }
                    appOps = parseAppOps(parser);
                    break;
                case TAG_PREFERRED_ACTIVITIES:
                    if (preferredActivities != null) {
                        throwOrLogMessage("Duplicate <preferred-activities> in role: " + name);
                        skipCurrentTag(parser);
                        continue;
                    }
                    preferredActivities = parsePreferredActivities(parser);
                    break;
                default:
                    throwOrLogForUnknownTag(parser);
                    skipCurrentTag(parser);
            }
        }

        if (requiredComponents == null) {
            requiredComponents = Collections.emptyList();
        }
        if (permissions == null) {
            permissions = Collections.emptyList();
        }
        if (appOpPermissions == null) {
            appOpPermissions = Collections.emptyList();
        }
        if (appOps == null) {
            appOps = Collections.emptyList();
        }
        if (preferredActivities == null) {
            preferredActivities = Collections.emptyList();
        }
        return new Role(name, allowBypassingQualification, behavior, defaultHoldersResourceName,
                descriptionResource, exclusivity, fallBackToDefaultHolder, featureFlag,
                labelResource, maxSdkVersion, minSdkVersion, onlyGrantWhenAdded,
                overrideUserWhenGranting, requestDescriptionResource, requestTitleResource,
                requestable, searchKeywordsResource, shortLabelResource, showNone, statik,
                systemOnly, visible, requiredComponents, permissions, appOpPermissions, appOps,
                preferredActivities, uiBehaviorName);
    }

    @NonNull
    private List<RequiredComponent> parseRequiredComponents(
            @NonNull XmlResourceParser parser) throws IOException, XmlPullParserException {
        List<RequiredComponent> requiredComponents = new ArrayList<>();

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            String name = parser.getName();
            switch (name) {
                case TAG_ACTIVITY:
                case TAG_PROVIDER:
                case TAG_RECEIVER:
                case TAG_SERVICE: {
                    RequiredComponent requiredComponent = parseRequiredComponent(parser, name);
                    if (requiredComponent == null) {
                        continue;
                    }
                    validateNoDuplicateElement(requiredComponent, requiredComponents,
                            "require component");
                    requiredComponents.add(requiredComponent);
                    break;
                }
                default:
                    throwOrLogForUnknownTag(parser);
                    skipCurrentTag(parser);
            }
        }

        return requiredComponents;
    }

    @Nullable
    private RequiredComponent parseRequiredComponent(@NonNull XmlResourceParser parser,
            @NonNull String name) throws IOException, XmlPullParserException {
        int minTargetSdkVersion = getAttributeIntValue(parser, ATTRIBUTE_MIN_TARGET_SDK_VERSION,
                Build.VERSION_CODES.BASE);
        int flags = getAttributeIntValue(parser, ATTRIBUTE_FLAGS, 0);
        String permission = getAttributeValue(parser, ATTRIBUTE_PERMISSION);
        int queryFlags = getAttributeIntValue(parser, ATTRIBUTE_QUERY_FLAGS, 0);
        IntentFilterData intentFilterData = null;
        List<RequiredMetaData> metaData = new ArrayList<>();
        List<String> validationMetaDataNames = mThrowOnError ? new ArrayList<>() : null;

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            switch (parser.getName()) {
                case TAG_INTENT_FILTER:
                    if (intentFilterData != null) {
                        throwOrLogMessage("Duplicate <intent-filter> in <" + name + ">");
                        skipCurrentTag(parser);
                        continue;
                    }
                    intentFilterData = parseIntentFilterData(parser);
                    break;
                case TAG_META_DATA:
                    String metaDataName = requireAttributeValue(parser, ATTRIBUTE_NAME,
                            TAG_META_DATA);
                    if (metaDataName == null) {
                        continue;
                    }
                    if (mThrowOnError) {
                        validateNoDuplicateElement(metaDataName, validationMetaDataNames,
                                "meta data");
                    }
                    // HACK: Only support boolean for now.
                    // TODO(b/211568084): Support android:resource and other types of android:value,
                    // maybe by switching to TypedArray and styleables.
                    Boolean metaDataValue = requireAttributeBooleanValue(parser, ATTRIBUTE_VALUE,
                            false, TAG_META_DATA);
                    if (metaDataValue == null) {
                        continue;
                    }
                    boolean metaDataProhibited = getAttributeBooleanValue(parser,
                            ATTRIBUTE_PROHIBITED, false);
                    RequiredMetaData requiredMetaData = new RequiredMetaData(metaDataName,
                            metaDataValue, metaDataProhibited);
                    metaData.add(requiredMetaData);
                    if (mThrowOnError) {
                        validationMetaDataNames.add(metaDataName);
                    }
                    break;
                default:
                    throwOrLogForUnknownTag(parser);
                    skipCurrentTag(parser);
            }
        }

        if (intentFilterData == null) {
            throwOrLogMessage("Missing <intent-filter> in <" + name + ">");
            return null;
        }
        switch (name) {
            case TAG_ACTIVITY:
                return new RequiredActivity(intentFilterData, minTargetSdkVersion, flags,
                        permission, queryFlags, metaData);
            case TAG_PROVIDER:
                return new RequiredContentProvider(intentFilterData, minTargetSdkVersion, flags,
                        permission, queryFlags, metaData);
            case TAG_RECEIVER:
                return new RequiredBroadcastReceiver(intentFilterData, minTargetSdkVersion, flags,
                        permission, queryFlags, metaData);
            case TAG_SERVICE:
                return new RequiredService(intentFilterData, minTargetSdkVersion, flags, permission,
                        queryFlags, metaData);
            default:
                throwOrLogMessage("Unknown tag <" + name + ">");
                return null;
        }
    }

    @Nullable
    private IntentFilterData parseIntentFilterData(@NonNull XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        String action = null;
        List<String> categories = new ArrayList<>();
        boolean hasData = false;
        String dataScheme = null;
        String dataType = null;

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            switch (parser.getName()) {
                case TAG_ACTION:
                    if (action != null) {
                        throwOrLogMessage("Duplicate <action> in <intent-filter>");
                        skipCurrentTag(parser);
                        continue;
                    }
                    action = requireAttributeValue(parser, ATTRIBUTE_NAME, TAG_ACTION);
                    break;
                case TAG_CATEGORY: {
                    String category = requireAttributeValue(parser, ATTRIBUTE_NAME, TAG_CATEGORY);
                    if (category == null) {
                        continue;
                    }
                    validateIntentFilterCategory(category);
                    validateNoDuplicateElement(category, categories, "category");
                    categories.add(category);
                    break;
                }
                case TAG_DATA:
                    if (!hasData) {
                        hasData = true;
                    } else {
                        throwOrLogMessage("Duplicate <data> in <intent-filter>");
                        skipCurrentTag(parser);
                        continue;
                    }
                    dataScheme = getAttributeValue(parser, ATTRIBUTE_SCHEME);
                    dataType = getAttributeValue(parser, ATTRIBUTE_MIME_TYPE);
                    if (dataType != null) {
                        validateIntentFilterDataType(dataType);
                    }
                    break;
                default:
                    throwOrLogForUnknownTag(parser);
                    skipCurrentTag(parser);
            }
        }

        if (action == null) {
            throwOrLogMessage("Missing <action> in <intent-filter>");
            return null;
        }
        return new IntentFilterData(action, categories, dataScheme, dataType);
    }

    private void validateIntentFilterCategory(@NonNull String category) {
        if (Objects.equals(category, Intent.CATEGORY_DEFAULT)) {
            throwOrLogMessage("<category> should not include " + Intent.CATEGORY_DEFAULT);
        }
    }

    /**
     * Validates the data type with the same logic in {@link
     * android.content.IntentFilter#addDataType(String)} to prevent the {@code
     * MalformedMimeTypeException}.
     */
    private void validateIntentFilterDataType(@NonNull String type) {
        int slashIndex = type.indexOf('/');
        if (slashIndex <= 0 || type.length() < slashIndex + 2) {
            throwOrLogMessage("Invalid attribute \"mimeType\" value on <data>: " + type);
        }
    }

    @NonNull
    private List<Permission> parsePermissions(@NonNull XmlResourceParser parser,
            @NonNull ArrayMap<String, PermissionSet> permissionSets) throws IOException,
            XmlPullParserException {
        List<Permission> permissions = new ArrayList<>();

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            switch (parser.getName()) {
                case TAG_PERMISSION_SET: {
                    String permissionSetName = requireAttributeValue(parser, ATTRIBUTE_NAME,
                            TAG_PERMISSION_SET);
                    if (permissionSetName == null) {
                        continue;
                    }
                    PermissionSet permissionSet = permissionSets.get(permissionSetName);
                    if (permissionSet == null) {
                        throwOrLogMessage("Unknown permission set:" + permissionSetName);
                        continue;
                    }
                    Supplier<Boolean> featureFlag = getAttributeMethodValue(parser,
                            ATTRIBUTE_FEATURE_FLAG, boolean.class, sFeatureFlagFallback,
                            TAG_PERMISSION_SET);
                    int minSdkVersion = getAttributeIntValue(parser, ATTRIBUTE_MIN_SDK_VERSION,
                            Build.VERSION_CODES.BASE);
                    int optionalMinSdkVersion = getAttributeIntValue(parser,
                            ATTRIBUTE_OPTIONAL_MIN_SDK_VERSION, minSdkVersion);
                    List<Permission> permissionsInSet = permissionSet.getPermissions();
                    int permissionsInSetSize = permissionsInSet.size();
                    for (int permissionsInSetIndex = 0;
                            permissionsInSetIndex < permissionsInSetSize; permissionsInSetIndex++) {
                        Permission permission = permissionsInSet.get(permissionsInSetIndex);
                        Supplier<Boolean> mergedFeatureFlag =
                                mergeFeatureFlags(permission.getFeatureFlag(), featureFlag);
                        int mergedMinSdkVersion =
                                Math.max(permission.getMinSdkVersion(), minSdkVersion);
                        int mergedOptionalMinSdkVersion = Math.max(
                                permission.getOptionalMinSdkVersion(), optionalMinSdkVersion);
                        permission = permission.withFeatureFlagAndSdkVersions(mergedFeatureFlag,
                                mergedMinSdkVersion, mergedOptionalMinSdkVersion);
                        // We do allow intersection between permission sets.
                        permissions.add(permission);
                    }
                    break;
                }
                case TAG_PERMISSION: {
                    Permission permission = parsePermission(parser, TAG_PERMISSION);
                    if (permission == null) {
                        continue;
                    }
                    validateNoDuplicateElement(permission, permissions, "permission");
                    permissions.add(permission);
                    break;
                }
                default:
                    throwOrLogForUnknownTag(parser);
                    skipCurrentTag(parser);
            }
        }

        return permissions;
    }

    @NonNull
    private List<Permission> parseAppOpPermissions(@NonNull XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        List<Permission> appOpPermissions = new ArrayList<>();

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            if (parser.getName().equals(TAG_APP_OP_PERMISSION)) {
                Permission appOpPermission = parsePermission(parser, TAG_APP_OP_PERMISSION);
                if (appOpPermission == null) {
                    continue;
                }
                validateNoDuplicateElement(appOpPermission, appOpPermissions, "app op permission");
                appOpPermissions.add(appOpPermission);
            } else {
                throwOrLogForUnknownTag(parser);
                skipCurrentTag(parser);
            }
        }

        return appOpPermissions;
    }

    @NonNull
    private List<AppOp> parseAppOps(@NonNull XmlResourceParser parser) throws IOException,
            XmlPullParserException {
        List<String> appOpNames = new ArrayList<>();
        List<AppOp> appOps = new ArrayList<>();

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            if (parser.getName().equals(TAG_APP_OP)) {
                String name = requireAttributeValue(parser, ATTRIBUTE_NAME, TAG_APP_OP);
                if (name == null) {
                    continue;
                }
                validateNoDuplicateElement(name, appOpNames, "app op");
                appOpNames.add(name);
                Supplier<Boolean> featureFlag = getAttributeMethodValue(parser,
                        ATTRIBUTE_FEATURE_FLAG, boolean.class, sFeatureFlagFallback, TAG_APP_OP);
                Integer maxTargetSdkVersion = getAttributeIntValue(parser,
                        ATTRIBUTE_MAX_TARGET_SDK_VERSION, Integer.MIN_VALUE);
                if (maxTargetSdkVersion == Integer.MIN_VALUE) {
                    maxTargetSdkVersion = null;
                }
                if (maxTargetSdkVersion != null && maxTargetSdkVersion < Build.VERSION_CODES.BASE) {
                    throwOrLogMessage("Invalid value for \"maxTargetSdkVersion\": "
                            + maxTargetSdkVersion);
                }
                int minSdkVersion = getAttributeIntValue(parser,
                        ATTRIBUTE_MIN_SDK_VERSION, Build.VERSION_CODES.BASE);
                String modeName = requireAttributeValue(parser, ATTRIBUTE_MODE, TAG_APP_OP);
                if (modeName == null) {
                    continue;
                }
                int modeIndex = sModeNameToMode.indexOfKey(modeName);
                if (modeIndex < 0) {
                    throwOrLogMessage("Unknown value for \"mode\" on <app-op>: " + modeName);
                    continue;
                }
                int mode = sModeNameToMode.valueAt(modeIndex);
                AppOp appOp = new AppOp(name, featureFlag, maxTargetSdkVersion, minSdkVersion,
                        mode);
                appOps.add(appOp);
            } else {
                throwOrLogForUnknownTag(parser);
                skipCurrentTag(parser);
            }
        }

        return appOps;
    }

    @NonNull
    private List<PreferredActivity> parsePreferredActivities(
            @NonNull XmlResourceParser parser) throws IOException, XmlPullParserException {
        List<PreferredActivity> preferredActivities = new ArrayList<>();

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            if (parser.getName().equals(TAG_PREFERRED_ACTIVITY)) {
                PreferredActivity preferredActivity = parsePreferredActivity(parser);
                if (preferredActivity == null) {
                    continue;
                }
                validateNoDuplicateElement(preferredActivity, preferredActivities,
                        "preferred activity");
                preferredActivities.add(preferredActivity);
            } else {
                throwOrLogForUnknownTag(parser);
                skipCurrentTag(parser);
            }
        }

        return preferredActivities;
    }

    @Nullable
    private PreferredActivity parsePreferredActivity(@NonNull XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        RequiredActivity activity = null;
        List<IntentFilterData> intentFilterDatas = new ArrayList<>();

        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlResourceParser.END_TAG)) {
            if (depth > innerDepth || type != XmlResourceParser.START_TAG) {
                continue;
            }

            switch (parser.getName()) {
                case TAG_ACTIVITY:
                    if (activity != null) {
                        throwOrLogMessage("Duplicate <activity> in <preferred-activity>");
                        skipCurrentTag(parser);
                        continue;
                    }
                    activity = (RequiredActivity) parseRequiredComponent(parser, TAG_ACTIVITY);
                    break;
                case TAG_INTENT_FILTER:
                    IntentFilterData intentFilterData = parseIntentFilterData(parser);
                    if (intentFilterData == null) {
                        continue;
                    }
                    validateNoDuplicateElement(intentFilterData, intentFilterDatas,
                            "intent filter");
                    if (intentFilterData.getDataType() != null) {
                        throwOrLogMessage("mimeType in <data> is not supported when setting a"
                                + " preferred activity");
                    }
                    intentFilterDatas.add(intentFilterData);
                    break;
                default:
                    throwOrLogForUnknownTag(parser);
                    skipCurrentTag(parser);
            }
        }

        if (activity == null) {
            throwOrLogMessage("Missing <activity> in <preferred-activity>");
            return null;
        }
        if (intentFilterDatas.isEmpty()) {
            throwOrLogMessage("Missing <intent-filter> in <preferred-activity>");
            return null;
        }
        return new PreferredActivity(activity, intentFilterDatas);
    }

    private void skipCurrentTag(@NonNull XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        int type;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && (parser.getDepth() >= innerDepth || type != XmlResourceParser.END_TAG)) {
            // Do nothing
        }
    }

    @Nullable
    private String getAttributeValue(@NonNull XmlResourceParser parser,
            @NonNull String name) {
        return parser.getAttributeValue(null, name);
    }

    @Nullable
    private String requireAttributeValue(@NonNull XmlResourceParser parser,
            @NonNull String name, @NonNull String tagName) {
        String value = getAttributeValue(parser, name);
        if (value == null) {
            throwOrLogMessage("Missing attribute \"" + name + "\" on <" + tagName + ">");
        }
        return value;
    }

    private boolean getAttributeBooleanValue(@NonNull XmlResourceParser parser,
            @NonNull String name, boolean defaultValue) {
        return parser.getAttributeBooleanValue(null, name, defaultValue);
    }

    @Nullable
    private Boolean requireAttributeBooleanValue(@NonNull XmlResourceParser parser,
            @NonNull String name, boolean defaultValue, @NonNull String tagName) {
        String value = requireAttributeValue(parser, name, tagName);
        if (value == null) {
            return null;
        }
        return getAttributeBooleanValue(parser, name, defaultValue);
    }

    private int getAttributeIntValue(@NonNull XmlResourceParser parser,
            @NonNull String name, int defaultValue) {
        return parser.getAttributeIntValue(null, name, defaultValue);
    }

    private int getAttributeResourceValue(@NonNull XmlResourceParser parser,
            @NonNull String name, int defaultValue) {
        return parser.getAttributeResourceValue(null, name, defaultValue);
    }

    @Nullable
    private Integer requireAttributeResourceValue(@NonNull XmlResourceParser parser,
            @NonNull String name, int defaultValue, @NonNull String tagName) {
        String value = requireAttributeValue(parser, name, tagName);
        if (value == null) {
            return null;
        }
        return getAttributeResourceValue(parser, name, defaultValue);
    }

    @Nullable
    private <T> Supplier<T> getAttributeMethodValue(@NonNull XmlResourceParser parser,
            @NonNull String name, @NonNull Class<T> returnType, @Nullable Supplier<T> fallbackValue,
            @NonNull String tagName) {
        String value = parser.getAttributeValue(null, name);
        if (value == null) {
            return null;
        }
        int lastDotIndex = value.lastIndexOf('.');
        if (lastDotIndex == -1) {
            throwOrLogMessage("Invalid method \"" + value + "\" for \"" + name + "\" on <" + tagName
                    + ">");
            return fallbackValue;
        }
        String className = applyJarjarTransformIfNeeded(value.substring(0, lastDotIndex));
        String methodName = value.substring(lastDotIndex + 1);
        Method method;
        try {
            Class<?> clazz = Class.forName(className);
            method = clazz.getMethod(methodName);
        } catch (Exception e) {
            throwOrLogMessage("Cannot find method \"" + value + "\" for \"" + name + "\" on <"
                    + tagName + ">", e);
            return fallbackValue;
        }
        int methodModifiers = method.getModifiers();
        if ((methodModifiers & Modifier.PUBLIC) != Modifier.PUBLIC) {
            throwOrLogMessage("Non-public method \"" + value + "\" for \"" + name + "\" on <"
                    + tagName + ">");
            return fallbackValue;
        }
        if ((methodModifiers & Modifier.STATIC) != Modifier.STATIC) {
            throwOrLogMessage("Non-static method \"" + value + "\" for \"" + name + "\" on <"
                    + tagName + ">");
            return fallbackValue;
        }
        Class<?> methodReturnType = method.getReturnType();
        if (!primitiveToWrapperClass(returnType).isAssignableFrom(
                primitiveToWrapperClass(methodReturnType))) {
            throwOrLogMessage("Unexpected return type for method \"" + value + "\" for \""
                    + name + "\" on <" + tagName + ">, expected:" + returnType.getName()
                    + ", found: " + methodReturnType.getName());
            return fallbackValue;
        }
        return new Supplier<T>() {
            @Override
            public T get() {
                try {
                    //noinspection unchecked
                    return (T) method.invoke(null);
                } catch (Exception e) {
                    throwOrLogMessage("Failed to invoke method \"" + value + "\" for \"" + name
                            + "\" on <" + tagName + ">", e);
                    return fallbackValue.get();
                }
            }
            @Override
            public String toString() {
                return "Method{name=" + value + "}";
            }
        };
    }

    // LINT.IfChange(applyJarjarTransformIfNeeded)
    /**
     * Simulate the jarjar transform that should happen on the class name.
     * <p>
     * Currently this only handles the {@code Flags} classes for feature flagging.
     */
    @NonNull
    private String applyJarjarTransformIfNeeded(@NonNull String className) {
        if (className.endsWith(".Flags") && Objects.equals(mContext.getPackageName(), "android")) {
            return "com.android.permission.jarjar." + className;
        }
        return className;
    }
    // LINT.ThenChange()

    /**
     * Return the wrapper class for the given class if it's a primitive type, or the class itself
     * otherwise.
     */
    @NonNull
    private Class<?> primitiveToWrapperClass(@NonNull Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return sPrimitiveToWrapperClass.get(clazz);
        }
        return clazz;
    }

    @Nullable
    private Supplier<Boolean> mergeFeatureFlags(@Nullable Supplier<Boolean> featureFlag1,
            @Nullable Supplier<Boolean> featureFlag2) {
        if (featureFlag1 == null) {
            return featureFlag2;
        }
        if (featureFlag2 == null) {
            return featureFlag1;
        }
        return () -> featureFlag1.get() && featureFlag2.get();
    }

    private <T> void validateNoDuplicateElement(@NonNull T element,
            @NonNull Collection<T> collection, @NonNull String name) {
        if (collection.contains(element)) {
            throwOrLogMessage("Duplicate " + name + ": " + element);
        }
    }

    private void throwOrLogMessage(String message) {
        if (mThrowOnError) {
            throw new IllegalArgumentException(message);
        } else {
            Log.wtf(LOG_TAG, message);
        }
    }

    private void throwOrLogMessage(String message, Throwable cause) {
        if (mThrowOnError) {
            throw new IllegalArgumentException(message, cause);
        } else {
            Log.wtf(LOG_TAG, message, cause);
        }
    }

    private void throwOrLogForUnknownTag(@NonNull XmlResourceParser parser) {
        throwOrLogMessage("Unknown tag: " + parser.getName());
    }
}
