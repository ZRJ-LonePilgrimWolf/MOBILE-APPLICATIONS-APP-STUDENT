package com.example.myapplication.repository;

import android.app.Application;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.StudentDao;
import com.example.myapplication.database.StudentEntity;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StudentRepository {

    private final StudentDao studentDao;
    private final ApiService apiService;
    private final ExecutorService executorService;

    public StudentRepository(Application application) {

        AppDatabase database = AppDatabase.getInstance(application);

        studentDao = database.studentDao();
        apiService = ApiClient.getApiService();

        executorService = Executors.newSingleThreadExecutor();
    }

    public void insertStudent(StudentEntity student) {
        executorService.execute(() -> {
            studentDao.insertStudent(student);
        });

        apiService.createStudent(student).enqueue(new Callback<StudentEntity>() {
            @Override
            public void onResponse(Call<StudentEntity> call, Response<StudentEntity> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executorService.execute(() -> studentDao.insertStudent(response.body()));
                }
            }

            @Override
            public void onFailure(Call<StudentEntity> call, Throwable t) {
                // Kept in local Room database for offline sync
            }
        });
    }

    public void updateStudent(StudentEntity student) {
        executorService.execute(() -> {
            studentDao.updateStudent(student);
        });

        apiService.updateStudent(student.getStudentId(), student).enqueue(new Callback<StudentEntity>() {
            @Override
            public void onResponse(Call<StudentEntity> call, Response<StudentEntity> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executorService.execute(() -> studentDao.updateStudent(response.body()));
                }
            }

            @Override
            public void onFailure(Call<StudentEntity> call, Throwable t) {
                // Kept in local Room database for offline sync
            }
        });
    }

    public void deleteStudent(StudentEntity student) {
        executorService.execute(() -> {
            studentDao.deleteStudent(student);
        });

        apiService.deleteStudent(student.getStudentId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                // Deleted on server successfully
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Handled offline
            }
        });
    }

    public void getAllStudents(RepositoryCallback<List<StudentEntity>> callback) {
        executorService.execute(() -> {
            List<StudentEntity> students = studentDao.getAllStudents();
            callback.onResult(students);
        });

        apiService.getAllStudents().enqueue(new Callback<List<StudentEntity>>() {
            @Override
            public void onResponse(Call<List<StudentEntity>> call, Response<List<StudentEntity>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<StudentEntity> remoteStudents = response.body();
                    executorService.execute(() -> {
                        for (StudentEntity s : remoteStudents) {
                            studentDao.insertStudent(s);
                        }
                        callback.onResult(studentDao.getAllStudents());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<StudentEntity>> call, Throwable t) {
                // Fallback to local Room database results
            }
        });
    }

    public void getStudentById(
            String studentId,
            RepositoryCallback<StudentEntity> callback) {

        executorService.execute(() -> {
            StudentEntity student = studentDao.getStudentById(studentId);
            callback.onResult(student);
        });

        apiService.getStudentById(studentId).enqueue(new Callback<StudentEntity>() {
            @Override
            public void onResponse(Call<StudentEntity> call, Response<StudentEntity> response) {
                if (response.isSuccessful() && response.body() != null) {
                    StudentEntity remoteStudent = response.body();
                    executorService.execute(() -> {
                        studentDao.insertStudent(remoteStudent);
                        callback.onResult(remoteStudent);
                    });
                }
            }

            @Override
            public void onFailure(Call<StudentEntity> call, Throwable t) {
                // Fallback to local Room database result
            }
        });
    }

    public interface RepositoryCallback<T> {
        void onResult(T result);
    }
}
