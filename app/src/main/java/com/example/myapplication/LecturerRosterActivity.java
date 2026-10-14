package com.example.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LecturerRosterActivity extends AppCompatActivity {

    private EditText etStudentSearch;
    private TextView tvStudentName, tvStudentNumber, tvStudentStatus, tvStudentProgram, tvStudentYear, tvStudentCourses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lecturer_roster);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.rol);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"THEME", "SETTINGS", "LOGOUT"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);

            roleAutoComplete.setDropDownBackgroundDrawable(new ColorDrawable(
                    Color.parseColor("#F8FAFC")
            ));
            roleAutoComplete.setDropDownWidth(
                    (int) (200 * getResources().getDisplayMetrics().density)
            );
            roleAutoComplete.setDropDownVerticalOffset(
                    (int) (20 * getResources().getDisplayMetrics().density)
            );
            roleAutoComplete.setDropDownHeight(
                    (int) (300 * getResources().getDisplayMetrics().density)
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

                if ("THEME".equalsIgnoreCase(selectedItem)) {
                    Intent intent = new Intent(LecturerRosterActivity.this, ThemeActivity.class);
                    startActivity(intent);
                } else if ("SETTINGS".equalsIgnoreCase(selectedItem)) {
                    Intent intent = new Intent(LecturerRosterActivity.this, SettingsActivity.class);
                    startActivity(intent);
                } else if ("LOGOUT".equalsIgnoreCase(selectedItem)) {
                    Intent intent = new Intent(LecturerRosterActivity.this, lecturer_login.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
            });
        }

        // Day Schedule Buttons
        Button btnMon = findViewById(R.id.btnMon);
        Button btnTue = findViewById(R.id.btnTue);
        Button btnWed = findViewById(R.id.btnWed);
        Button btnThu = findViewById(R.id.btnThu);
        Button btnFri = findViewById(R.id.btnFri);

        if (btnMon != null) {
            btnMon.setOnClickListener(v -> Toast.makeText(this, "Monday Schedule Active", Toast.LENGTH_SHORT).show());
        }
        if (btnTue != null) {
            btnTue.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, teusday.class);
                startActivity(intent);
            });
        }
        if (btnWed != null) {
            btnWed.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, wednesday.class);
                startActivity(intent);
            });
        }
        if (btnThu != null) {
            btnThu.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, thursday.class);
                startActivity(intent);
            });
        }
        if (btnFri != null) {
            btnFri.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, friday.class);
                startActivity(intent);
            });
        }

        TextView tvActionNeededBadge = findViewById(R.id.tvActionNeededBadge);
        if (tvActionNeededBadge != null) {
            tvActionNeededBadge.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, ActionNeededActivity.class);
                startActivity(intent);
            });
        }

        Button btnGoToActionNeeded = findViewById(R.id.btnGoToActionNeeded);
        if (btnGoToActionNeeded != null) {
            btnGoToActionNeeded.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, ActionNeededActivity.class);
                startActivity(intent);
            });
        }

        etStudentSearch = findViewById(R.id.etStudentSearch);
        Button btnSearchStudent = findViewById(R.id.btnSearchStudent);
        tvStudentName = findViewById(R.id.tvStudentName);
        tvStudentNumber = findViewById(R.id.tvStudentNumber);
        tvStudentStatus = findViewById(R.id.tvStudentStatus);
        tvStudentProgram = findViewById(R.id.tvStudentProgram);
        tvStudentYear = findViewById(R.id.tvStudentYear);
        tvStudentCourses = findViewById(R.id.tvStudentCourses);

        if (btnSearchStudent != null && etStudentSearch != null) {
            btnSearchStudent.setOnClickListener(v -> performStudentSearch());
        }
    }

    private void performStudentSearch() {
        String query = etStudentSearch.getText().toString().trim().toLowerCase();
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a student name or ID", Toast.LENGTH_SHORT).show();
            return;
        }

        if (query.contains("john") || query.contains("20210001") || query.contains("phiri")) {
            tvStudentName.setText("JOHN PHIRI");
            tvStudentNumber.setText("Student ID: 20210001");
            tvStudentStatus.setText("Active");
            tvStudentProgram.setText("BSc. Computer Science");
            tvStudentYear.setText("3rd Year");
            tvStudentCourses.setText("ICT 361, ICT 351, ICT 341");
            Toast.makeText(this, "Student found: John Phiri", Toast.LENGTH_SHORT).show();
        } else if (query.contains("sarah") || query.contains("20210004") || query.contains("tembo")) {
            tvStudentName.setText("SARAH TEMBO");
            tvStudentNumber.setText("Student ID: 20210004");
            tvStudentStatus.setText("Pending Registration");
            tvStudentProgram.setText("BSc. Information Technology");
            tvStudentYear.setText("1st Year");
            tvStudentCourses.setText("Pending Registration Approval");
            Toast.makeText(this, "Student found: Sarah Tembo (Pending)", Toast.LENGTH_SHORT).show();
        } else if (query.contains("david") || query.contains("20210005") || query.contains("lungu")) {
            tvStudentName.setText("DAVID LUNGU");
            tvStudentNumber.setText("Student ID: 20210005");
            tvStudentStatus.setText("Unassigned Group");
            tvStudentProgram.setText("BSc. Computer Science");
            tvStudentYear.setText("3rd Year");
            tvStudentCourses.setText("ICT 361, ICT 351 (No Group)");
            Toast.makeText(this, "Student found: David Lungu (Unassigned)", Toast.LENGTH_SHORT).show();
        } else if (query.contains("mary") || query.contains("20210002") || query.contains("banda")) {
            tvStudentName.setText("MARY BANDA");
            tvStudentNumber.setText("Student ID: 20210002");
            tvStudentStatus.setText("Active");
            tvStudentProgram.setText("BSc. Information Technology");
            tvStudentYear.setText("2nd Year");
            tvStudentCourses.setText("ICT 241, ICT 251, ICT 261");
            Toast.makeText(this, "Student found: Mary Banda", Toast.LENGTH_SHORT).show();
        } else {
            tvStudentName.setText(query.toUpperCase() + " (FOUND)");
            tvStudentNumber.setText("Student ID: 2021" + Math.abs(query.hashCode() % 9000 + 1000));
            tvStudentStatus.setText("Active");
            tvStudentProgram.setText("BSc. Computer Science");
            tvStudentYear.setText("3rd Year");
            tvStudentCourses.setText("ICT 361, ICT 351, ICT 341");
            Toast.makeText(this, "Student details retrieved", Toast.LENGTH_SHORT).show();
        }
    }
}
