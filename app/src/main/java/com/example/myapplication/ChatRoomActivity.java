package com.example.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** One conversation: a group room (university / programme / lab group / staff) or a private encrypted chat. */
public class ChatRoomActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_ROOM = "room";
    public static final String EXTRA_OTHER_ID = "other_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBTITLE = "subtitle";
    public static final String MODE_ROOM = "room";
    public static final String MODE_PRIVATE = "private";

    private static final long REFRESH_MS = 3000;   // look for new messages every 3 seconds
    private static final int MAX_TEXT = 2000;

    private ApiService api;
    private boolean isPrivate;
    private String room;
    private int otherId;
    private int myId;
    private String title;

    // Private chats only
    private volatile String myPublicKey;
    private volatile String theirPublicKey;
    private volatile boolean keyRequestRunning = false;
    private volatile String keyProblem = null;     // why a private message cannot be sent yet (shown on Send)

    private long lastId = 0;
    private boolean fetching = false;
    private boolean stopped = false;

    private ListView list;
    private EditText etMessage;
    private Button btnSend;
    private TextView tvNotice;
    private MessageAdapter adapter;
    private final List<Shown> messages = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            fetch();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    /** A message ready to show. */
    private static class Shown {
        boolean mine;
        String name;
        String role;
        String text;
        String time;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);

        ImageView back = findViewById(R.id.back);
        back.setOnClickListener(v -> finish());

        if (Session.bearer() == null) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        api = ApiClient.getClient().create(ApiService.class);
        myId = Session.accountId();
        isPrivate = MODE_PRIVATE.equals(getIntent().getStringExtra(EXTRA_MODE));
        room = getIntent().getStringExtra(EXTRA_ROOM);
        otherId = getIntent().getIntExtra(EXTRA_OTHER_ID, 0);
        title = getIntent().getStringExtra(EXTRA_TITLE);

        ((TextView) findViewById(R.id.tvRoomTitle)).setText(title);
        ((TextView) findViewById(R.id.tvRoomSubtitle)).setText(getIntent().getStringExtra(EXTRA_SUBTITLE));

        tvNotice = findViewById(R.id.tvRoomNotice);
        list = findViewById(R.id.messageList);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        adapter = new MessageAdapter();
        list.setAdapter(adapter);
        btnSend.setOnClickListener(v -> sendMessage());

        if (isPrivate) {
            tvNotice.setText("🔒 Preparing secure keys…");
            prepareKeys();
        } else {
            tvNotice.setText(room != null && room.startsWith("staff:")
                    ? "Staff room: only lecturers can see this chat."
                    : "Group chat: everyone listed above can read these messages.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (api != null && !stopped) handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refresh);
        background.shutdown();
    }

    // ---------------------------------------------------------------- keys (private chats)

    private void prepareKeys() {
        if (keyRequestRunning) return;
        keyRequestRunning = true;
        background.execute(() -> {
            try {
                if (myPublicKey == null) {
                    String mine = ChatCrypto.ensureKeyPair(myId);
                    Response<ResponseBody> up = api.publishKey(Session.bearer(), new ChatDtos.PublicKeyBody(mine)).execute();
                    if (!up.isSuccessful()) {
                        setKeyProblem("Could not register your secure key with the server: " + chatbot.serverMessage(up));
                        return;
                    }
                    myPublicKey = mine;
                }
                Response<ChatDtos.KeyResponse> r = api.getKey(Session.bearer(), otherId).execute();
                if (r.isSuccessful() && r.body() != null && r.body().public_key != null) {
                    theirPublicKey = r.body().public_key;
                    keyProblem = null;
                    runOnUiThread(() -> tvNotice.setText("🔒 End-to-end encrypted. Only you and " + title
                            + " can read these messages. The server stores them locked."));
                } else if (r.code() == 404) {
                    setKeyProblem(title + " has not opened Chat yet, so there is no key to lock a message for them."
                            + " Ask them to open Chat once; you can send straight after.");
                } else {
                    setKeyProblem("Could not get " + title + "'s secure key: " + chatbot.serverMessage(r));
                }
            } catch (Exception e) {
                setKeyProblem("Private messages are not available on this phone: " + e.getMessage());
            } finally {
                keyRequestRunning = false;
            }
        });
    }

    private void setKeyProblem(String problem) {
        keyProblem = problem;
        runOnUiThread(() -> tvNotice.setText("🔒 " + problem));
    }

    // ---------------------------------------------------------------- receive

    private void fetch() {
        if (fetching || stopped) return;
        if (isPrivate && theirPublicKey == null) prepareKeys();   // they may have opened Chat since
        fetching = true;

        Call<ChatDtos.MessagesResponse> call = isPrivate
                ? api.dmMessages(Session.bearer(), otherId, lastId)
                : api.roomMessages(Session.bearer(), room, lastId);

        call.enqueue(new Callback<ChatDtos.MessagesResponse>() {
            @Override
            public void onResponse(Call<ChatDtos.MessagesResponse> c, Response<ChatDtos.MessagesResponse> response) {
                fetching = false;
                if (!response.isSuccessful() || response.body() == null) {
                    if (response.code() == 401 || response.code() == 403) {
                        stopped = true;
                        handler.removeCallbacks(refresh);
                        tvNotice.setText(chatbot.serverMessage(response));
                        btnSend.setEnabled(false);
                    }
                    return;
                }
                List<ChatDtos.Message> fresh = response.body().messages;
                if (fresh == null || fresh.isEmpty()) return;
                lastId = fresh.get(fresh.size() - 1).message_id;

                if (isPrivate) {
                    // Decrypting uses the Keystore, so do it off the screen thread.
                    background.execute(() -> {
                        List<Shown> ready = new ArrayList<>();
                        for (ChatDtos.Message m : fresh) ready.add(toShown(m));
                        runOnUiThread(() -> show(ready));
                    });
                } else {
                    List<Shown> ready = new ArrayList<>();
                    for (ChatDtos.Message m : fresh) ready.add(toShown(m));
                    show(ready);
                }
            }

            @Override
            public void onFailure(Call<ChatDtos.MessagesResponse> c, Throwable t) {
                fetching = false;   // try again on the next refresh
            }
        });
    }

    private Shown toShown(ChatDtos.Message m) {
        Shown s = new Shown();
        s.mine = m.sender_id == myId;
        s.name = m.sender_name;
        s.role = m.sender_role;
        s.time = shortTime(m.created_at);
        if (!isPrivate) {
            s.text = m.body;
        } else {
            try {
                ChatDtos.EncryptedPayload p = gson.fromJson(m.body, ChatDtos.EncryptedPayload.class);
                s.text = ChatCrypto.decrypt(p, myId, s.mine);
            } catch (Exception e) {
                s.text = "🔒 This message cannot be opened on this phone (it was locked for a different key).";
            }
        }
        return s;
    }

    private void show(List<Shown> ready) {
        messages.addAll(ready);
        adapter.notifyDataSetChanged();
        list.setSelection(messages.size() - 1);
    }

    // ---------------------------------------------------------------- send

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        if (text.length() > MAX_TEXT) {
            etMessage.setError("At most " + MAX_TEXT + " characters");
            return;
        }
        if (isPrivate && (myPublicKey == null || theirPublicKey == null)) {
            // Explain instead of silently doing nothing, and try to fetch the keys again.
            Toast.makeText(this, keyProblem != null ? keyProblem : "Preparing secure keys… try again in a moment.",
                    Toast.LENGTH_LONG).show();
            prepareKeys();
            return;
        }
        btnSend.setEnabled(false);

        if (isPrivate) {
            background.execute(() -> {
                try {
                    ChatDtos.EncryptedPayload p = ChatCrypto.encrypt(text, theirPublicKey, myPublicKey);
                    Response<ResponseBody> r = api.sendDm(Session.bearer(), otherId, new ChatDtos.SendEncrypted(p)).execute();
                    runOnUiThread(() -> afterSend(r.isSuccessful(), r.isSuccessful() ? null : chatbot.serverMessage(r)));
                } catch (Exception e) {
                    runOnUiThread(() -> afterSend(false, "Could not send: " + e.getMessage()));
                }
            });
        } else {
            api.sendRoomMessage(Session.bearer(), room, new ChatDtos.SendText(text)).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    afterSend(response.isSuccessful(), response.isSuccessful() ? null : chatbot.serverMessage(response));
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    afterSend(false, "Cannot reach the server. Your message was not sent.");
                }
            });
        }
    }

    private void afterSend(boolean ok, String problem) {
        btnSend.setEnabled(true);
        if (ok) {
            etMessage.setText("");
            fetch();   // show it straight away
        } else {
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show();   // the text stays in the box
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String shortTime(String iso) {
        if (iso == null) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(iso);
            return new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return "";
        }
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    // ---------------------------------------------------------------- list adapter

    private class MessageAdapter extends BaseAdapter {
        @Override public int getCount() { return messages.size(); }
        @Override public Object getItem(int position) { return messages.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean isEnabled(int position) { return false; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
            Shown m = messages.get(position);

            LinearLayout rowRoot = v.findViewById(R.id.messageRow);
            MaterialCardView bubble = v.findViewById(R.id.messageBubble);
            TextView tvSender = v.findViewById(R.id.tvSender);
            TextView tvText = v.findViewById(R.id.tvMessageText);
            TextView tvTime = v.findViewById(R.id.tvMessageTime);

            tvText.setText(m.text);
            tvTime.setText(m.time);

            if (m.mine) {
                rowRoot.setGravity(Gravity.END);
                bubble.setCardBackgroundColor(themeColor(androidx.appcompat.R.attr.colorPrimary));
                int white = ContextCompat.getColor(ChatRoomActivity.this, R.color.white);
                tvText.setTextColor(white);
                tvTime.setTextColor(white);
                tvSender.setVisibility(View.GONE);
            } else {
                rowRoot.setGravity(Gravity.START);
                bubble.setCardBackgroundColor(ContextCompat.getColor(ChatRoomActivity.this, R.color.card_bg_color));
                tvText.setTextColor(ContextCompat.getColor(ChatRoomActivity.this, R.color.form_text_color));
                tvTime.setTextColor(ContextCompat.getColor(ChatRoomActivity.this, R.color.form_label_color));
                tvSender.setVisibility(isPrivate ? View.GONE : View.VISIBLE);
                tvSender.setText("lecturer".equals(m.role) ? m.name + " · Lecturer" : m.name);
            }
            return v;
        }
    }
}