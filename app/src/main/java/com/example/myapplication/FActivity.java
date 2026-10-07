package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class FActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.f);
        ImageView reg = findViewById(R.id.profil);
        reg.setOnClickListener(v -> {
            Intent intent = new Intent(FActivity.this, Profile.class);
            startActivity(intent);
        });
        ImageView search = findViewById(R.id.search);
        search.setOnClickListener(v -> {
            Intent intent = new Intent(FActivity.this, search.class);
            startActivity(intent);
        });
    }
}