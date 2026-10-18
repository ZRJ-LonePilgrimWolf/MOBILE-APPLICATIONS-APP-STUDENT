package com.example.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class wednesday extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wednesday);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.rol);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"THEME", "SETTINGS", "LOGOUT"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);

            roleAutoComplete.setDropDownBackgroundDrawable(new ColorDrawable(androidx.core.content.ContextCompat.getColor(this, R.color.card_bg_color)));
            roleAutoComplete.setDropDownWidth((int) (200 * getResources().getDisplayMetrics().density));
            roleAutoComplete.setDropDownVerticalOffset((int) (20 * getResources().getDisplayMetrics().density));
            roleAutoComplete.setDropDownHeight((int) (300 * getResources().getDisplayMetrics().density));

            roleAutoComplete.setOnClickListener(v -> roleAutoComplete.showDropDown());
            roleAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    roleAutoComplete.post(() -> { if (roleAutoComplete.getWindowToken() != null) roleAutoComplete.showDropDown(); });
                }
            });

            roleAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                String selectedItem = (String) parent.getItemAtPosition(position);
                roleAutoComplete.post(() -> roleAutoComplete.setText("", false));

                if ("THEME".equalsIgnoreCase(selectedItem)) {
                    Intent intent = new Intent(wednesday.this, ThemeActivity.class);
                    startActivity(intent);
                } else if ("SETTINGS".equalsIgnoreCase(selectedItem)) {
                    Intent intent = new Intent(wednesday.this, SettingsActivity.class);
                    startActivity(intent);
                } else if ("LOGOUT".equalsIgnoreCase(selectedItem)) {
                    logout();
                }
            });
        }
    }
}
