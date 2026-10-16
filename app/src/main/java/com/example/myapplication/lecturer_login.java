package com.example.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class lecturer_login extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lecturer_login);

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.Roll);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"Student", "Lecturer"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);
            roleAutoComplete.setText("Lecturer", false);
            roleAutoComplete.setDropDownBackgroundDrawable(new ColorDrawable(androidx.core.content.ContextCompat.getColor(this, R.color.card_bg_color)));
            roleAutoComplete.setOnClickListener(v -> roleAutoComplete.showDropDown());
            roleAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    roleAutoComplete.post(() -> { if (roleAutoComplete.getWindowToken() != null) roleAutoComplete.showDropDown(); });
                }
            });
            roleAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                String selectedRole = (String) parent.getItemAtPosition(position);
                if ("Student".equalsIgnoreCase(selectedRole)) {
                    Intent intent = new Intent(lecturer_login.this, MainActivity.class);
                    startActivity(intent);
                }
            });
        }

        Button loginButton = findViewById(R.id.lec_button);
        EditText etNumber = findViewById(R.id.number);
        EditText etPassword = findViewById(R.id.Password);

        if (loginButton != null) {
            loginButton.setOnClickListener(v -> {
                String numberStr = etNumber != null ? etNumber.getText().toString().trim() : "";
                String passwordStr = etPassword != null ? etPassword.getText().toString().trim() : "";

                if (numberStr.isEmpty() || passwordStr.isEmpty()) {
                    Toast.makeText(this, "Please enter lecturer ID and password", Toast.LENGTH_SHORT).show();
                    return;
                }

                ApiService apiService = ApiClient.getClient().create(ApiService.class);
                LoginRequest request = new LoginRequest(numberStr, passwordStr, "Lecturer");

                apiService.login(request).enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            if (!"lecturer".equalsIgnoreCase(response.body().getRole())) {
                                Toast.makeText(lecturer_login.this, "This is not a lecturer account. Use the Student login.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            Session.start(response.body().getToken(), response.body().getAccountId(), response.body().getRole());
                            ChatCrypto.publishInBackground();   // so others can send private messages straight away
                            Toast.makeText(lecturer_login.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(lecturer_login.this, LecturerRosterActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(lecturer_login.this, "Login Failed: Invalid credentials or server error", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        Toast.makeText(lecturer_login.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            });
        }

        TextView reg = findViewById(R.id.Lec_buttonPanel);
        if (reg != null) {
            reg.setOnClickListener(v -> {
                Intent intent = new Intent(lecturer_login.this, lecture_registration.class);
                startActivity(intent);
            });
        }
        TextView forgotPassword = findViewById(R.id.Lec);
        if (forgotPassword != null) {
            forgotPassword.setOnClickListener(v -> {
                Intent intent = new Intent(lecturer_login.this, resetpassword.class);
                startActivity(intent);
            });
        }
    }
}