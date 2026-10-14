package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class search extends AppCompatActivity {

    private EditText etStudentSearch;
    private TextView tvStudentName, tvStudentNumber, tvStudentProgram, tvStudentYear, tvStudentCourses, chatButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        etStudentSearch = findViewById(R.id.etStudentSearch);
        Button btnSearchStudent = findViewById(R.id.btnSearchStudent);
        tvStudentName = findViewById(R.id.tvStudentName);
        tvStudentNumber = findViewById(R.id.tvStudentNumber);
        tvStudentProgram = findViewById(R.id.tvStudentProgram);
        tvStudentYear = findViewById(R.id.tvStudentYear);
        tvStudentCourses = findViewById(R.id.tvStudentCourses);
        chatButton = findViewById(R.id.chat);

        if (btnSearchStudent != null && etStudentSearch != null) {
            btnSearchStudent.setOnClickListener(v -> performStudentSearch());
        }

        if (chatButton != null) {
            chatButton.setOnClickListener(v -> {
                Toast.makeText(this, "Opening direct chat...", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void performStudentSearch() {
        String query = etStudentSearch.getText().toString().trim().toLowerCase();
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a student name or ID", Toast.LENGTH_SHORT).show();
            return;
        }

        if (query.contains("john") || query.contains("20210001") || query.contains("phiri")) {
            if (tvStudentName != null) tvStudentName.setText("JOHN PHIRI");
            if (tvStudentNumber != null) tvStudentNumber.setText("Student ID: 20210001");
            if (tvStudentProgram != null) tvStudentProgram.setText("BSc. Computer Science");
            if (tvStudentYear != null) tvStudentYear.setText("3rd Year");
            if (tvStudentCourses != null) tvStudentCourses.setText("ICT 361, ICT 351, ICT 341");
            Toast.makeText(this, "Student found: John Phiri", Toast.LENGTH_SHORT).show();
        } else if (query.contains("sarah") || query.contains("20210004") || query.contains("tembo")) {
            if (tvStudentName != null) tvStudentName.setText("SARAH TEMBO");
            if (tvStudentNumber != null) tvStudentNumber.setText("Student ID: 20210004");
            if (tvStudentProgram != null) tvStudentProgram.setText("BSc. Information Technology");
            if (tvStudentYear != null) tvStudentYear.setText("1st Year");
            if (tvStudentCourses != null) tvStudentCourses.setText("Pending Registration Approval");
            Toast.makeText(this, "Student found: Sarah Tembo", Toast.LENGTH_SHORT).show();
        } else if (query.contains("mary") || query.contains("20210002") || query.contains("banda")) {
            if (tvStudentName != null) tvStudentName.setText("MARY BANDA");
            if (tvStudentNumber != null) tvStudentNumber.setText("Student ID: 20210002");
            if (tvStudentProgram != null) tvStudentProgram.setText("BSc. Information Technology");
            if (tvStudentYear != null) tvStudentYear.setText("2nd Year");
            if (tvStudentCourses != null) tvStudentCourses.setText("ICT 241, ICT 251, ICT 261");
            Toast.makeText(this, "Student found: Mary Banda", Toast.LENGTH_SHORT).show();
        } else {
            if (tvStudentName != null) tvStudentName.setText(query.toUpperCase() + " (FOUND)");
            if (tvStudentNumber != null) tvStudentNumber.setText("Student ID: 2021" + Math.abs(query.hashCode() % 9000 + 1000));
            if (tvStudentProgram != null) tvStudentProgram.setText("BSc. Computer Science");
            if (tvStudentYear != null) tvStudentYear.setText("3rd Year");
            if (tvStudentCourses != null) tvStudentCourses.setText("ICT 361, ICT 351, ICT 341");
            Toast.makeText(this, "Student details retrieved", Toast.LENGTH_SHORT).show();
        }
    }
}
