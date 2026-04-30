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

package com.android.role.controller.behavior;

import android.content.Context;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.role.controller.model.Permissions;
import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for behavior of the system gallery role
 */
public class SystemGalleryRoleBehavior implements RoleBehavior {
    private static final List<String> STORAGE_PERMISSIONS = new ArrayList<>();
    static {
        STORAGE_PERMISSIONS.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        STORAGE_PERMISSIONS.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        STORAGE_PERMISSIONS.add(android.Manifest.permission.ACCESS_MEDIA_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            STORAGE_PERMISSIONS.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            STORAGE_PERMISSIONS.add(android.Manifest.permission.READ_MEDIA_VIDEO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            STORAGE_PERMISSIONS.add(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        }
    }

    @Override
    public void grantAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        Permissions.grantAsUser(packageName, STORAGE_PERMISSIONS,
                /* ignoreDisabledSystemPackages */ false, /* overrideUserSetAndFixed */ true,
                /* setGrantedByRole */ true, /* setGrantedByDefault */ false,
                /* setSystemFixed */ false, user, context);
    }

}
