/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.policy.impl;

import com.android.internal.R;
import com.android.internal.widget.DigitalClock;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.RotarySelector;
import com.android.internal.widget.SlidingTab;
import com.android.internal.widget.WaveView;
import com.android.internal.widget.multiwaveview.MultiWaveView;
import com.android.internal.widget.multiwaveview.TargetDrawable;
import com.android.internal.widget.multiwaveview.GlowPadView;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;

import android.content.ActivityNotFoundException;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;

import android.content.res.ColorStateList;

import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.*;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout.LayoutParams;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.media.AudioManager;
import android.provider.MediaStore;
import android.provider.Settings;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * The screen within {@link LockPatternKeyguardView} that shows general
 * information about the device depending on its state, and how to get
 * past it, as applicable.
 */
class LockScreen extends LinearLayout implements KeyguardScreen {

    private static final int ON_RESUME_PING_DELAY = 500; // delay first ping until the screen is on
    private static final boolean DBG = false;
    private static final String TAG = "LockScreen";
    private static final String ENABLE_MENU_KEY_FILE = "/data/local/enable_menu_key";
    private static final int WAIT_FOR_ANIMATION_TIMEOUT = 0;
    private static final int WAIT_FOR_ANIMATION_TIMEOUT_ALT = 500;
    private static final int STAY_ON_WHILE_GRABBED_TIMEOUT = 30000;

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private LockPatternUtils mLockPatternUtils;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private KeyguardScreenCallback mCallback;

    // current configuration state of keyboard and display
    private int mKeyboardHidden;
    private int mCreationOrientation;

    private boolean mSilentMode;
    private AudioManager mAudioManager;
    private boolean mEnableMenuKeyInLockScreen;

    private KeyguardStatusViewManager mStatusViewManager;
    private UnlockWidgetCommonMethods mUnlockWidgetMethods;
    //private View mUnlockWidget;

    private boolean mCameraDisabled;
    private boolean mSearchDisabled;
    // Is there a vibrator
    private final boolean mHasVibrator;

    private TextView mCarrier;
    private View mUnlockWidget;
    
    // Get the style from settings
    private int mLockscreenStyle = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_STYLE, LOCK_STYLE_JB);
//    private int mLockscreenStyle = LOCK_STYLE_GB;
    
    private static final int LOCK_STYLE_JB = 0;    
    private static final int LOCK_STYLE_ICS = 1;
    private static final int LOCK_STYLE_GB = 2;
    private static final int LOCK_STYLE_ECLAIR = 3;
    private static final int LOCK_STYLE_BB = 4;
    
    private boolean mUseJbLockscreen = (mLockscreenStyle == LOCK_STYLE_JB);
    private boolean mUseIcsLockscreen = (mLockscreenStyle == LOCK_STYLE_ICS);
    private boolean mUseGbLockscreen = (mLockscreenStyle == LOCK_STYLE_GB);
    private boolean mUseEclairLockscreen = (mLockscreenStyle == LOCK_STYLE_ECLAIR);
    private boolean mUseBbLockscreen = (mLockscreenStyle == LOCK_STYLE_BB);

    private DigitalClock mDigitalClock;
    InfoCallbackImpl mInfoCallback = new InfoCallbackImpl() {

        @Override
        public void onRingerModeChanged(int state) {
            boolean silent = AudioManager.RINGER_MODE_NORMAL != state;
            if (silent != mSilentMode) {
                mSilentMode = silent;
                mUnlockWidgetMethods.updateResources();
            }
        }

        @Override
        public void onDevicePolicyManagerStateChanged() {
            updateTargets();
        }

    };

    SimStateCallback mSimStateCallback = new SimStateCallback() {
        public void onSimStateChanged(State simState) {
            updateTargets();
        }
    };

    private interface UnlockWidgetCommonMethods {
        // Update resources based on phone state
        public void updateResources();

        // Get the view associated with this widget
        public View getView();

        // Reset the view
        public void reset(boolean animate);

        // Animate the widget if it supports ping()
        public void ping();
        // Enable or disable a target. ResourceId is the id of the *drawable* associated with the
        // target.
        public void setEnabled(int resourceId, boolean enabled);

        // Get the target position for the given resource. Returns -1 if not found.
        public int getTargetPosition(int resourceId);

        // Clean up when this widget is going away
        public void cleanUp();
    }

    class RotarySelMethods implements RotarySelector.OnDialTriggerListener,
            UnlockWidgetCommonMethods {
        private final RotarySelector mRotarySel;
        
        RotarySelMethods(RotarySelector rotarySel) {
            mRotarySel = rotarySel;
        }
        
        /** {@inheritDoc} */
        public void onDialTrigger(View v, int whichHandle) {
            if (whichHandle == RotarySelector.OnDialTriggerListener.LEFT_HANDLE) {
                mCallback.goToUnlockScreen();
            } else if (whichHandle == RotarySelector.OnDialTriggerListener.RIGHT_HANDLE) {
                toggleRingMode();
                updateResources();
                mCallback.pokeWakelock();
            }
        }
        
        public void onGrabbedStateChange(View v, int grabbedState) { }
        
        public View getView() {
            return mRotarySel;
        }
        
        public void updateResources() {
            boolean vibe = mSilentMode
                && (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);
        
            int iconId = mSilentMode ? (vibe ? R.drawable.ic_jog_dial_vibrate_on
                    : R.drawable.ic_jog_dial_sound_off) : R.drawable.ic_jog_dial_sound_on;
        
            mRotarySel.setRightHandleResource(iconId);
        }
        
        public void reset(boolean animate) { }
        
        public void ping() { }

        public void setEnabled(int resourceId, boolean enabled) {
            // Not used
        }

        public int getTargetPosition(int resourceId) {
            return -1; // Not supported
        }

        public void cleanUp() {
            mRotarySel.setOnDialTriggerListener(null);
        }
        
    }
    
    class SlidingTabMethods implements SlidingTab.OnTriggerListener, UnlockWidgetCommonMethods {
        private final SlidingTab mSlidingTab;

        SlidingTabMethods(SlidingTab slidingTab) {
            mSlidingTab = slidingTab;
        }

        public void updateResources() {
            boolean vibe = mSilentMode
                && (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);

            mSlidingTab.setRightTabResources(
                    mSilentMode ? ( vibe ? R.drawable.ic_jog_dial_vibrate_on
                                         : R.drawable.ic_jog_dial_sound_off )
                                : R.drawable.ic_jog_dial_sound_on,
                    mSilentMode ? R.drawable.jog_tab_target_yellow
                                : R.drawable.jog_tab_target_gray,
                    mSilentMode ? R.drawable.jog_tab_bar_right_sound_on
                                : R.drawable.jog_tab_bar_right_sound_off,
                    mSilentMode ? R.drawable.jog_tab_right_sound_on
                                : R.drawable.jog_tab_right_sound_off);
        }

        /** {@inheritDoc} */
        public void onTrigger(View v, int whichHandle) {
            if (whichHandle == SlidingTab.OnTriggerListener.LEFT_HANDLE) {
                mCallback.goToUnlockScreen();
            } else if (whichHandle == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
                toggleRingMode();
                updateResources();
                mCallback.pokeWakelock();
            }
        }

        /** {@inheritDoc} */
        public void onGrabbedStateChange(View v, int grabbedState) {
            if (grabbedState == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
                mSilentMode = isSilentMode();
                mSlidingTab.setRightHintText(mSilentMode ? R.string.lockscreen_sound_on_label
                        : R.string.lockscreen_sound_off_label);
            }
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (grabbedState != SlidingTab.OnTriggerListener.NO_HANDLE) {
                mCallback.pokeWakelock();
            }
        }

        public View getView() {
            return mSlidingTab;
        }

        public void reset(boolean animate) {
            mSlidingTab.reset(animate);
        }

        public void ping() {
        }
        
		public void setEnabled(int resourceId, boolean enabled) {
            // Not used
        }

        public int getTargetPosition(int resourceId) {
            return -1; // Not supported
        }

        public void cleanUp() {
            mSlidingTab.setOnTriggerListener(null);
        }
    }

    class WaveViewMethods implements WaveView.OnTriggerListener, UnlockWidgetCommonMethods {

        private final WaveView mWaveView;

        WaveViewMethods(WaveView waveView) {
            mWaveView = waveView;
        }
        /** {@inheritDoc} */
        public void onTrigger(View v, int whichHandle) {
            if (whichHandle == WaveView.OnTriggerListener.CENTER_HANDLE) {
                requestUnlockScreen();
            }
        }

        /** {@inheritDoc} */
        public void onGrabbedStateChange(View v, int grabbedState) {
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (grabbedState == WaveView.OnTriggerListener.CENTER_HANDLE) {
                mCallback.pokeWakelock(STAY_ON_WHILE_GRABBED_TIMEOUT);
            }
        }

        public void updateResources() {
        }

        public View getView() {
            return mWaveView;
        }
        public void reset(boolean animate) {
            mWaveView.reset();
        }
        public void ping() {
        }
        public void setEnabled(int resourceId, boolean enabled) {
            // Not used
        }
        public int getTargetPosition(int resourceId) {
            return -1; // Not supported
        }
        public void cleanUp() {
            mWaveView.setOnTriggerListener(null);
        }
    }

    class MultiWaveViewMethods implements MultiWaveView.OnTriggerListener,
            UnlockWidgetCommonMethods {

        private final MultiWaveView mMultiWaveView;
        private boolean mCameraDisabled;
        
        MultiWaveViewMethods(MultiWaveView multiWaveView) {
            mMultiWaveView = multiWaveView;
            final boolean cameraDisabled = mLockPatternUtils.getDevicePolicyManager()
                    .getCameraDisabled(null);
            if (cameraDisabled) {
                Log.v(TAG, "Camera disabled by Device Policy");
                mCameraDisabled = true;
            } else {
                // Camera is enabled if resource is initially defined for MultiWaveView
                // in the lockscreen layout file
                mCameraDisabled = mMultiWaveView.getTargetResourceId()
                        != R.array.lockscreen_targets_with_camera;
            }
        }
        
        public void updateResources() {
            int resId;
            if (mCameraDisabled) {
                // Fall back to showing ring/silence if camera is disabled by DPM...
                resId = mSilentMode ? R.array.lockscreen_targets_when_silent
                    : R.array.lockscreen_targets_when_soundon;
            } else {
                resId = R.array.lockscreen_targets_with_camera;
            }
            mMultiWaveView.setTargetResources(resId);
        }
        
        public void onGrabbed(View v, int handle) {
        
        }
        
        public void onReleased(View v, int handle) {
        
        }
        
        public void onTrigger(View v, int target) {
            if (target == 0 || target == 1) { // 0 = unlock/portrait, 1 = unlock/landscape
                mCallback.goToUnlockScreen();
            } else if (target == 2 || target == 3) { // 2 = alt/portrait, 3 = alt/landscape
                if (!mCameraDisabled) {
                    // Start the Camera
                    Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                    mCallback.goToUnlockScreen();
                } else {
                    toggleRingMode();
                    mUnlockWidgetMethods.updateResources();
                    mCallback.pokeWakelock();
                }
            }
        }
        
        public void onGrabbedStateChange(View v, int handle) {
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (handle != MultiWaveView.OnTriggerListener.NO_HANDLE) {
                mCallback.pokeWakelock();
            }
        }
        
        public View getView() {
            return mMultiWaveView;
        }
        
        public void reset(boolean animate) {
            mMultiWaveView.reset(animate);
        }
        
        public void ping() {
            mMultiWaveView.ping();
        }

        @Override
        public void setEnabled(int resourceId, boolean enabled) {
            mMultiWaveView.setEnableTarget(resourceId, enabled);
        }
        @Override
        public int getTargetPosition(int resourceId) { 
            return mMultiWaveView.getTargetPosition(resourceId);
        }
        @Override
        public void cleanUp() {
            mMultiWaveView.setOnTriggerListener(null);
        }
        @Override
        public void onFinishFinalAnimation() { }
    }

    class GlowPadViewMethods implements GlowPadView.OnTriggerListener,
            UnlockWidgetCommonMethods {
        private final GlowPadView mGlowPadView;

        GlowPadViewMethods(GlowPadView glowPadView) {
            mGlowPadView = glowPadView;
        }

        public boolean isTargetPresent(int resId) {
            return mGlowPadView.getTargetPosition(resId) != -1;
        }

        private StateListDrawable getLayeredDrawable(Drawable back, Drawable front, int inset, boolean frontBlank) {
            Resources res = getResources();
            InsetDrawable[] inactivelayer = new InsetDrawable[2];
            InsetDrawable[] activelayer = new InsetDrawable[2];
            inactivelayer[0] = new InsetDrawable(res.getDrawable(com.android.internal.R.drawable.ic_lockscreen_lock_pressed), 0, 0, 0, 0);
            inactivelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);
            activelayer[0] = new InsetDrawable(back, 0, 0, 0, 0);
            activelayer[1] = new InsetDrawable(frontBlank ? res.getDrawable(android.R.color.transparent) : front, inset, inset, inset, inset);
            StateListDrawable states = new StateListDrawable();
            LayerDrawable inactiveLayerDrawable = new LayerDrawable(inactivelayer);
            inactiveLayerDrawable.setId(0, 0);
            inactiveLayerDrawable.setId(1, 1);
            LayerDrawable activeLayerDrawable = new LayerDrawable(activelayer);
            activeLayerDrawable.setId(0, 0);
            activeLayerDrawable.setId(1, 1);
            states.addState(TargetDrawable.STATE_INACTIVE, inactiveLayerDrawable);
            states.addState(TargetDrawable.STATE_ACTIVE, activeLayerDrawable);
            return states;
        }

        public boolean isTargetPresent(int resId) {
            return mGlowPadView.getTargetPosition(resId) != -1;
        }
        public void updateResources() {
            int resId;
            if (mCameraDisabled && mEnableRingSilenceFallback) {
                // Fall back to showing ring/silence if camera is disabled...
                resId = mSilentMode ? R.array.lockscreen_targets_when_silent
                    : R.array.lockscreen_targets_when_soundon;
            } else {
                resId = R.array.lockscreen_targets_with_camera;
            }
            if (mGlowPadView.getTargetResourceId() != resId) {
                mGlowPadView.setTargetResources(resId);
            }

            // Update the search icon with drawable from the search .apk
            if (!mSearchDisabled) {
                SearchManager searchManager = getSearchManager();
                if (searchManager != null) {
                    ComponentName component = searchManager.getGlobalSearchActivity();
                    if (component != null) {
                        if (!mGlowPadView.replaceTargetDrawablesIfPresent(component,
                                ASSIST_ICON_METADATA_NAME,
                                com.android.internal.R.drawable.ic_lockscreen_search)) {
                            Slog.w(TAG, "Couldn't grab icon from package " + component);
                        }
                    } else {
                        storedDraw.add(new TargetDrawable(res, 0));
                    }
                }
                mGlowPadView.setTargetResources(storedDraw);
            }
        }

        public void onGrabbed(View v, int handle) {

        }

        public void onReleased(View v, int handle) {

        }

        public void onTrigger(View v, int target) {
            final int resId = mGlowPadView.getResourceIdForTarget(target);
            switch (resId) {
                case com.android.internal.R.drawable.ic_lockscreen_search:
                    Intent assistIntent = getAssistIntent();
                    if (assistIntent != null) {
                        launchActivity(assistIntent);
                    } else {
                        Log.w(TAG, "Failed to get intent for assist activity");
                    }
                    mCallback.pokeWakelock();
                    break;

                case com.android.internal.R.drawable.ic_lockscreen_camera:
                    launchActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                    mCallback.pokeWakelock();
                    break;

                case com.android.internal.R.drawable.ic_lockscreen_silent:
                    toggleRingMode();

                    mCallback.pokeWakelock();

                    break;

                case com.android.internal.R.drawable.ic_lockscreen_unlock_phantom:
                case com.android.internal.R.drawable.ic_lockscreen_unlock:
                    mCallback.goToUnlockScreen();
                    break;
                }
            } else {
                final boolean isLand = mCreationOrientation == Configuration.ORIENTATION_LANDSCAPE;
                if ((target == 0 && (mIsScreenLarge || !isLand)) || (target == 2 && !mIsScreenLarge && isLand)) {
                    mCallback.goToUnlockScreen();
                } else {
                    target -= 1 + mTargetOffset;
                    if (target < mStoredTargets.length && mStoredTargets[target] != null) {
                        try {
                            Intent tIntent = Intent.parseUri(mStoredTargets[target], 0);
                            tIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            mContext.startActivity(tIntent);
                            mCallback.goToUnlockScreen();
                            return;
                        } catch (URISyntaxException e) {
                        } catch (ActivityNotFoundException e) {
                        }
                    }
                }
            }
        }

        public void onGrabbedStateChange(View v, int handle) {
            // Don't poke the wake lock when returning to a state where the handle is
            // not grabbed since that can happen when the system (instead of the user)
            // cancels the grab.
            if (handle != GlowPadView.OnTriggerListener.NO_HANDLE) {
                mCallback.pokeWakelock();
            }
        }

        public View getView() {
            return mGlowPadView;
        }

        public void reset(boolean animate) {
            mGlowPadView.reset(animate);
        }

        public void ping() {
            mGlowPadView.ping();
        }

        public void setEnabled(int resourceId, boolean enabled) {
            mGlowPadView.setEnableTarget(resourceId, enabled);
        }

        public int getTargetPosition(int resourceId) {
            return mGlowPadView.getTargetPosition(resourceId);
        }

        public void cleanUp() {
            mGlowPadView.setOnTriggerListener(null);
        }

        public void onFinishFinalAnimation() {

        }
    }

    private void requestUnlockScreen() {
        // Delay hiding lock screen long enough for animation to finish
        postDelayed(new Runnable() {
            public void run() {
                mCallback.goToUnlockScreen();
            }
        }, mUseBbLockscreen ? WAIT_FOR_ANIMATION_TIMEOUT_ALT
                : WAIT_FOR_ANIMATION_TIMEOUT);
    }

    private void toggleRingMode() {
        // toggle silent mode
        mSilentMode = !mSilentMode;
        if (mSilentMode) {
            final boolean vibe = (Settings.System.getInt(
                mContext.getContentResolver(),
                Settings.System.VIBRATE_IN_SILENT, 1) == 1);

            mAudioManager.setRingerMode(vibe
                ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_SILENT);
        } else {
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
    }

    /**
     * In general, we enable unlocking the insecure key guard with the menu key. However, there are
     * some cases where we wish to disable it, notably when the menu button placement or technology
     * is prone to false positives.
     *
     * @return true if the menu key should be enabled
     */
    private boolean shouldEnableMenuKey() {
        final Resources res = getResources();
        final boolean configDisabled = res.getBoolean(R.bool.config_disableMenuKeyInLockScreen);
        final boolean isTestHarness = ActivityManager.isRunningInTestHarness();
        final boolean fileOverride = (new File(ENABLE_MENU_KEY_FILE)).exists();
        final boolean menuOverride = Settings.System.getInt(getContext().getContentResolver(), Settings.System.MENU_UNLOCK_SCREEN, 0) == 1;
        return !configDisabled || isTestHarness || fileOverride || menuOverride;
    }

    /**
     * @param context Used to setup the view.
     * @param configuration The current configuration. Used to use when selecting layout, etc.
     * @param lockPatternUtils Used to know the state of the lock pattern settings.
     * @param updateMonitor Used to register for updates on various keyguard related
     *    state, and query the initial state at setup.
     * @param callback UWaveViewsed to communicate back to the host keyguard view.
     */
    LockScreen(Context context, Configuration configuration, LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor updateMonitor,
            KeyguardScreenCallback callback) {
        super(context);
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;

        mEnableMenuKeyInLockScreen = shouldEnableMenuKey();

        mCreationOrientation = configuration.orientation;
	boolean mTabletui = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.MODE_TABLET_UI, 0) == 1;

        mKeyboardHidden = configuration.hardKeyboardHidden;

        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** CREATING LOCK SCREEN", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + " res orient=" + context.getResources().getConfiguration().orientation);
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        if (DBG) Log.v(TAG, "Creation orientation = " + mCreationOrientation);
        if (mCreationOrientation != Configuration.ORIENTATION_LANDSCAPE) {
	    if (mUseJbLockscreen || mTabletui) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_jb, this, true);
	    } else if (mUseIcsLockscreen) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_ics, this, true);
	    } else if (mUseGbLockscreen) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_gb, this, true);
	    } else if (mUseEclairLockscreen) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_eclair, this, true);
	    } else if (mUseBbLockscreen) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_bb, this, true);
	    } else {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_jb, this, true);
	    }
        } else {
            if (mUseJbLockscreen || mTabletui) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_land_jb, this, true);
	    } else if (mUseIcsLockscreen) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_land_ics, this, true);
	    } else if (mUseGbLockscreen) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_land_gb, this, true);
	    } else if (mUseEclairLockscreen) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_land_eclair, this, true);
	    } else {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_jb, this, true);
	    }
        }
	if (!mUseBbLockscreen) {
        setBackground(mContext, (ViewGroup) findViewById(R.id.root));
	}

        mStatusViewManager = new KeyguardStatusViewManager(this, mUpdateMonitor, mLockPatternUtils,
                mCallback, false);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        mCarrier = (TextView) findViewById(R.id.carrier);
        
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = vibrator == null ? false : vibrator.hasVibrator();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mSilentMode = isSilentMode();
        mUnlockWidget = findViewById(R.id.unlock_widget);
        mUnlockWidgetMethods = createUnlockMethods(mUnlockWidget);

        if (DBG) Log.v(TAG, "*** LockScreen accel is "
                + (mUnlockWidget.isHardwareAccelerated() ? "on":"off"));
    }

    private UnlockWidgetCommonMethods createUnlockMethods(View unlockWidget) {
        if (unlockWidget instanceof RotarySelector) {
            RotarySelector rotarySelView = (RotarySelector) mUnlockWidget;
            rotarySelView.setLeftHandleResource(R.drawable.ic_jog_dial_unlock);
            RotarySelMethods rotarySelMethods = new RotarySelMethods(rotarySelView);
            rotarySelView.setOnDialTriggerListener(rotarySelMethods);
            return rotarySelMethods;
         } else if (unlockWidget instanceof SlidingTab) {
            SlidingTab slidingTabView = (SlidingTab) unlockWidget;
            slidingTabView.setHoldAfterTrigger(true, false);
            slidingTabView.setLeftHintText(R.string.lockscreen_unlock_label);
            slidingTabView.setLeftTabResources(
                    R.drawable.ic_jog_dial_unlock,
                    R.drawable.jog_tab_target_green,
                    R.drawable.jog_tab_bar_left_unlock,
                    R.drawable.jog_tab_left_unlock);
            SlidingTabMethods slidingTabMethods = new SlidingTabMethods(slidingTabView);
            slidingTabView.setOnTriggerListener(slidingTabMethods);
            mUnlockWidgetMethods = slidingTabMethods;
        } else if (mUnlockWidget instanceof WaveView) {
            WaveView waveView = (WaveView) mUnlockWidget;
            WaveViewMethods waveViewMethods = new WaveViewMethods(waveView);
            waveView.setOnTriggerListener(waveViewMethods);
//            mUnlockWidgetMethods = waveViewMethods;
            return waveViewMethods;
        } else if (unlockWidget instanceof MultiWaveView) {
            MultiWaveView multiWaveView = (MultiWaveView) mUnlockWidget;
            MultiWaveViewMethods multiWaveViewMethods = new MultiWaveViewMethods(multiWaveView);
            multiWaveView.setOnTriggerListener(multiWaveViewMethods);
            return multiWaveViewMethods;
        } else if (unlockWidget instanceof GlowPadView) {
            GlowPadView glowPadView = (GlowPadView) unlockWidget;
            GlowPadViewMethods glowPadViewMethods = new GlowPadViewMethods(glowPadView);
            glowPadView.setOnTriggerListener(glowPadViewMethods);
            return glowPadViewMethods;
        } else {
            throw new IllegalStateException("Unrecognized unlock widget: " + mUnlockWidget);
        }

    private void updateTargets() {
        boolean disabledByAdmin = mLockPatternUtils.getDevicePolicyManager()
                .getCameraDisabled(null);
        boolean disabledBySimState = mUpdateMonitor.isSimLocked();
        boolean cameraPresent = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);

        boolean searchTargetPresent = (mUnlockWidgetMethods instanceof GlowPadViewMethods)
                ? ((GlowPadViewMethods) mUnlockWidgetMethods)
                        .isTargetPresent(com.android.internal.R.drawable.ic_lockscreen_search)
                        : false;

        if (disabledByAdmin) {
            Log.v(TAG, "Camera disabled by Device Policy");
        } else if (disabledBySimState) {
            Log.v(TAG, "Camera disabled by Sim State");
        }
        boolean searchActionAvailable = isAssistantAvailable();
        mCameraDisabled = disabledByAdmin || disabledBySimState || !cameraTargetPresent;
        mSearchDisabled = disabledBySimState || !searchActionAvailable || !searchTargetPresent;
        mUnlockWidgetMethods.updateResources();

        if (DBG) Log.v(TAG, "*** LockScreen accel is "
                + (mUnlockWidget.isHardwareAccelerated() ? "on":"off"));
    }
	
    static void setBackground(Context context, ViewGroup layout) {
        String lockBack = Settings.System.getString(context.getContentResolver(), Settings.System.LOCKSCREEN_BACKGROUND);
        if (lockBack != null) {
            if (!lockBack.isEmpty()) {
                try {
                    layout.setBackgroundColor(Integer.parseInt(lockBack));
                } catch(NumberFormatException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    layout.getParent();
                    ViewParent parent =  layout.getParent();
                    if (parent != null) {
                        //change parent to show background correctly on scale
                        RelativeLayout rlout = new RelativeLayout(context);
                        ((ViewGroup) parent).removeView(layout);
                        layout.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        ((ViewGroup) parent).addView(rlout); // change parent to new layout
                        rlout.addView(layout);
                        // create framelayout and add imageview to set background
                        FrameLayout flayout = new FrameLayout(context);
                        flayout.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        ImageView mLockScreenWallpaperImage = new ImageView(flayout.getContext());
                        mLockScreenWallpaperImage.setScaleType(ScaleType.CENTER_CROP);
                        flayout.addView(mLockScreenWallpaperImage, -1, -1);
                        Context settingsContext = context.createPackageContext("com.android.settings", 0);
                        String wallpaperFile = settingsContext.getFilesDir() + "/lockwallpaper";
                        Bitmap background = BitmapFactory.decodeFile(wallpaperFile);
                        Drawable d = new BitmapDrawable(context.getResources(), background);
                        mLockScreenWallpaperImage.setImageDrawable(d);
                        // add background to lock screen.
                        rlout.addView(flayout,0);
                    }
                } catch (NameNotFoundException e) {
                }
            }
        }
    }

    private boolean isSilentMode() {
        return mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && mEnableMenuKeyInLockScreen) {
            mCallback.goToUnlockScreen();
        }
        return false;
    }

    void updateConfiguration() {
        Configuration newConfig = getResources().getConfiguration();
        if (newConfig.orientation != mCreationOrientation) {
            mCallback.recreateMe(newConfig);
        } else if (newConfig.hardKeyboardHidden != mKeyboardHidden) {
            mKeyboardHidden = newConfig.hardKeyboardHidden;
            final boolean isKeyboardOpen = mKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
            if (mUpdateMonitor.isKeyguardBypassEnabled() && isKeyboardOpen) {
                mCallback.goToUnlockScreen();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** LOCK ATTACHED TO WINDOW");
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + getResources().getConfiguration());
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.w(TAG, "***** LOCK CONFIG CHANGING", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + newConfig);
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    public boolean needsInput() {
        return false;
    }

    /** {@inheritDoc} */
    public void onPause() {
        mStatusViewManager.onPause();
        mUnlockWidgetMethods.reset(false);
    }

    private final Runnable mOnResumePing = new Runnable() {
        public void run() {
            mUnlockWidgetMethods.ping();
        }
    };

    /** {@inheritDoc} */
    public void onResume() {
        mStatusViewManager.onResume();
        postDelayed(mOnResumePing, ON_RESUME_PING_DELAY);
    }

    /** {@inheritDoc} */
    public void cleanUp() {
        mUpdateMonitor.removeCallback(this); // this must be first
        mLockPatternUtils = null;
        mUpdateMonitor = null;
        mCallback = null;
    }

    /** {@inheritDoc} */
    public void onRingerModeChanged(int state) {
        boolean silent = AudioManager.RINGER_MODE_NORMAL != state;
        if (silent != mSilentMode) {
            mSilentMode = silent;
            mUnlockWidgetMethods.updateResources();
        }
    }

    public void onPhoneStateChanged(String newState) {
    }
}

