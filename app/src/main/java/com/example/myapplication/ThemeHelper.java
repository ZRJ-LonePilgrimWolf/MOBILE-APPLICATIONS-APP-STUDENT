package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** One place that stores and reads the light/dark mode and the theme colour. */
public final class ThemeHelper {

    public static final String PREFS = "theme_prefs";      // same file your code already uses
    public static final String KEY_MODE = "night_mode";    // same key your code already uses
    public static final String KEY_ACCENT = "accent";

    public static final String NAVY = "navy";
    public static final String BLUE = "blue";
    public static final String PURPLE = "purple";
    public static final String TEAL = "teal";

    private ThemeHelper() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getMode(Context c) {
        return prefs(c).getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void saveMode(Context c, int mode) {
        prefs(c).edit().putInt(KEY_MODE, mode).apply();
    }

    public static String getAccent(Context c) {
        return prefs(c).getString(KEY_ACCENT, NAVY);
    }

    public static void saveAccent(Context c, String accent) {
        prefs(c).edit().putString(KEY_ACCENT, accent).apply();
    }

    /** Applies the saved light/dark mode only if it differs (avoids needless rebuilds). */
    public static void applySavedMode(Context c) {
        int mode = getMode(c);
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    /** Which style in themes.xml belongs to a colour name. */
    public static int styleFor(String accent) {
        switch (accent) {
            case BLUE:   return R.style.Theme_MyApplication_Blue;
            case PURPLE: return R.style.Theme_MyApplication_Purple;
            case TEAL:   return R.style.Theme_MyApplication_Teal;
            case NAVY:
            default:     return R.style.Theme_MyApplication_Navy;
        }
    }
}