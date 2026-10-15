package com.example.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme);

        sharedPreferences = getSharedPreferences("theme_prefs", MODE_PRIVATE);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        RadioButton rbLightMode = findViewById(R.id.rbLightMode);
        RadioButton rbDarkMode = findViewById(R.id.rbDarkMode);
        RadioButton rbSystemMode = findViewById(R.id.rbSystemMode);

        if (rbLightMode != null && rbDarkMode != null && rbSystemMode != null) {
            rbLightMode.setOnClickListener(v -> {
                rbLightMode.setChecked(true);
                rbDarkMode.setChecked(false);
                rbSystemMode.setChecked(false);
            });
            rbDarkMode.setOnClickListener(v -> {
                rbLightMode.setChecked(false);
                rbDarkMode.setChecked(true);
                rbSystemMode.setChecked(false);
            });
            rbSystemMode.setOnClickListener(v -> {
                rbLightMode.setChecked(false);
                rbDarkMode.setChecked(false);
                rbSystemMode.setChecked(true);
            });
        }

        int savedMode = sharedPreferences.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (savedMode == AppCompatDelegate.MODE_NIGHT_YES) {
            if (rbDarkMode != null) {
                rbDarkMode.setChecked(true);
                if (rbLightMode != null) rbLightMode.setChecked(false);
                if (rbSystemMode != null) rbSystemMode.setChecked(false);
            }
        } else if (savedMode == AppCompatDelegate.MODE_NIGHT_NO) {
            if (rbLightMode != null) {
                rbLightMode.setChecked(true);
                if (rbDarkMode != null) rbDarkMode.setChecked(false);
                if (rbSystemMode != null) rbSystemMode.setChecked(false);
            }
        } else {
            if (rbSystemMode != null) {
                rbSystemMode.setChecked(true);
                if (rbLightMode != null) rbLightMode.setChecked(false);
                if (rbDarkMode != null) rbDarkMode.setChecked(false);
            }
        }

        Button btnApplyTheme = findViewById(R.id.btnApplyTheme);
        if (btnApplyTheme != null) {
            btnApplyTheme.setOnClickListener(v -> {
                int mode;

                if (rbDarkMode != null && rbDarkMode.isChecked()) {
                    mode = AppCompatDelegate.MODE_NIGHT_YES;
                    Toast.makeText(this, "Dark Theme Applied", Toast.LENGTH_SHORT).show();
                } else if (rbLightMode != null && rbLightMode.isChecked()) {
                    mode = AppCompatDelegate.MODE_NIGHT_NO;
                    Toast.makeText(this, "Light Theme Applied", Toast.LENGTH_SHORT).show();
                } else {
                    mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    Toast.makeText(this, "System Theme Applied", Toast.LENGTH_SHORT).show();
                }

                sharedPreferences.edit().putInt("night_mode", mode).apply();
                if (AppCompatDelegate.getDefaultNightMode() != mode) {
                    AppCompatDelegate.setDefaultNightMode(mode);
                }
            });
        }
    }
}
