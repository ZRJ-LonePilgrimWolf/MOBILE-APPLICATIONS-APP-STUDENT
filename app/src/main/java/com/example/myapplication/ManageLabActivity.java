package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Lecturers add and delete lab groups and programmes. */
public class ManageLabActivity extends BaseActivity {

    private ApiService api;
    private LinearLayout groupList, programList;
    private int pad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = ApiClient.getClient().create(ApiService.class);
        pad = (int) (16 * getResources().getDisplayMetrics().density);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        Button back = button("← Back");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        // ---- Lab groups ----
        root.addView(title("Lab groups"));
        EditText groupInput = input("New group, e.g. G15");
        root.addView(groupInput);
        Button addGroup = button("Add group");
        addGroup.setOnClickListener(v -> add(true, groupInput));
        root.addView(addGroup);
        groupList = new LinearLayout(this);
        groupList.setOrientation(LinearLayout.VERTICAL);
        root.addView(groupList);

        // ---- Programmes ----
        root.addView(title("Programmes"));
        EditText programInput = input("New programme, e.g. Computer Engineering");
        root.addView(programInput);
        Button addProgram = button("Add programme");
        addProgram.setOnClickListener(v -> add(false, programInput));
        root.addView(addProgram);
        programList = new LinearLayout(this);
        programList.setOrientation(LinearLayout.VERTICAL);
        root.addView(programList);

        load();
    }

    /** Reads both lists from GET /api/reference and shows them. */
    private void load() {
        api.referenceRaw().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (isFinishing() || isDestroyed()) return;
                if (!r.isSuccessful() || r.body() == null) { toast("Could not load the lists (" + r.code() + ")"); return; }
                try {
                    JSONObject json = new JSONObject(r.body().string());
                    groupList.removeAllViews();
                    JSONArray groups = json.optJSONArray("groups");
                    for (int i = 0; groups != null && i < groups.length(); i++) {
                        JSONObject g = groups.getJSONObject(i);
                        int id = g.getInt("lab_group_id");
                        String name = g.optString("group_name");
                        String label = name + "  (" + g.optInt("member_count") + "/" + g.optInt("max_members", 16) + ")";
                        groupList.addView(row(label, () -> confirmDelete(true, id, name)));
                    }
                    programList.removeAllViews();
                    JSONArray programs = json.optJSONArray("programs");
                    for (int i = 0; programs != null && i < programs.length(); i++) {
                        JSONObject p = programs.getJSONObject(i);
                        int id = p.getInt("program_id");
                        String name = p.optString("program_name");
                        programList.addView(row(name, () -> confirmDelete(false, id, name)));
                    }
                } catch (Exception e) {
                    toast("Could not read the lists");
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                toast("No connection. Try again.");
            }
        });
    }

    private void add(boolean group, EditText in) {
        String name = in.getText().toString().trim();
        if (name.isEmpty()) { in.setError("Enter a name"); return; }
        Map<String, String> body = new HashMap<>();
        body.put(group ? "group_name" : "program_name", name);
        Call<ResponseBody> call = group
                ? api.addLabGroup(Session.bearer(), body)
                : api.addProgram(Session.bearer(), body);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful()) {
                    in.setText("");
                    toast(group ? "Group added" : "Programme added");
                    load();
                } else {
                    toast(errorMessage(r));
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                toast("No connection. Try again.");
            }
        });
    }

    private void confirmDelete(boolean group, int id, String name) {
        new AlertDialog.Builder(this)
                .setTitle(group ? "Delete lab group" : "Delete programme")
                .setMessage("Delete \"" + name + "\"? This only works if no students are in it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    Call<ResponseBody> call = group
                            ? api.deleteLabGroup(Session.bearer(), id)
                            : api.deleteProgram(Session.bearer(), id);
                    call.enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                            if (isFinishing() || isDestroyed()) return;
                            if (r.isSuccessful()) { toast("Deleted"); load(); }
                            else toast(errorMessage(r));
                        }
                        @Override
                        public void onFailure(Call<ResponseBody> c, Throwable t) {
                            if (isFinishing() || isDestroyed()) return;
                            toast("No connection. Try again.");
                        }
                    });
                })
                .show();
    }

    /** The server's own message, e.g. "This lab group still has 12 student(s)...". */
    private String errorMessage(Response<ResponseBody> r) {
        try {
            if (r.errorBody() != null) {
                return new JSONObject(r.errorBody().string()).optString("message", "Error " + r.code());
            }
        } catch (Exception ignored) { }
        return "Error " + r.code();
    }

    // ---------- small UI helpers ----------
    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(20);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, pad, 0, pad / 2);
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout row(String label, Runnable onDelete) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button del = button("Delete");
        del.setTextColor(0xFFD32F2F);
        del.setOnClickListener(v -> onDelete.run());
        row.addView(del);
        return row;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}