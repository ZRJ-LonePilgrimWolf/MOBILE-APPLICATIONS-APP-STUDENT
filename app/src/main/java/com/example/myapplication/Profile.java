package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Profile extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        ImageView submitButton = findViewById(R.id.change);
        if (submitButton != null) {
            submitButton.setOnClickListener(v -> {
                Intent intent = new Intent(Profile.this, changegroup.class);
                startActivity(intent);
            });
        }

        TextView editButton = findViewById(R.id.information);
        if (editButton != null) {
            editButton.setOnClickListener(v -> {
                Intent intent = new Intent(Profile.this, edit1.class);
                startActivity(intent);
            });
        }

        TextView edit1Button = findViewById(R.id.edit2);
        if (edit1Button != null) {
            edit1Button.setOnClickListener(v -> {
                Intent intent = new Intent(Profile.this, edit2.class);
                startActivity(intent);
            });
        }
    }
}
