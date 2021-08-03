/*
 * Copyright (C) 2014-2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
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
package com.raven.lair.tabs;

import static android.os.UserHandle.USER_SYSTEM;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.View;

import android.content.ContentResolver;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.custom.preference.SecureSettingListPreference;
import com.android.settings.custom.preference.SystemSettingListPreference;
import com.android.settings.SettingsPreferenceFragment;

import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;

import com.android.internal.util.custom.cutout.CutoutUtils;
import com.android.internal.util.custom.ThemesUtils;

import java.util.Set;

public class Statusbar extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String CATEGORY_BRIGHTNESS = "status_bar_brightness_category";

    private static final String BRIGHTNESS_SLIDER_STYLE = "brightness_slider_style";

    private static final String ICON_BLACKLIST = "icon_blacklist";

    private static final String STATUS_BAR_QUICK_QS_PULLDOWN = "qs_quick_pulldown";
    private static final String STATUS_BAR_QUICK_QS_SHOW_BRIGHTNESS_SLIDER = "qqs_show_brightness_slider";
    private static final String STATUS_BAR_QUICK_QS_SHOW_AUTO_BRIGHTNESS = "qs_show_auto_brightness";

    private static final int STATUS_BAR_QS_BRIGHTNESS_NEVER_SHOW = 0;

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;

    private SystemSettingListPreference mQuickPulldown;

    private SecureSettingListPreference mStatusBarQsShowBrightnessSlider;
    private SwitchPreference mStatusBarQsShowAutoBrightness;

    private IOverlayManager mOverlayManager;
    private IOverlayManager mOverlayService;
    
    private PreferenceCategory mStatusBarBrightnessCategory;

    private static boolean sHasCenteredNotch;

   private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";
    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String QS_BATTERY_PERCENTAGE = "qs_battery_percentage";

    private ListPreference mBatteryPercent;
    private ListPreference mBatteryStyle;
    private ListPreference mBrightnessSliderStyle;
    private SwitchPreference mQsBatteryPercent;

    private int mBatteryPercentValue;

    private static final int BATTERY_STYLE_PORTRAIT = 0;
    private static final int BATTERY_STYLE_TEXT = 4;
    private static final int BATTERY_STYLE_HIDDEN = 5;
    private static final int BATTERY_PERCENT_HIDDEN = 0;
    //private static final int BATTERY_PERCENT_SHOW_INSIDE = 1;
    //private static final int BATTERY_PERCENT_SHOW_OUTSIDE = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.statusbar);
        
	mOverlayService = IOverlayManager.Stub
                .asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE));
		
        int batterystyle = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY_STYLE, BATTERY_STYLE_PORTRAIT, UserHandle.USER_CURRENT);
        mBatteryStyle = (ListPreference) findPreference("status_bar_battery_style");
        mBatteryStyle.setValue(String.valueOf(batterystyle));
        mBatteryStyle.setSummary(mBatteryStyle.getEntry());
        mBatteryStyle.setOnPreferenceChangeListener(this);

        mBatteryPercentValue = Settings.System.getIntForUser(getContentResolver(),
	Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, BATTERY_PERCENT_HIDDEN, UserHandle.USER_CURRENT);
        mBatteryPercent = (ListPreference) findPreference("status_bar_show_battery_percent");
        mBatteryPercent.setValue(String.valueOf(mBatteryPercentValue));
        mBatteryPercent.setSummary(mBatteryPercent.getEntry());
        mBatteryPercent.setOnPreferenceChangeListener(this);

	 mBatteryPercent.setEnabled(
                batterystyle != BATTERY_STYLE_TEXT && batterystyle != BATTERY_STYLE_HIDDEN);

        mQsBatteryPercent = (SwitchPreference) findPreference(QS_BATTERY_PERCENTAGE);
        mQsBatteryPercent.setChecked((Settings.System.getInt(
                getActivity().getApplicationContext().getContentResolver(),
                Settings.System.QS_SHOW_BATTERY_PERCENT, 0) == 1));
        mQsBatteryPercent.setOnPreferenceChangeListener(this);

        sHasCenteredNotch = CutoutUtils.hasCenteredCutout(getActivity());

        mQuickPulldown = findPreference(STATUS_BAR_QUICK_QS_PULLDOWN);
        mQuickPulldown.setOnPreferenceChangeListener(this);
        updateQuickPulldownSummary(mQuickPulldown.getIntValue(0));

        mStatusBarBrightnessCategory = getPreferenceScreen().findPreference(CATEGORY_BRIGHTNESS);
        mStatusBarQsShowBrightnessSlider = mStatusBarBrightnessCategory.findPreference(STATUS_BAR_QUICK_QS_SHOW_BRIGHTNESS_SLIDER);
        mStatusBarQsShowBrightnessSlider.setOnPreferenceChangeListener(this);
        mStatusBarQsShowAutoBrightness = mStatusBarBrightnessCategory.findPreference(STATUS_BAR_QUICK_QS_SHOW_AUTO_BRIGHTNESS);
        getBrightnessSliderPref();
        if (!getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available)){
            mStatusBarBrightnessCategory.removePreference(mStatusBarQsShowAutoBrightness);
            mStatusBarQsShowAutoBrightness = null;
        }
        enableStatusBarQsBrightnessDependents(mStatusBarQsShowBrightnessSlider.getIntValue(1));
    }

   public void handleOverlays(String packagename, Boolean state, IOverlayManager mOverlayManager) {
        try {
            mOverlayService.setEnabled(packagename, state, USER_SYSTEM);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
            mQuickPulldown.setEntries(R.array.status_bar_quick_qs_pulldown_entries_rtl);
            mQuickPulldown.setEntryValues(R.array.status_bar_quick_qs_pulldown_values);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

       ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mBatteryStyle) {
            int batterystyle = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_STYLE, batterystyle,
                UserHandle.USER_CURRENT);
            int index = mBatteryStyle.findIndexOfValue((String) newValue);
            mBatteryStyle.setSummary(mBatteryStyle.getEntries()[index]);
            mBatteryPercent.setEnabled(
                    batterystyle != BATTERY_STYLE_TEXT && batterystyle != BATTERY_STYLE_HIDDEN);
            return true;
        } else if (preference == mBatteryPercent) {
            mBatteryPercentValue = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(resolver,
                    Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, mBatteryPercentValue,
                    UserHandle.USER_CURRENT);
            int index = mBatteryPercent.findIndexOfValue((String) newValue);
            mBatteryPercent.setSummary(mBatteryPercent.getEntries()[index]);
            return true;
        } else if (preference == mQsBatteryPercent) {
            Settings.System.putInt(resolver,
                    Settings.System.QS_SHOW_BATTERY_PERCENT,
                    (Boolean) newValue ? 1 : 0);
            return true;
        }
      else if (preference == mBrightnessSliderStyle) {
            String brightness_style = (String) newValue;
            final Context context = getContext();
            switch (brightness_style) {
                case "1":
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_DANIEL);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEMINII);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUND);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUNDSTROKE);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMESTROKE);
                   break;
                case "2":
                    handleOverlays(true, context, ThemesUtils.BRIGHTNESS_SLIDER_DANIEL);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEMINII);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUND);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUNDSTROKE);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMESTROKE);
                   break;
                case "3":
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_DANIEL);
                    handleOverlays(true, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEMINII);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUND);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUNDSTROKE);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMESTROKE);
                   break;
                case "4":
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_DANIEL);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEMINII);
                    handleOverlays(true, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUND);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUNDSTROKE);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMESTROKE);
                   break;
                case "5":
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_DANIEL);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEMINII);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUND);
                    handleOverlays(true, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUNDSTROKE);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMESTROKE);
                   break;
                case "6":
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_DANIEL);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEMINII);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUND);
                    handleOverlays(false, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMEROUNDSTROKE);
                    handleOverlays(true, context, ThemesUtils.BRIGHTNESS_SLIDER_MEMESTROKE);
                   break;
            }
            return true;
        }

       int value = Integer.parseInt((String) newValue);
        String key = preference.getKey();
        switch (key) {
            case STATUS_BAR_QUICK_QS_PULLDOWN:
                updateQuickPulldownSummary(value);
                break;
            case STATUS_BAR_QUICK_QS_SHOW_BRIGHTNESS_SLIDER:
                enableStatusBarQsBrightnessDependents(value);
                break;
        }
        return true;
    }

    private void enableStatusBarQsBrightnessDependents(int qsBrightnessType) {
        if (mStatusBarQsShowAutoBrightness != null){
            mStatusBarQsShowAutoBrightness.setEnabled(qsBrightnessType != STATUS_BAR_QS_BRIGHTNESS_NEVER_SHOW);
        }
    }

   private void getBrightnessSliderPref() {
        mBrightnessSliderStyle = (ListPreference) findPreference(BRIGHTNESS_SLIDER_STYLE);
        mBrightnessSliderStyle.setOnPreferenceChangeListener(this);
        if (ThemesUtils.isThemeEnabled("com.android.systemui.brightness.slider.memestroke")) {
            mBrightnessSliderStyle.setValue("6");
        } else if (ThemesUtils.isThemeEnabled("com.android.systemui.brightness.slider.memeroundstroke")) {
            mBrightnessSliderStyle.setValue("5");
        } else if (ThemesUtils.isThemeEnabled("com.android.systemui.brightness.slider.memeround")) {
            mBrightnessSliderStyle.setValue("4");
        } else if (ThemesUtils.isThemeEnabled("com.android.systemui.brightness.slider.mememini")) {
            mBrightnessSliderStyle.setValue("3");
        } else if (ThemesUtils.isThemeEnabled("com.android.systemui.brightness.slider.daniel")) {
            mBrightnessSliderStyle.setValue("2");
        } else {
            mBrightnessSliderStyle.setValue("1");
        }
    }

   private void handleOverlays(Boolean state, Context context, String[] overlays) {
        if (context == null) {
            return;
        }
        for (int i = 0; i < overlays.length; i++) {
            String xui = overlays[i];
            try {
                mOverlayService.setEnabled(xui, state, USER_SYSTEM);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateQuickPulldownSummary(int value) {
        String summary="";
        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL){
            if (value == PULLDOWN_DIR_LEFT) {
                value = PULLDOWN_DIR_RIGHT;
            }else if (value == PULLDOWN_DIR_RIGHT) {
                value = PULLDOWN_DIR_LEFT;
            }
        }
        switch (value) {
            case PULLDOWN_DIR_NONE:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_off);
                break;
            case PULLDOWN_DIR_LEFT:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_summary_left_edge);
                break;
            case PULLDOWN_DIR_RIGHT:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_summary_right_edge);
                break;
        }
        mQuickPulldown.setSummary(summary);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CUSTOM_SETTINGS;
    }
}
