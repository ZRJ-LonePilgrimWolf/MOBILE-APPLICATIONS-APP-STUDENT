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

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class lecture_registration extends AppCompatActivity {

    private EditText numberInput, emailInput, passwordInput, codeInput;
    private Button registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lecturer_registration);

        // Role dropdown (same behaviour as before)
        AutoCompleteTextView roleAutoComplete = findViewById(R.id.Roll);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"Student", "Lecturer"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);
            roleAutoComplete.setText("Lecturer", false);
            roleAutoComplete.setDropDownBackgroundDrawable(new ColorDrawable(Color.parseColor("#1E293B")));
            roleAutoComplete.setOnClickListener(v -> roleAutoComplete.showDropDown());
            roleAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                if ("Student".equalsIgnoreCase((String) parent.getItemAtPosition(position))) {
                    startActivity(new Intent(lecture_registration.this, Registration.class));
                }
            });
        }

        numberInput  = findViewById(R.id.number);
        emailInput   = findViewById(R.id.email);
        passwordInput = findViewById(R.id.Password);
        codeInput    = findViewById(R.id.access_code);
        registerButton = findViewById(R.id.lec_button);

        registerButton.setOnClickListener(v -> attemptRegister());

        TextView reg = findViewById(R.id.Lec_buttonPanel);
        reg.setOnClickListener(v -> startActivity(new Intent(lecture_registration.this, lecturer_login.class)));
    }

    private void attemptRegister() {
        String username = numberInput.getText().toString().trim();
        String email    = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String code     = codeInput.getText().toString().trim();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || code.isEmpty()) {
            toast("Please fill in every field"); return;
        }
        if (!email.contains("@")) { toast("Enter a valid email address"); return; }
        if (password.length() < 6) { toast("Password must be at least 6 characters"); return; }

        registerButton.setEnabled(false);
        registerButton.setText("Registering...");

        ApiClient.getClient().create(ApiService.class)
                .registerLecturer(new RegisterLecturerRequest(username, password, email, code))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        resetButton();
                        String message = serverMessage(response);
                        if (response.isSuccessful()) {
                            toast(message);   // 201 CREATED
                            startActivity(new Intent(lecture_registration.this, lecturer_login.class));
                            finish();
                        } else {
                            toast(message);   // 403 BAD_CODE, 409 USERNAME_TAKEN...
                        }
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        resetButton();
                        toast("Cannot reach the server. Is it running?");
                    }
                });
    }

    private void resetButton() {
        registerButton.setEnabled(true);
        registerButton.setText(R.string.register);
    }

    private String serverMessage(Response<ResponseBody> response) {
        try {
            if (response.body() != null)
                return new JSONObject(response.body().string()).getString("message");
            if (response.errorBody() != null)
                return new JSONObject(response.errorBody().string()).getString("message");
        } catch (Exception ignored) { }
        return "Registration failed (code " + response.code() + ")";
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}