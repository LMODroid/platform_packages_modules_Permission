/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui.handheld;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.settingslib.widget.FooterPreference;

/**
 * Preference used as a description.
 * <p>
 * This preference does not set an {@link androidx.preference.Preference#setOrder(int) order} for
 * itself like {@link FooterPreference} does, and uses the default (insertion) order instead.
 */
public class HandheldDescriptionPreference extends FooterPreference {

    public HandheldDescriptionPreference(@NonNull Context context, boolean isFooter) {
        super(context);

        setOrder(DEFAULT_ORDER);
        setIconVisibility(isFooter ? View.VISIBLE : View.GONE);
    }
}
