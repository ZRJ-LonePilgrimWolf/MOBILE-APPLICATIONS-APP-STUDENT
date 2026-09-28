package com.example.myapplication.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.myapplication.database.StudentEntity;
import com.example.myapplication.repository.StudentRepository;

import java.util.List;

public class StudentViewModel extends AndroidViewModel {

    private final StudentRepository repository;

    private final MutableLiveData<List<StudentEntity>> students =
            new MutableLiveData<>();

    private final MutableLiveData<StudentEntity> selectedStudent =
            new MutableLiveData<>();

    public StudentViewModel(Application application) {
        super(application);
        repository = new StudentRepository(application);
    }

    public LiveData<List<StudentEntity>> getStudents() {
        return students;
    }

    public LiveData<StudentEntity> getSelectedStudent() {
        return selectedStudent;
    }

    public void loadStudents() {
        repository.getAllStudents(result -> {
            students.postValue(result);
        });
    }

    public void loadStudentById(String studentId) {
        repository.getStudentById(studentId, result -> {
            selectedStudent.postValue(result);
        });
    }

    public void insertStudent(StudentEntity student) {
        repository.insertStudent(student);

        repository.getAllStudents(result -> {
            students.postValue(result);
        });
    }

    public void updateStudent(StudentEntity student) {
        repository.updateStudent(student);

        repository.getAllStudents(result -> {
            students.postValue(result);
        });
    }

    public void deleteStudent(StudentEntity student) {
        repository.deleteStudent(student);

        repository.getAllStudents(result -> {
            students.postValue(result);
        });
    }
}
