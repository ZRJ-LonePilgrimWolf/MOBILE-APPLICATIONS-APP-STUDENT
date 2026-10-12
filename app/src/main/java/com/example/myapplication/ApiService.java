package com.example.myapplication;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/login")
    Call<LoginResponse> login(@Body LoginRequest loginRequest);

    @GET("api/students")
    Call<StudentsResponse> getStudents(@Header("Authorization") String bearer);

    @POST("api/students")
    Call<ResponseBody> createStudent(@Header("Authorization") String bearer, @Body NewStudent body);


    // ---- Registration (registerSafe.js on the server) ----
    @POST("api/register/student")
    Call<ResponseBody> registerStudent(@Body RegisterStudentRequest body);

    @POST("api/register/lecturer")
    Call<ResponseBody> registerLecturer(@Body RegisterLecturerRequest body);
}
