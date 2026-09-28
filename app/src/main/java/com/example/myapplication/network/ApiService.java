package com.example.myapplication.network;

import com.example.myapplication.database.StudentEntity;
import com.example.myapplication.model.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @POST("login")
    Call<User> login(
            @Query("username") String username,
            @Query("password") String password
    );

    @GET("students")
    Call<List<StudentEntity>> getAllStudents();

    @GET("students/{studentId}")
    Call<StudentEntity> getStudentById(
            @Path("studentId") String studentId
    );

    @POST("students")
    Call<StudentEntity> createStudent(
            @Body StudentEntity student
    );

    @PUT("students/{studentId}")
    Call<StudentEntity> updateStudent(
            @Path("studentId") String studentId,
            @Body StudentEntity student
    );

    @DELETE("students/{studentId}")
    Call<Void> deleteStudent(
            @Path("studentId") String studentId
    );
}
