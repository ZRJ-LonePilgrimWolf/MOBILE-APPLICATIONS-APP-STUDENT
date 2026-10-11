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
}
