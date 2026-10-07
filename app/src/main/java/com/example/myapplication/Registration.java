package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Registration extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration);

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.Roll);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"Student", "Lecturer"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);
            roleAutoComplete.setOnClickListener(v -> roleAutoComplete.showDropDown());
            roleAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    roleAutoComplete.showDropDown();
                }
            });
            roleAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                String selectedRole = (String) parent.getItemAtPosition(position);
                if ("Lecturer".equalsIgnoreCase(selectedRole)) {
                    Intent intent = new Intent(Registration.this, lecture_registration.class);
                    startActivity(intent);
                }
            });
        }

        AutoCompleteTextView programAutoComplete = findViewById(R.id.dropdown_menu);
        if (programAutoComplete != null) {
            String[] programs = new String[]{
                "BSc. Computer Science",
                "BSc. Information Technology",
                "BSc. Software Engineering",
                "BSc. Data Science",
                "BSc. Information Systems"
            };
            ArrayAdapter<String> programAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, programs);
            programAutoComplete.setAdapter(programAdapter);
            programAutoComplete.setOnClickListener(v -> programAutoComplete.showDropDown());
            programAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    programAutoComplete.showDropDown();
                }
            });
        }

        Button loginButton = findViewById(R.id.button);
        loginButton.setOnClickListener(v -> {
            Intent intent = new Intent(Registration.this, FActivity.class);
            startActivity(intent);
        });

        TextView reg = findViewById(R.id.butt);
        reg.setOnClickListener(v -> {
            Intent intent = new Intent(Registration.this, MainActivity.class);
            startActivity(intent);
        });
    }
}
