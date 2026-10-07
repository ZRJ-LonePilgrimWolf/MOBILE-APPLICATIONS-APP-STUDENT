package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class lecturer_login extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lecturer_login);

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.Roll);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"Student", "Lecturer"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);
            roleAutoComplete.setText("Lecturer", false);
            roleAutoComplete.setOnClickListener(v -> roleAutoComplete.showDropDown());
            roleAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    roleAutoComplete.showDropDown();
                }
            });
            roleAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                String selectedRole = (String) parent.getItemAtPosition(position);
                if ("Student".equalsIgnoreCase(selectedRole)) {
                    Intent intent = new Intent(lecturer_login.this, MainActivity.class);
                    startActivity(intent);
                }
            });
        }

        Button loginButton = findViewById(R.id.lec_button);
        if (loginButton != null) {
            loginButton.setOnClickListener(v -> {
                Intent intent = new Intent(lecturer_login.this, LecturerRosterActivity.class);
                startActivity(intent);
            });
        }

        TextView reg = findViewById(R.id.Lec_buttonPanel);
        if (reg != null) {
            reg.setOnClickListener(v -> {
                Intent intent = new Intent(lecturer_login.this, lecture_registration.class);
                startActivity(intent);
            });
        }
    }
}
