package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        LinearLayout rowChangePassword = findViewById(R.id.rowChangePassword);
        if (rowChangePassword != null) {
            rowChangePassword.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, resetpassword.class);
                startActivity(intent);
            });
        }
    }
}
