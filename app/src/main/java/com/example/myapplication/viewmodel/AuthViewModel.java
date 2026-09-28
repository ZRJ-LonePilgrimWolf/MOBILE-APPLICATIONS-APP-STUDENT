package com.example.myapplication.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;

import com.example.myapplication.repository.AuthRepository;

public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository repository;

    public AuthViewModel(Application application) {
        super(application);
        repository = new AuthRepository();
    }

    public void login(
            String username,
            String password,
            AuthCallback callback) {

        repository.login(username, password, callback);
    }

    public interface AuthCallback extends AuthRepository.AuthCallback {
    }
}
