package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class changegroup extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changegroup);

        Button submitButton = findViewById(R.id.change);
        if (submitButton != null) {
            submitButton.setOnClickListener(v -> {
                Toast.makeText(this, "Group Change Request Submitted!", Toast.LENGTH_SHORT).show();
                finish();
            });
        }
    }
}
