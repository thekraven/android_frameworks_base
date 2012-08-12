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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import com.android.internal.telephony.Phone;
import android.util.Log;

import com.android.systemui.R;

public class NetworkModeController implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.NetworkModeController";

    private Context mContext;
    private CompoundButton mCheckBox;

    public static final String ACTION_MODIFY_NETWORK_MODE = "com.android.internal.telephony.MODIFY_NETWORK_MODE";
    public static final String EXTRA_NETWORK_MODE = "networkMode";

    private int mNetworkMode;
    private boolean mState = false;

    public NetworkModeController(Context context, CompoundButton checkbox) {
        mContext = context;
        mNetworkMode = getNetworkMode();
        mState = networkModeToState(mNetworkMode);
        mCheckBox = checkbox;
        checkbox.setChecked(mState);
        checkbox.setOnCheckedChangeListener(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        int networkType = checked ? Phone.NT_MODE_WCDMA_PREF : Phone.NT_MODE_GSM_ONLY;

        Intent intent = new Intent(ACTION_MODIFY_NETWORK_MODE);
        intent.putExtra(EXTRA_NETWORK_MODE, networkType);
        mContext.sendBroadcast(intent);
    }

    private int getNetworkMode() {
        int mode = 99;
        try {
            mode = Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.PREFERRED_NETWORK_MODE);
        } catch (Exception e) {}
        return mode;
    }

    private static boolean networkModeToState(int mode) {
        switch(mode) {
            case Phone.NT_MODE_WCDMA_PREF:
            case Phone.NT_MODE_WCDMA_ONLY:
            case Phone.NT_MODE_GSM_UMTS:
                return true;
            case Phone.NT_MODE_GSM_ONLY:
                return false;
            case Phone.NT_MODE_CDMA:
            case Phone.NT_MODE_CDMA_NO_EVDO:
            case Phone.NT_MODE_EVDO_NO_CDMA:
            case Phone.NT_MODE_GLOBAL:
                // need to check wtf is going on
                Log.d(TAG, "Unexpected network mode (" + mode + ")");
        }

        return false;
    }
}
