package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class resetpassword extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resetpassword);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        Button submitButton = findViewById(R.id.change);
        if (submitButton != null) {
            submitButton.setOnClickListener(v -> {
                Toast.makeText(this, "Password reset instructions sent!", Toast.LENGTH_SHORT).show();
                finish();
            });
        }
    }
}
