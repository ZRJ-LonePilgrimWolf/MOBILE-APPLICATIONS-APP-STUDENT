package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class edit2 extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit2);

        Button saveButton = findViewById(R.id.editing);
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                Toast.makeText(this, "Personal details updated!", Toast.LENGTH_SHORT).show();
                finish();
            });
        }
    }
}
