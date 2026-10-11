package com.example.myapplication;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.UUID;

// Lecturer adds a student. Saved on the phone FIRST (Room), then sent to the server.
public class AddStudentActivity extends AppCompatActivity {
    private TextView list;

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        return e;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);
        sv.addView(root);
        setContentView(sv);

        EditText name = field("Student name (letters only)");
        EditText number = field("Student number (9 digits)");
        number.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText program = field("Programme: 1=CS 2=IT 3=DS");
        program.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText group = field("Group: 1=G01 2=G02 3=G03 4=G04");
        group.setInputType(InputType.TYPE_CLASS_NUMBER);
        Button save = new Button(this);
        save.setText("Save (works offline)");
        Button sync = new Button(this);
        sync.setText("Sync now");
        list = new TextView(this);

        root.addView(name);
        root.addView(number);
        root.addView(program);
        root.addView(group);
        root.addView(save);
        root.addView(sync);
        root.addView(list);

        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String num = number.getText().toString().trim();
            if (!n.matches("[A-Za-z ]+")) {
                Toast.makeText(this, "Name: letters and spaces only", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!num.matches("\\d{9}")) {
                Toast.makeText(this, "Student number must be exactly 9 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            int p, g;
            try {
                p = Integer.parseInt(program.getText().toString().trim());
                g = Integer.parseInt(group.getText().toString().trim());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Enter programme and group numbers", Toast.LENGTH_SHORT).show();
                return;
            }
            LocalStudent s = new LocalStudent();
            s.operationId = UUID.randomUUID().toString();   // the receipt number
            s.name = n;
            s.number = num;
            s.programId = p;
            s.groupId = g;
            s.status = "PENDING";
            s.message = "Saved on this phone";
            AppDb.get(this).dao().insert(s);                 // durable local save happens FIRST
            refresh();
            SyncHelper.sync(this, this::refresh);            // then try the server (stays PENDING if offline)
        });
        sync.setOnClickListener(v -> SyncHelper.sync(this, this::refresh));
        refresh();
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        for (LocalStudent s : AppDb.get(this).dao().all()) {
            sb.append(s.status).append(" | ").append(s.name).append(" ").append(s.number)
              .append("\n   ").append(s.message).append("\n\n");
        }
        list.setText(sb.toString());
    }
}
