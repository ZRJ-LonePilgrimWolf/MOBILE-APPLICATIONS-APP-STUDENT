package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Registration extends BaseActivity {

    // Same rules as the server (middleware.js), so the student sees the problem before sending.
    private static final String NAME_RULE = "^\\p{L}[\\p{L}\\p{M} .'’-]{1,99}$";
    private static final String NUMBER_RULE = "^\\d{9}$";

    private EditText etFullName, etUsername, etEmail, etNumber, etPassword;
    private AutoCompleteTextView acProgram, acGroup;
    private Button btnRegister;

    // label shown in the list -> real ID in MySQL
    private final Map<String, Integer> programIdByLabel = new LinkedHashMap<>();
    private final Map<String, ReferenceResponse.Group> groupByLabel = new LinkedHashMap<>();
    private int selectedProgramId = -1;
    private int selectedGroupId = -1;

    private ApiService api;

    // If the student taps a list before it has loaded, open it as soon as it arrives.
    private boolean openProgramWhenLoaded = false;
    private boolean openGroupWhenLoaded = false;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration);

        api = ApiClient.getClient().create(ApiService.class);

        // Item 11: back arrow
        ImageView back = findViewById(R.id.back);
        back.setOnClickListener(v -> finish());

        etFullName = findViewById(R.id.full_name);
        etUsername = findViewById(R.id.username);
        etEmail = findViewById(R.id.email);
        etNumber = findViewById(R.id.number);
        etPassword = findViewById(R.id.Password);
        acProgram = findViewById(R.id.dropdown_menu);
        acGroup = findViewById(R.id.labgroup);
        btnRegister = findViewById(R.id.button);

        setupRoleDropdown();
        setupProgramAndGroupDropdowns();
        loadProgrammesAndGroups();

        // Item 8: the button now validates and registers on the server; it no longer just opens the next page.
        btnRegister.setOnClickListener(v -> attemptRegister());

        TextView haveAccount = findViewById(R.id.butt);
        haveAccount.setOnClickListener(v -> goToLogin(null));
    }

    // ---------------------------------------------------------------- dropdowns

    private void setupRoleDropdown() {
        AutoCompleteTextView role = findViewById(R.id.Roll);
        if (role == null) return;
        List<String> roles = new ArrayList<>();
        roles.add("Student");
        roles.add("Lecturer");
        role.setAdapter(new NoFilterAdapter(this, roles));
        styleDropdown(role);
        role.setOnClickListener(v -> open(role));
        role.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            if ("Lecturer".equalsIgnoreCase(selected)) {
                startActivity(new Intent(Registration.this, lecture_registration.class));
                finish();
            }
        });
    }

    private void setupProgramAndGroupDropdowns() {
        styleDropdown(acProgram);
        styleDropdown(acGroup);

        acProgram.setOnClickListener(v -> {
            if (programIdByLabel.isEmpty()) {
                openProgramWhenLoaded = true;
                toast("Loading programmes…");
                loadProgrammesAndGroups();
            } else {
                open(acProgram);
            }
        });
        acGroup.setOnClickListener(v -> {
            if (groupByLabel.isEmpty()) {
                openGroupWhenLoaded = true;
                toast("Loading lab groups…");
                loadProgrammesAndGroups();
            } else {
                open(acGroup);
            }
        });

        // Look the choice up by its LABEL (positions change when the list filters itself).
        acProgram.setOnItemClickListener((parent, view, position, id) -> {
            String label = (String) parent.getItemAtPosition(position);
            Integer pid = programIdByLabel.get(label);
            selectedProgramId = pid == null ? -1 : pid;
            acProgram.setError(null);
        });

        // Item 10: picking a lab group now stores its real ID; full groups are refused here already.
        acGroup.setOnItemClickListener((parent, view, position, id) -> {
            String label = (String) parent.getItemAtPosition(position);
            ReferenceResponse.Group g = groupByLabel.get(label);
            if (g == null) { selectedGroupId = -1; return; }
            if (g.member_count >= g.max_members) {
                selectedGroupId = -1;
                acGroup.setText("", false);
                toast(g.group_name + " is full (" + g.max_members + " students). Choose another group.");
                return;
            }
            selectedGroupId = g.lab_group_id;
            acGroup.setError(null);
        });
    }

    private void styleDropdown(AutoCompleteTextView ac) {
        ac.setDropDownBackgroundDrawable(new ColorDrawable(
                ContextCompat.getColor(this, R.color.card_bg_color)));
    }

    /** Opens the list (after the screen is ready). */
    private void open(AutoCompleteTextView ac) {
        ac.post(() -> {
            if (ac.getWindowToken() != null && ac.getAdapter() != null) ac.showDropDown();
        });
    }

    /**
     * A list adapter that never hides items. A normal ArrayAdapter "filters" by the text in the box,
     * so after one choice (or with an error showing) it could show nothing at all.
     */
    private static class NoFilterAdapter extends ArrayAdapter<String> {
        private final List<String> all;

        NoFilterAdapter(Context context, List<String> items) {
            super(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(items));
            all = new ArrayList<>(items);
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults r = new FilterResults();
                    r.values = all;
                    r.count = all.size();
                    return r;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }
    }

    /** Gets the REAL programmes and lab groups (with their MySQL IDs) from the server. */
    private void loadProgrammesAndGroups() {
        if (loading) return;
        loading = true;
        api.getReference().enqueue(new Callback<ReferenceResponse>() {
            @Override
            public void onResponse(Call<ReferenceResponse> call, Response<ReferenceResponse> response) {
                loading = false;
                if (!response.isSuccessful() || response.body() == null) {
                    toast("Could not load programmes and lab groups (server answered " + response.code() + ")");
                    return;
                }
                ReferenceResponse ref = response.body();

                programIdByLabel.clear();
                if (ref.programs != null) {
                    for (ReferenceResponse.Program p : ref.programs) {
                        programIdByLabel.put(p.program_name, p.program_id);
                    }
                }

                groupByLabel.clear();
                if (ref.groups != null) {
                    for (ReferenceResponse.Group g : ref.groups) {
                        String label = g.member_count >= g.max_members
                                ? g.group_name + "  (FULL)"
                                : g.group_name + "  (" + g.member_count + "/" + g.max_members + " students)";
                        groupByLabel.put(label, g);
                    }
                }

                if (programIdByLabel.isEmpty() || groupByLabel.isEmpty()) {
                    toast("The server sent no programmes or lab groups. Check the program and labgroup tables.");
                }

                acProgram.setAdapter(new NoFilterAdapter(Registration.this, new ArrayList<>(programIdByLabel.keySet())));
                acGroup.setAdapter(new NoFilterAdapter(Registration.this, new ArrayList<>(groupByLabel.keySet())));

                // The student tapped a list while it was loading: open it now.
                if (openProgramWhenLoaded) { openProgramWhenLoaded = false; open(acProgram); }
                if (openGroupWhenLoaded) { openGroupWhenLoaded = false; open(acGroup); }
            }

            @Override
            public void onFailure(Call<ReferenceResponse> call, Throwable t) {
                loading = false;
                openProgramWhenLoaded = false;
                openGroupWhenLoaded = false;
                toast("Cannot reach the server (" + t.getClass().getSimpleName() + "). Is the backend running? Tap Program to retry.");
            }
        });
    }

    // ---------------------------------------------------------------- register

    private void attemptRegister() {
        String name = etFullName.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String number = etNumber.getText().toString().trim();
        String password = etPassword.getText().toString();

        List<String> problems = new ArrayList<>();

        if (!name.matches(NAME_RULE)) {
            etFullName.setError("Enter your full name (letters and spaces only)");
            problems.add("name");
        }
        if (username.length() < 3 || username.contains(" ")) {
            etUsername.setError("At least 3 characters, no spaces");
            problems.add("username");
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email address");
            problems.add("email");
        }
        if (!number.matches(NUMBER_RULE)) {
            etNumber.setError("Student number must be exactly 9 digits");
            problems.add("student number");
        }
        if (selectedProgramId < 0) {
            acProgram.setError("Choose your programme");
            problems.add("programme");
        }
        if (selectedGroupId < 0) {
            acGroup.setError("Choose a lab group");
            problems.add("lab group");
        }
        if (password.length() < 6) {
            etPassword.setError("At least 6 characters");
            problems.add("password");
        }

        if (!problems.isEmpty()) {
            toast("Please fix: " + TextUtils.join(", ", problems));
            return;   // nothing is sent, and the student stays on this page
        }

        btnRegister.setEnabled(false);
        btnRegister.setText("Registering…");

        RegisterStudentRequest body = new RegisterStudentRequest(
                username, password, email, name, number, selectedProgramId, selectedGroupId);

        api.registerStudent(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                btnRegister.setEnabled(true);
                btnRegister.setText(R.string.register);

                if (response.isSuccessful()) {   // 201 CREATED or 200 LINKED
                    toast("Registered! Log in with your student number " + number + " or username " + username + ".");
                    goToLogin(number);
                    return;
                }

                // Read the server's { code, message } and show it next to the right field.
                String code = "", message = "Registration failed (" + response.code() + ")";
                try (ResponseBody err = response.errorBody()) {
                    if (err != null) {
                        JSONObject json = new JSONObject(err.string());
                        code = json.optString("code", "");
                        message = json.optString("message", message);
                    }
                } catch (Exception ignored) { }

                switch (code) {
                    case "USERNAME_TAKEN":
                        etUsername.setError(message);
                        break;
                    case "DUPLICATE":
                    case "ALREADY_REGISTERED":
                    case "NUMBER_RESERVED":
                        etNumber.setError(message);
                        break;
                    case "GROUP_FULL":
                    case "GROUP_NOT_FOUND":
                        selectedGroupId = -1;
                        acGroup.setText("", false);
                        acGroup.setError(message);
                        loadProgrammesAndGroups();   // refresh the counts
                        break;
                    case "OWNERSHIP_FAILED":
                        etFullName.setError(message);
                        break;
                    default:
                        break;
                }
                toast(message);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                btnRegister.setEnabled(true);
                btnRegister.setText(R.string.register);
                toast("Cannot reach the server. Is the backend running?");
            }
        });
    }

    /** Back to the login page; the old copy of it is replaced so Back does not return here. */
    private void goToLogin(String studentNumber) {
        Intent i = new Intent(Registration.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (studentNumber != null) i.putExtra("student_number", studentNumber);
        startActivity(i);
        finish();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}