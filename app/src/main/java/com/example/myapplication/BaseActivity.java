package com.example.myapplication;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** Every screen extends this, so every screen gets the saved mode and colour. */
public class BaseActivity extends AppCompatActivity {

    private String appliedAccent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applySavedMode(this);
        appliedAccent = ThemeHelper.getAccent(this);
        setTheme(ThemeHelper.styleFor(appliedAccent));   // MUST be before super.onCreate
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        recreateIfAccentChanged();   // e.g. coming back from the Theme screen
    }

    /** Rebuilds this screen if the colour was changed since it was built. */
    protected void recreateIfAccentChanged() {
        if (!isFinishing() && !isDestroyed()
                && !ThemeHelper.getAccent(this).equals(appliedAccent)) {
            recreate();
        }
    }
}