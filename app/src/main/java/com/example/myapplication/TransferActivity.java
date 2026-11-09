package com.example.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lecturers export students, lab groups and programmes to a CSV file
 * (opens in Excel / Google Sheets) and import an edited CSV back.
 * Import adds and updates only; it never deletes.
 */
public class TransferActivity extends BaseActivity {

    private static final int REQ_EXPORT = 7101, REQ_IMPORT = 7102;

    private ApiService api;
    private byte[] pendingCsv;      // the file waiting to be saved
    private String pendingType;     // students / groups / programs
    private TextView status;
    private int pad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pad = (int) (16 * getResources().getDisplayMetrics().density);

        // Imports can take a while (one row at a time), so allow up to 2 minutes.
        OkHttpClient slow = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        api = ApiClient.getClient().newBuilder().client(slow).build().create(ApiService.class);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView back = text("←  Export / Import (CSV)", 20, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(text("Export saves a .csv file that opens in Excel or Google Sheets. "
                + "To import, edit that file, save it as CSV (UTF-8) and pick it here. "
                + "Import adds new rows and updates existing students. It never deletes anything.", 14, false));

        section(root, "Students", "students", "nexa-students.csv");
        section(root, "Lab groups", "groups", "nexa-groups.csv");
        section(root, "Programmes", "programs", "nexa-programmes.csv");

        status = text("", 14, false);
        root.addView(status);
    }

    private void section(LinearLayout root, String title, String type, String fileName) {
        root.addView(text(title, 17, true));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button ex = button("Export " + title.toLowerCase());
        ex.setOnClickListener(v -> exportCsv(type, fileName));
        Button im = button("Import " + title.toLowerCase());
        im.setOnClickListener(v -> pickFile(type));
        row.addView(ex, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(im, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);
    }

    // ---------------- EXPORT ----------------
    private void exportCsv(String type, String fileName) {
        status.setText("Preparing " + fileName + "…");
        api.exportCsv(Session.bearer(), type).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (isFinishing() || isDestroyed()) return;
                if (!r.isSuccessful() || r.body() == null) { status.setText(errorMessage(r)); return; }
                try {
                    pendingCsv = r.body().bytes();
                } catch (Exception e) { status.setText("Could not read the file"); return; }
                // Let the lecturer choose where to save it (Downloads, Google Drive...).
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("text/csv");
                i.putExtra(Intent.EXTRA_TITLE, fileName);
                startActivityForResult(i, REQ_EXPORT);
            }
            @Override public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (!isFinishing()) status.setText("No connection. Try again.");
            }
        });
    }

    // ---------------- IMPORT ----------------
    private void pickFile(String type) {
        pendingType = type;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel"});
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_EXPORT) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null || pendingCsv == null) throw new Exception();
                out.write(pendingCsv);
                status.setText("Saved. Open it in Excel or Google Sheets.");
                Toast.makeText(this, "CSV file saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                status.setText("Could not save the file");
            }
        } else if (requestCode == REQ_IMPORT) {
            String csv;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception();
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                    if (sb.length() > 1_000_000) { status.setText("File too large (max 1 MB)"); return; }
                }
                csv = sb.toString();
            } catch (Exception e) { status.setText("Could not read the file"); return; }

            int rows = Math.max(0, csv.trim().split("\n").length - 1);
            String type = pendingType;
            new AlertDialog.Builder(this)
                    .setTitle("Import " + type)
                    .setMessage("Import " + rows + " row(s)? New rows are added and existing students updated. Nothing is deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Import", (d, w) -> sendImport(type, csv))
                    .show();
        }
    }

    private void sendImport(String type, String csv) {
        status.setText("Importing… please wait");
        Map<String, String> body = new HashMap<>();
        body.put("csv", csv);
        api.importCsv(Session.bearer(), type, body).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (isFinishing() || isDestroyed()) return;
                if (!r.isSuccessful() || r.body() == null) { status.setText(errorMessage(r)); return; }
                try {
                    JSONObject j = new JSONObject(r.body().string());
                    StringBuilder msg = new StringBuilder(j.optString("message"));
                    JSONArray skipped = j.optJSONArray("skipped");
                    for (int i = 0; skipped != null && i < skipped.length() && i < 20; i++) {
                        JSONObject s = skipped.getJSONObject(i);
                        msg.append("\n• Row ").append(s.optInt("row")).append(": ").append(s.optString("reason"));
                    }
                    status.setText(j.optString("message"));
                    new AlertDialog.Builder(TransferActivity.this)
                            .setTitle("Import finished")
                            .setMessage(msg.toString())
                            .setPositiveButton("OK", null)
                            .show();
                } catch (Exception e) { status.setText("Import done, but the reply could not be read"); }
            }
            @Override public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (!isFinishing()) status.setText("No connection or it took too long. Check the roster before retrying.");
            }
        });
    }

    // ---------------- helpers ----------------
    private String errorMessage(Response<ResponseBody> r) {
        try {
            if (r.errorBody() != null) {
                return new JSONObject(r.errorBody().string()).optString("message", "Error " + r.code());
            }
        } catch (Exception ignored) { }
        return "Error " + r.code();
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, pad / 2, 0, pad / 2);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }
}