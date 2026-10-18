package com.example.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LecturerRosterActivity extends BaseActivity {
    private EditText etStudentSearch;
    private TextView tvStudentName, tvStudentNumber, tvStudentStatus, tvStudentProgram, tvStudentYear, tvStudentCourses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lecturer_roster);
        enableChatBubble();

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.rol);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"CHAT", "THEME", "SETTINGS", "LOGOUT"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);

            roleAutoComplete.setDropDownBackgroundDrawable(new ColorDrawable(androidx.core.content.ContextCompat.getColor(this, R.color.card_bg_color)));
            roleAutoComplete.setDropDownWidth((int) (200 * getResources().getDisplayMetrics().density));
            roleAutoComplete.setDropDownVerticalOffset((int) (20 * getResources().getDisplayMetrics().density));
            roleAutoComplete.setDropDownHeight((int) (300 * getResources().getDisplayMetrics().density));

            roleAutoComplete.setOnClickListener(v -> roleAutoComplete.showDropDown());
            roleAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    roleAutoComplete.post(() -> {
                        if (roleAutoComplete.getWindowToken() != null)
                            roleAutoComplete.showDropDown();
                    });
                }
            });

            roleAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                String selectedItem = (String) parent.getItemAtPosition(position);

                // Prevent displaying the selected text inside the AutoCompleteTextView icon
                roleAutoComplete.post(() -> roleAutoComplete.setText("", false));

                if ("CHAT".equalsIgnoreCase(selectedItem)) {
                    startActivity(new Intent(LecturerRosterActivity.this, chatbot.class));
                } else if ("THEME".equalsIgnoreCase(selectedItem)) {
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
            tvActionNeededBadge.setOnClickListener(v -> startActivity(new Intent(LecturerRosterActivity.this, ActionNeededActivity.class)));
        }
        Button btnGoToActionNeeded = findViewById(R.id.btnGoToActionNeeded);
        if (btnGoToActionNeeded != null) {
            btnGoToActionNeeded.setOnClickListener(v -> startActivity(new Intent(LecturerRosterActivity.this, ActionNeededActivity.class)));
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

        // "+ Add student" button floating at the bottom right (added in code, no XML change needed)
        Button add = new Button(this);
        add.setText("Student roster");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, 32, 32);
        ((ViewGroup) findViewById(android.R.id.content)).addView(add, lp);
        add.setOnClickListener(v -> startActivity(new Intent(this, RosterListActivity.class)));
    }

    // REAL search: asks the server for the roster, then matches name or number.
    private void performStudentSearch() {
        String q = etStudentSearch.getText().toString().trim().toLowerCase();
        if (q.isEmpty()) {
            Toast.makeText(this, "Please enter a student name or number", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.getClient().create(ApiService.class).getStudents(Session.bearer()).enqueue(new Callback<StudentsResponse>() {
            @Override
            public void onResponse(Call<StudentsResponse> call, Response<StudentsResponse> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().students == null) {
                    Toast.makeText(LecturerRosterActivity.this, "Server said " + response.code() + " - log in again?", Toast.LENGTH_LONG).show();
                    return;
                }
                for (StudentItem s : response.body().students) {
                    boolean nameHit = s.student_name != null && s.student_name.toLowerCase().contains(q);
                    boolean numHit = s.student_number != null && s.student_number.contains(q);
                    if (nameHit || numHit) {
                        tvStudentName.setText(s.student_name.toUpperCase());
                        tvStudentNumber.setText("Student number: " + s.student_number);
                        tvStudentStatus.setText("Active");
                        tvStudentProgram.setText(s.program_name);
                        tvStudentYear.setText("Group " + s.group_name);
                        tvStudentCourses.setText("");
                        return;
                    }
                }
                Toast.makeText(LecturerRosterActivity.this, "No student found", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<StudentsResponse> call, Throwable t) {
                Toast.makeText(LecturerRosterActivity.this, "Offline - cannot search the server: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
