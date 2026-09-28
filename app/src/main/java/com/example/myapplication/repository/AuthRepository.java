package com.example.myapplication.repository;

import com.example.myapplication.model.User;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {

    private final ApiService apiService;

    public AuthRepository() {
        apiService = ApiClient.getApiService();
    }

    public void login(
            String username,
            String password,
            AuthCallback callback) {

        apiService.login(username, password).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Login failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public interface AuthCallback {
        void onSuccess(User user);
        void onError(String message);
    }
}
