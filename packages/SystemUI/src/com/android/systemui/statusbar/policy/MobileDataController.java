/*
 * Copyright (C) 2011 The CyanogenMod Project
 * This code has been modified. Portions copyright (C) 2012 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.net.ConnectivityManager;

import com.android.systemui.R;

public class MobileDataController implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.MobileDataController";

    private Context mContext;
    private CompoundButton mCheckBox;

    private boolean mMobileData;

    public MobileDataController(Context context, CompoundButton checkbox) {
        mContext = context;
        mMobileData = getMobileData();
        mCheckBox = checkbox;
        checkbox.setChecked(mMobileData);
        checkbox.setOnCheckedChangeListener(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.setMobileDataEnabled(checked);
    }

    private boolean getMobileData() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getMobileDataEnabled();
    }

}
