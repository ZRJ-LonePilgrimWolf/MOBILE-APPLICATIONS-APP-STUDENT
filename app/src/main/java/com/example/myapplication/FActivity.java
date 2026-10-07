package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class FActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.f);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.f_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
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