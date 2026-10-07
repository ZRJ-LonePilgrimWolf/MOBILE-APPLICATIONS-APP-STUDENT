package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LecturerRosterActivity extends AppCompatActivity {

    private EditText etStudentSearch;
    private Button btnSearchStudent;
    private TextView tvStudentName, tvStudentNumber, tvStudentStatus, tvStudentProgram, tvStudentYear, tvStudentCourses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.lecturer_roster);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.roster_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        etStudentSearch = findViewById(R.id.etStudentSearch);
        btnSearchStudent = findViewById(R.id.btnSearchStudent);
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
        } else if (query.contains("mary") || query.contains("20210002") || query.contains("banda")) {
            tvStudentName.setText("MARY BANDA");
            tvStudentNumber.setText("Student ID: 20210002");
            tvStudentStatus.setText("Active");
            tvStudentProgram.setText("BSc. Information Technology");
            tvStudentYear.setText("2nd Year");
            tvStudentCourses.setText("ICT 241, ICT 251, ICT 261");
            Toast.makeText(this, "Student found: Mary Banda", Toast.LENGTH_SHORT).show();
        } else if (query.contains("peter") || query.contains("20210003") || query.contains("mulenga")) {
            tvStudentName.setText("PETER MULENGA");
            tvStudentNumber.setText("Student ID: 20210003");
            tvStudentStatus.setText("Active");
            tvStudentProgram.setText("BSc. Software Engineering");
            tvStudentYear.setText("4th Year");
            tvStudentCourses.setText("ICT 461, ICT 451, ICT 441");
            Toast.makeText(this, "Student found: Peter Mulenga", Toast.LENGTH_SHORT).show();
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
