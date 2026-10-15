package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class chatbot extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        EditText etChatMessage = findViewById(R.id.etChatMessage);
        Button btnSendMessage = findViewById(R.id.btnSendMessage);

        if (btnSendMessage != null && etChatMessage != null) {
            btnSendMessage.setOnClickListener(v -> {
                String msg = etChatMessage.getText().toString().trim();
                if (msg.isEmpty()) {
                    Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Query sent to AI Assistant!", Toast.LENGTH_SHORT).show();
                    etChatMessage.setText("");
                }
            });
        }
    }
}
