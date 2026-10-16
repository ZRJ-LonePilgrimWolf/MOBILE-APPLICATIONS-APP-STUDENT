package com.example.myapplication;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.LinkedHashMap;
import java.util.Map;

public class ThemeActivity extends BaseActivity {

    private RadioButton rbLightMode, rbDarkMode, rbSystemMode;
    private final Map<String, Button> colourButtons = new LinkedHashMap<>();
    private final Map<String, String> colourNames = new LinkedHashMap<>();
    private String pendingAccent;   // colour tapped but not applied yet

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        // ---------- light / dark / system ----------
        rbLightMode = findViewById(R.id.rbLightMode);
        rbDarkMode = findViewById(R.id.rbDarkMode);
        rbSystemMode = findViewById(R.id.rbSystemMode);

        rbLightMode.setOnClickListener(v -> checkOnly(rbLightMode));
        rbDarkMode.setOnClickListener(v -> checkOnly(rbDarkMode));
        rbSystemMode.setOnClickListener(v -> checkOnly(rbSystemMode));

        int savedMode = ThemeHelper.getMode(this);
        if (savedMode == AppCompatDelegate.MODE_NIGHT_YES) checkOnly(rbDarkMode);
        else if (savedMode == AppCompatDelegate.MODE_NIGHT_NO) checkOnly(rbLightMode);
        else checkOnly(rbSystemMode);

        // ---------- the 4 colours ----------
        addColour(ThemeHelper.NAVY, R.id.btnColorNavy, "Navy");
        addColour(ThemeHelper.BLUE, R.id.btnColorBlue, "Blue");
        addColour(ThemeHelper.PURPLE, R.id.btnColorPurple, "Purple");
        addColour(ThemeHelper.TEAL, R.id.btnColorTeal, "Teal");

        pendingAccent = ThemeHelper.getAccent(this);
        showSelectedColour();

        // ---------- apply ----------
        Button btnApplyTheme = findViewById(R.id.btnApplyTheme);
        btnApplyTheme.setOnClickListener(v -> applyChanges());
    }

    private void checkOnly(RadioButton selected) {
        rbLightMode.setChecked(selected == rbLightMode);
        rbDarkMode.setChecked(selected == rbDarkMode);
        rbSystemMode.setChecked(selected == rbSystemMode);
    }

    private void addColour(String key, int buttonId, String label) {
        Button b = findViewById(buttonId);
        if (b == null) return;
        colourButtons.put(key, b);
        colourNames.put(key, label);
        b.setOnClickListener(v -> {
            pendingAccent = key;
            showSelectedColour();
        });
    }

    /** Puts a tick and an outline on the chosen colour so the user sees the choice. */
    private void showSelectedColour() {
        int outline = ContextCompat.getColor(this, R.color.form_text_color);
        int widthPx = (int) (3 * getResources().getDisplayMetrics().density);
        for (Map.Entry<String, Button> e : colourButtons.entrySet()) {
            boolean chosen = e.getKey().equals(pendingAccent);
            Button b = e.getValue();
            String label = colourNames.get(e.getKey());
            b.setText(chosen ? "✓ " + label : label);
            b.setContentDescription(label + (chosen ? ", selected" : ""));
            if (b instanceof MaterialButton) {
                MaterialButton mb = (MaterialButton) b;
                mb.setStrokeColor(ColorStateList.valueOf(outline));
                mb.setStrokeWidth(chosen ? widthPx : 0);
            }
        }
    }

    private void applyChanges() {
        int mode;
        if (rbDarkMode.isChecked()) mode = AppCompatDelegate.MODE_NIGHT_YES;
        else if (rbLightMode.isChecked()) mode = AppCompatDelegate.MODE_NIGHT_NO;
        else mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

        boolean modeChanged = AppCompatDelegate.getDefaultNightMode() != mode;
        boolean accentChanged = !pendingAccent.equals(ThemeHelper.getAccent(this));

        // 1. Save first, so any screen rebuilt below reads the new values.
        ThemeHelper.saveMode(this, mode);
        ThemeHelper.saveAccent(this, pendingAccent);

        if (!modeChanged && !accentChanged) {
            Toast.makeText(this, "No changes to apply", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Theme applied", Toast.LENGTH_SHORT).show();

        // 2. Light/dark changed: AppCompat rebuilds every open screen right away.
        if (modeChanged) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
        // 3. Colour changed: rebuild this screen now; the others rebuild themselves
        //    in onResume (BaseActivity) when you go back to them.
        if (accentChanged) {
            getWindow().getDecorView().post(this::recreateIfAccentChanged);
        }
    }
}