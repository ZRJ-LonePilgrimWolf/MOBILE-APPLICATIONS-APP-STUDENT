package com.example.myapplication;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ActionNeededActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.action_needed);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        Button btnApproveRegistration = findViewById(R.id.btnApproveRegistration);
        Button btnRejectRegistration = findViewById(R.id.btnRejectRegistration);
        if (btnApproveRegistration != null) {
            btnApproveRegistration.setOnClickListener(v -> Toast.makeText(this, "Registration Approved for Sarah Tembo!", Toast.LENGTH_SHORT).show());
        }
        if (btnRejectRegistration != null) {
            btnRejectRegistration.setOnClickListener(v -> Toast.makeText(this, "Registration Request Rejected", Toast.LENGTH_SHORT).show());
        }

        AutoCompleteTextView actvGroupSelect = findViewById(R.id.actvGroupSelect);
        if (actvGroupSelect != null) {
            String[] groups = new String[]{"Group A - Mobile Apps", "Group B - Web Systems", "Group C - Database Systems"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, groups);
            actvGroupSelect.setAdapter(adapter);
            actvGroupSelect.setOnClickListener(v -> actvGroupSelect.showDropDown());
            actvGroupSelect.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    actvGroupSelect.showDropDown();
                }
            });
        }

        Button btnAssignGroup = findViewById(R.id.btnAssignGroup);
        if (btnAssignGroup != null) {
            btnAssignGroup.setOnClickListener(v -> {
                String selected = actvGroupSelect != null ? actvGroupSelect.getText().toString() : "";
                if (selected.isEmpty()) {
                    Toast.makeText(this, "Please select a group first", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "David Lungu assigned to " + selected, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
