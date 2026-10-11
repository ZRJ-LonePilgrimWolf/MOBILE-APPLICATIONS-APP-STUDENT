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

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private EditText editTextstudentid;
    private EditText editTextPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextstudentid = findViewById(R.id.editTextstudentid);
        editTextPassword = findViewById(R.id.editTextPassword);

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.Roll);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"Student", "Lecturer"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);
            roleAutoComplete.setDropDownBackgroundDrawable(new ColorDrawable(Color.parseColor("#1E293B")));
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
        if (loginButton != null) {
            loginButton.setOnClickListener(v -> {
                attemptLogin();
            });
        }

        TextView reg = findViewById(R.id.buttonPanel);
        if (reg != null) {
            reg.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, Registration.class);
                startActivity(intent);
            });
        }
    }

    private void attemptLogin() {
        if (editTextstudentid == null || editTextPassword == null) {
            return;
        }

        String studentid = editTextstudentid.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (studentid.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "PLEASE FILL IN ALL FIELDS", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = "https://localhost:3000/auth/login";
        JSONObject loginData = new JSONObject();
        try {
            loginData.put("studentid", studentid);
            loginData.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                url,
                loginData,
                response -> {
                    Toast.makeText(this, "LOGIN SUCCESSFUL", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, FActivity.class);
                    startActivity(intent);
                },
                error -> {
                    Toast.makeText(this, "LOGIN FAILED", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, FActivity.class);
                    startActivity(intent);
                }
        );

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(jsonObjectRequest);
    }
}
