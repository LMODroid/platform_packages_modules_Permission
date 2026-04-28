/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui;

import static com.android.permissioncontroller.PermissionControllerStatsLog.ROLE_SETTINGS_FRAGMENT_ACTION_REPORTED;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.modules.utils.build.SdkLevel;
import com.android.permission.flags.Flags;
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.role.utils.PackageUtils;
import com.android.permissioncontroller.role.utils.RoleUiBehaviorUtils;
import com.android.permissioncontroller.role.utils.SettingsCompat;
import com.android.role.controller.model.Role;
import com.android.role.controller.model.Roles;
import com.android.settingslib.utils.applications.AppUtils;

import java.util.List;
import java.util.Objects;

/**
 * Child fragment for a default app.
 * <p>
 * Must be added as a child fragment and its parent fragment must be a
 * {@link PreferenceFragmentCompat} that implements {@link Parent}.
 *
 * @param <PF> type of the parent fragment
 */
public class DefaultAppChildFragment<PF extends PreferenceFragmentCompat
        & DefaultAppChildFragment.Parent> extends Fragment
        implements DefaultAppConfirmationDialogFragment.Listener,
        Preference.OnPreferenceClickListener {

    private static final String PREFERENCE_KEY_RECOMMENDED_CATEGORY =
            DefaultAppChildFragment.class.getName() + ".preference.RECOMMENDED_CATEGORY";
    private static final String PREFERENCE_KEY_RECOMMENDED_DESCRIPTION =
            DefaultAppChildFragment.class.getName() + ".preference.RECOMMENDED_DESCRIPTION";
    private static final String PREFERENCE_KEY_OTHERS_CATEGORY =
            DefaultAppChildFragment.class.getName() + ".preference.OTHERS_CATEGORY";
    private static final String PREFERENCE_KEY_NONE = DefaultAppChildFragment.class.getName()
            + ".preference.NONE";
    private static final String PREFERENCE_KEY_DESCRIPTION = DefaultAppChildFragment.class.getName()
            + ".preference.DESCRIPTION";
    private static final String PREFERENCE_KEY_OTHER_NFC_SERVICES =
            DefaultAppChildFragment.class.getName() + ".preference.OTHER_NFC_SERVICES";
    private static final String PREFERENCE_EXTRA_PACKAGE_NAME =
            DefaultAppChildFragment.class.getName() + ".extra.PACKAGE_NAME";
    private static final String PREFERENCE_EXTRA_UID = DefaultAppChildFragment.class.getName()
            + ".extra.UID";

    @NonNull
    private String mRoleName;
    @NonNull
    private UserHandle mUser;

    @NonNull
    private Role mRole;

    @NonNull
    private DefaultAppViewModel mViewModel;

    /**
     * Create a new instance of this fragment.
     *
     * @param roleName the name of the role for the default app
     * @param user the user for the default app
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static DefaultAppChildFragment newInstance(@NonNull String roleName,
            @NonNull UserHandle user) {
        DefaultAppChildFragment fragment = new DefaultAppChildFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_ROLE_NAME, roleName);
        arguments.putParcelable(Intent.EXTRA_USER, user);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mRoleName = arguments.getString(Intent.EXTRA_ROLE_NAME);
        mUser = arguments.getParcelable(Intent.EXTRA_USER);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        PF preferenceFragment = requirePreferenceFragment();
        Activity activity = requireActivity();
        mRole = Roles.get(activity).get(mRoleName);
        preferenceFragment.setTitle(getString(mRole.getLabelResource()));

        ViewModelProvider.Factory viewModelFactory = new DefaultAppViewModel.Factory(mRole, mUser,
                activity.getApplication());
        mViewModel = new ViewModelProvider(this, viewModelFactory).get(DefaultAppViewModel.class);
        mViewModel.getRecommendedLiveData().observe(this,
                applicationItems -> onApplicationListChanged());
        mViewModel.getLiveData().observe(this, applicationItems -> onApplicationListChanged());
        mViewModel.getManageRoleHolderStateLiveData().observe(this,
                this::onManageRoleHolderStateChanged);
    }

    private void onApplicationListChanged() {
        List<RoleApplicationItem> recommendedApplicationItems =
                mViewModel.getRecommendedLiveData().getValue();
        if (recommendedApplicationItems == null) {
            return;
        }
        List<RoleApplicationItem> otherApplicationItems = mViewModel.getLiveData().getValue();
        if (otherApplicationItems == null) {
            return;
        }

        PF preferenceFragment = requirePreferenceFragment();
        PreferenceManager preferenceManager = preferenceFragment.getPreferenceManager();
        Context context = preferenceManager.getContext();

        PreferenceScreen preferenceScreen = preferenceFragment.getPreferenceScreen();
        PreferenceCategory oldRecommendedPreferenceCategory = null;
        PreferenceCategory oldOthersPreferenceCategory = null;
        ArrayMap<String, Preference> oldPreferences = new ArrayMap<>();
        if (preferenceScreen == null) {
            preferenceScreen = preferenceManager.createPreferenceScreen(context);
            preferenceFragment.setPreferenceScreen(preferenceScreen);
        } else {
            if (Flags.defaultAppsRecommendationEnabled()) {
                oldRecommendedPreferenceCategory =
                        preferenceScreen.findPreference(PREFERENCE_KEY_RECOMMENDED_CATEGORY);
                clearPreferenceCategory(oldRecommendedPreferenceCategory, oldPreferences);
                oldOthersPreferenceCategory =
                        preferenceScreen.findPreference(PREFERENCE_KEY_OTHERS_CATEGORY);
                clearPreferenceCategory(oldOthersPreferenceCategory, oldPreferences);
            }
            clearPreferences(preferenceScreen, oldPreferences);
        }

        if (Flags.defaultAppsRecommendationEnabled() && !recommendedApplicationItems.isEmpty()) {
            addApplicationPreferenceCategory(oldRecommendedPreferenceCategory, true,
                    preferenceScreen, false, recommendedApplicationItems, oldPreferences, context);
            if (mRole.shouldShowNone() || !otherApplicationItems.isEmpty()) {
                boolean noneChecked = !(hasHolderApplication(recommendedApplicationItems)
                        || hasHolderApplication(otherApplicationItems));
                addApplicationPreferenceCategory(oldOthersPreferenceCategory, false,
                        preferenceScreen, noneChecked, otherApplicationItems, oldPreferences,
                        context);
            }
        } else {
            boolean noneChecked = !hasHolderApplication(otherApplicationItems);
            addNonePreferenceIfNeeded(preferenceScreen, noneChecked, oldPreferences, context);
            addApplicationPreferences(preferenceScreen, otherApplicationItems, oldPreferences,
                    context);
        }

        addNonPaymentNfcServicesPreference(preferenceScreen, oldPreferences, context);
        addDescriptionPreference(preferenceScreen, oldPreferences);

        preferenceFragment.onPreferenceScreenChanged();
    }

    private static void clearPreferenceCategory(@Nullable PreferenceCategory preferenceCategory,
            @NonNull ArrayMap<String, Preference> oldPreferences) {
        if (preferenceCategory == null) {
            return;
        }
        clearPreferences(preferenceCategory, oldPreferences);
        preferenceCategory.getParent().removePreference(preferenceCategory);
        preferenceCategory.setOrder(Preference.DEFAULT_ORDER);
    }

    private static void clearPreferences(@NonNull PreferenceGroup preferenceGroup,
            @NonNull ArrayMap<String, Preference> oldPreferences) {
        for (int i = preferenceGroup.getPreferenceCount() - 1; i >= 0; --i) {
            Preference preference = preferenceGroup.getPreference(i);

            preferenceGroup.removePreference(preference);
            preference.setOrder(Preference.DEFAULT_ORDER);
            oldPreferences.put(preference.getKey(), preference);
        }
    }

    private void addApplicationPreferenceCategory(
            @Nullable PreferenceCategory oldPreferenceCategory, boolean isRecommended,
            @NonNull PreferenceScreen preferenceScreen, boolean noneChecked,
            @NonNull List<RoleApplicationItem> applicationItems,
            @NonNull ArrayMap<String, Preference> oldPreferences, @NonNull Context context) {
        PreferenceCategory preferenceCategory = oldPreferenceCategory;
        if (preferenceCategory == null) {
            preferenceCategory = new PreferenceCategory(context);
            preferenceCategory.setKey(isRecommended ? PREFERENCE_KEY_RECOMMENDED_CATEGORY
                    : PREFERENCE_KEY_OTHERS_CATEGORY);
            preferenceCategory.setTitle(isRecommended
                    ? RoleUiBehaviorUtils.getRecommendedApplicationsTitle(mRole, context)
                    : getString(R.string.default_app_others));
        }
        preferenceScreen.addPreference(preferenceCategory);
        if (isRecommended) {
            addRecommendedDescriptionPreference(preferenceCategory, oldPreferences, context);
        } else {
            addNonePreferenceIfNeeded(preferenceCategory, noneChecked, oldPreferences, context);
        }
        addApplicationPreferences(preferenceCategory, applicationItems, oldPreferences, context);
    }

    private void addRecommendedDescriptionPreference(@NonNull PreferenceGroup preferenceGroup,
            @NonNull ArrayMap<String, Preference> oldPreferences, @NonNull Context context) {
        Preference preference = oldPreferences.get(PREFERENCE_KEY_RECOMMENDED_DESCRIPTION);
        if (preference == null) {
            preference = requirePreferenceFragment().createDescriptionPreference(false);
            preference.setKey(PREFERENCE_KEY_RECOMMENDED_DESCRIPTION);
            preference.setSummary(
                    RoleUiBehaviorUtils.getRecommendedApplicationsDescription(mRole, context));
        }

        preferenceGroup.addPreference(preference);
    }

    private static boolean hasHolderApplication(
            @NonNull List<RoleApplicationItem> applicationItems) {
        int applicationItemsSize = applicationItems.size();
        for (int i = 0; i < applicationItemsSize; i++) {
            RoleApplicationItem applicationItem = applicationItems.get(i);
            if (applicationItem.isHolderApplication()) {
                return true;
            }
        }
        return false;
    }

    private void addNonePreferenceIfNeeded(@NonNull PreferenceGroup preferenceGroup,
            boolean checked, @NonNull ArrayMap<String, Preference> oldPreferences,
            @NonNull Context context) {
        if (!mRole.shouldShowNone()) {
            return;
        }

        Drawable icon = AppCompatResources.getDrawable(context, R.drawable.ic_remove_circle);
        String title = getString(R.string.default_app_none);
        addApplicationPreference(preferenceGroup, PREFERENCE_KEY_NONE, icon, title, checked, null,
                oldPreferences, context);
    }

    private void addApplicationPreferences(@NonNull PreferenceGroup preferenceGroup,
            @NonNull List<RoleApplicationItem> applicationItems,
            @NonNull ArrayMap<String, Preference> oldPreferences, @NonNull Context context) {
        int applicationItemsSize = applicationItems.size();
        for (int i = 0; i < applicationItemsSize; i++) {
            RoleApplicationItem applicationItem = applicationItems.get(i);
            ApplicationInfo applicationInfo = applicationItem.getApplicationInfo();
            int userId = UserHandle.getUserHandleForUid(applicationInfo.uid).getIdentifier();
            String key = applicationInfo.packageName + "@" + userId;
            Drawable icon = Utils.getBadgedIcon(context, applicationInfo);
            String title = Utils.getFullAppLabel(applicationInfo, context);
            boolean isHolderApplication = applicationItem.isHolderApplication();

            addApplicationPreference(preferenceGroup, key, icon, title, isHolderApplication,
                    applicationInfo, oldPreferences, context);
        }
    }

    private void addApplicationPreference(@NonNull PreferenceGroup preferenceGroup,
            @NonNull String key, @NonNull Drawable icon, @NonNull CharSequence title,
            boolean checked, @Nullable ApplicationInfo applicationInfo,
            @NonNull ArrayMap<String, Preference> oldPreferences, @NonNull Context context) {
        RoleApplicationPreference roleApplicationPreference =
                (RoleApplicationPreference) oldPreferences.get(key);
        TwoStatePreference preference;
        if (roleApplicationPreference == null) {
            roleApplicationPreference = requirePreferenceFragment().createApplicationPreference();
            preference = roleApplicationPreference.asTwoStatePreference();
            preference.setKey(key);
            preference.setIcon(icon);
            preference.setTitle(title);
            preference.setPersistent(false);
            preference.setOnPreferenceChangeListener((preference2, newValue) -> false);
            preference.setOnPreferenceClickListener(this);
            // In the cases we need this (see #onPreferenceClick()), this should never be null.
            // This method (addPreference) is used for both legitimate apps and the `NONE` item,
            // the `NONE` item passes a null applicationinfo object. NFC uses a different preference
            // method for adding, and a different onclick method
            if (applicationInfo != null) {
                UserHandle user = UserHandle.getUserHandleForUid(applicationInfo.uid);
                roleApplicationPreference.setContentDescription(
                        AppUtils.getAppContentDescription(
                                context, applicationInfo.packageName, user.getIdentifier()));
                Bundle extras = preference.getExtras();
                extras.putString(PREFERENCE_EXTRA_PACKAGE_NAME, applicationInfo.packageName);
                extras.putInt(PREFERENCE_EXTRA_UID, applicationInfo.uid);
            }
        } else {
            preference = roleApplicationPreference.asTwoStatePreference();
        }

        preference.setChecked(checked);
        if (applicationInfo != null) {
            UserHandle user = UserHandle.getUserHandleForUid(applicationInfo.uid);
            roleApplicationPreference.setRestrictionIntent(
                    mRole.getApplicationRestrictionIntentAsUser(applicationInfo, user, context));
            RoleUiBehaviorUtils.prepareApplicationPreferenceAsUser(mRole, roleApplicationPreference,
                    applicationInfo, user, context);
        }

        preferenceGroup.addPreference(preference);
    }

    private void onManageRoleHolderStateChanged(int state) {
        ManageRoleHolderStateLiveData liveData = mViewModel.getManageRoleHolderStateLiveData();
        switch (state) {
            case ManageRoleHolderStateLiveData.STATE_SUCCESS:
                String packageName = liveData.getLastPackageName();
                if (packageName != null) {
                    mRole.onHolderSelectedAsUser(packageName, liveData.getLastUser(),
                            requireContext());
                }
                liveData.resetState();
                break;
            case ManageRoleHolderStateLiveData.STATE_FAILURE:
                liveData.resetState();
                break;
        }
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        String key = preference.getKey();
        if (Objects.equals(key, PREFERENCE_KEY_NONE)) {
            PermissionControllerStatsLog.write(
                    ROLE_SETTINGS_FRAGMENT_ACTION_REPORTED, Process.INVALID_UID, null, mRoleName);
            mViewModel.setNoneDefaultApp();
        } else {
            String packageName =
                    preference.getExtras().getString(PREFERENCE_EXTRA_PACKAGE_NAME);
            int uid = preference.getExtras().getInt(PREFERENCE_EXTRA_UID);
            CharSequence confirmationMessage =
                    RoleUiBehaviorUtils.getConfirmationMessage(mRole, packageName,
                            requireContext());
            if (confirmationMessage != null) {
                DefaultAppConfirmationDialogFragment.show(packageName, uid, confirmationMessage,
                        this);
            } else {
                setDefaultApp(packageName, uid);
            }
        }
        return true;
    }

    @Override
    public void setDefaultApp(@NonNull String packageName, int uid) {
        PermissionControllerStatsLog.write(
                ROLE_SETTINGS_FRAGMENT_ACTION_REPORTED, uid, packageName, mRoleName);
        mViewModel.setDefaultApp(packageName, UserHandle.getUserHandleForUid(uid));
    }

    private void addNonPaymentNfcServicesPreference(@NonNull PreferenceScreen preferenceScreen,
            @NonNull ArrayMap<String, Preference> oldPreferences, @NonNull Context context) {
        if (!(SdkLevel.isAtLeastV() && Objects.equals(mRoleName, RoleManager.ROLE_WALLET))) {
            return;
        }

        Intent intent = new Intent(SettingsCompat.ACTION_MANAGE_OTHER_NFC_SERVICES_SETTINGS);
        if (!PackageUtils.isIntentResolvedToSettings(intent, context)) {
            return;
        }

        Preference preference = oldPreferences.get(PREFERENCE_KEY_OTHER_NFC_SERVICES);
        if (preference == null) {
            preference = new Preference(context);
            preference.setKey(PREFERENCE_KEY_OTHER_NFC_SERVICES);
            preference.setIcon(AppCompatResources.getDrawable(context, R.drawable.ic_nfc));
            preference.setTitle(context.getString(
                    R.string.default_payment_app_other_nfc_services));
            preference.setPersistent(false);
            preference.setOnPreferenceClickListener(preference2 -> {
                context.startActivity(intent);
                return true;
            });
        }

        preferenceScreen.addPreference(preference);
    }

    private void addDescriptionPreference(@NonNull PreferenceScreen preferenceScreen,
            @NonNull ArrayMap<String, Preference> oldPreferences) {
        Preference preference = oldPreferences.get(PREFERENCE_KEY_DESCRIPTION);
        if (preference == null) {
            preference = requirePreferenceFragment().createDescriptionPreference(true);
            preference.setKey(PREFERENCE_KEY_DESCRIPTION);
            preference.setSummary(mRole.getDescriptionResource());
        }

        preferenceScreen.addPreference(preference);
    }

    @NonNull
    private PF requirePreferenceFragment() {
        //noinspection unchecked
        return (PF) requireParentFragment();
    }

    /**
     * Interface that the parent fragment must implement.
     */
    public interface Parent {

        /**
         * Set the title of the current settings page.
         *
         * @param title the title of the current settings page
         */
        void setTitle(@NonNull CharSequence title);

        /**
         * Create a new preference for an application.
         *
         * @return a new preference for an application
         */
        @NonNull
        RoleApplicationPreference createApplicationPreference();

        /**
         * Create a new preference for a description.
         *
         * @param isFooter whether the description is a footer
         * @return a new preference for the description
         */
        @NonNull
        Preference createDescriptionPreference(boolean isFooter);

        /**
         * Callback when changes have been made to the {@link PreferenceScreen} of the parent
         * {@link PreferenceFragmentCompat}.
         */
        void onPreferenceScreenChanged();
    }
}
