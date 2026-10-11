package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
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

public class LecturerRosterActivity extends AppCompatActivity {
    private EditText etStudentSearch;
    private TextView tvStudentName, tvStudentNumber, tvStudentStatus, tvStudentProgram, tvStudentYear, tvStudentCourses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lecturer_roster);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        TextView tvActionNeededBadge = findViewById(R.id.tvActionNeededBadge);
        if (tvActionNeededBadge != null) {
            tvActionNeededBadge.setOnClickListener(v ->
                    startActivity(new Intent(LecturerRosterActivity.this, ActionNeededActivity.class)));
        }
        Button btnGoToActionNeeded = findViewById(R.id.btnGoToActionNeeded);
        if (btnGoToActionNeeded != null) {
            btnGoToActionNeeded.setOnClickListener(v ->
                    startActivity(new Intent(LecturerRosterActivity.this, ActionNeededActivity.class)));
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
        add.setText("+ Add student");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, 32, 32);
        ((ViewGroup) findViewById(android.R.id.content)).addView(add, lp);
        add.setOnClickListener(v -> startActivity(new Intent(this, AddStudentActivity.class)));
    }

    // REAL search: asks the server for the roster, then matches name or number.
    private void performStudentSearch() {
        String q = etStudentSearch.getText().toString().trim().toLowerCase();
        if (q.isEmpty()) {
            Toast.makeText(this, "Please enter a student name or number", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.getClient().create(ApiService.class).getStudents(Session.bearer())
                .enqueue(new Callback<StudentsResponse>() {
                    @Override
                    public void onResponse(Call<StudentsResponse> call, Response<StudentsResponse> response) {
                        if (!response.isSuccessful() || response.body() == null || response.body().students == null) {
                            Toast.makeText(LecturerRosterActivity.this,
                                    "Server said " + response.code() + " - log in again?", Toast.LENGTH_LONG).show();
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
                        Toast.makeText(LecturerRosterActivity.this,
                                "Offline - cannot search the server: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
