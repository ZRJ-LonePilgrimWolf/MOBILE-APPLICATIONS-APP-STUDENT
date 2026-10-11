package com.example.myapplication;

import android.content.Intent;
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

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.Roll);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"Student", "Lecturer"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);
            roleAutoComplete.setOnClickListener(v -> roleAutoComplete.showDropDown());
            roleAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    roleAutoComplete.showDropDown();
                }
            });

            roleAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                String selectedRole = (String) parent.getItemAtPosition(position);
                if ("Lecturer".equalsIgnoreCase(selectedRole)) {
                    Intent intent = new Intent(MainActivity.this, lecturer_login.class);
                    startActivity(intent);
                }
            });
        }

        Button loginButton = findViewById(R.id.button);
        EditText etNumber = findViewById(R.id.number);
        EditText etPassword = findViewById(R.id.Password);

        loginButton.setOnClickListener(v -> {
            String numberStr = etNumber != null ? etNumber.getText().toString().trim() : "";
            String passwordStr = etPassword != null ? etPassword.getText().toString().trim() : "";

            if (numberStr.isEmpty() || passwordStr.isEmpty()) {
                Toast.makeText(this, "Please enter student number and password", Toast.LENGTH_SHORT).show();
                return;
            }

            ApiService apiService = ApiClient.getClient().create(ApiService.class);
            LoginRequest request = new LoginRequest(numberStr, passwordStr, "Student");

            apiService.login(request).enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        if (!"student".equalsIgnoreCase(response.body().getRole())) {
                            Toast.makeText(MainActivity.this, "This is not a student account. Use the Lecturer login.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Session.start(response.body().getToken(), response.body().getAccountId(), response.body().getRole());
                        Toast.makeText(MainActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(MainActivity.this, FActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(MainActivity.this, "Login Failed: Invalid credentials or server error", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });

        TextView reg = findViewById(R.id.buttonPanel);
        reg.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Registration.class);
            startActivity(intent);
        });
    }
}
