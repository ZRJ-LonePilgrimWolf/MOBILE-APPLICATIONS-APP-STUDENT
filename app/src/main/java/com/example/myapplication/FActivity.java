package com.example.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class FActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.f);

        ImageView reg = findViewById(R.id.profil);
        if (reg != null) {
            reg.setOnClickListener(v -> {
                Intent intent = new Intent(FActivity.this, Profile.class);
                startActivity(intent);
            });
        }

        ImageView search = findViewById(R.id.search);
        if (search != null) {
            search.setOnClickListener(v -> {
                Intent intent = new Intent(FActivity.this, search.class);
                startActivity(intent);
            });
        }

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.rol);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"THEME", "PROFILE", "MY COURSES", "SETTINGS", "LOGOUT"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);

            roleAutoComplete.setDropDownBackgroundDrawable(new ColorDrawable(
                    Color.parseColor("#1E293B")
            ));
            roleAutoComplete.setDropDownWidth(
                    (int) (200 * getResources().getDisplayMetrics().density)
            );
            roleAutoComplete.setDropDownVerticalOffset(
                    (int) (20 * getResources().getDisplayMetrics().density)
            );
            roleAutoComplete.setDropDownHeight(
                    (int) (400 * getResources().getDisplayMetrics().density)
            );

            roleAutoComplete.setOnClickListener(v -> roleAutoComplete.showDropDown());
            roleAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    roleAutoComplete.showDropDown();
                }
            });

            roleAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                String selectedItem = (String) parent.getItemAtPosition(position);

                // Prevent displaying the selected text inside the AutoCompleteTextView icon
                roleAutoComplete.post(() -> roleAutoComplete.setText("", false));

                if ("PROFILE".equalsIgnoreCase(selectedItem)) {
                    Intent intent = new Intent(FActivity.this, Profile.class);
                    startActivity(intent);
                } else if ("LOGOUT".equalsIgnoreCase(selectedItem)) {
                    Intent intent = new Intent(FActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
            });
        }
    }
}
