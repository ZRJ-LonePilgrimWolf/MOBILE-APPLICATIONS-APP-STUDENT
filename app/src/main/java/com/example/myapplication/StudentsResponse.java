package com.example.myapplication;

import java.util.List;

// GET /api/students answers { count, students: [ ... ] }
public class StudentsResponse {
    public int count;
    public List<StudentItem> students;
}
