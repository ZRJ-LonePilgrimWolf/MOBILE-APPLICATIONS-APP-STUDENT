package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A round floating chat button (bottom-right of the screen) with a red unread count on it.
 * Built in code and laid over the screen, so no layout file has to change.
 * Tapping it opens the chat home. The count refreshes every few seconds while the screen is visible.
 */
public final class ChatBubble {

    private static final long REFRESH_MS = 8000;   // check for new messages every 8 seconds

    private final Activity activity;
    private final ApiService api;
    private final View container;
    private final MaterialCardView circle;
    private final TextView badge;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            load();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    public static ChatBubble attach(Activity activity) {
        return new ChatBubble(activity);
    }

    private ChatBubble(Activity a) {
        activity = a;
        api = ApiClient.getClient().create(ApiService.class);

        // Holder 64x64dp, bottom-right, lifted above the bottom navigation bar.
        FrameLayout holder = new FrameLayout(a);
        FrameLayout.LayoutParams holderLp = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM | Gravity.END);
        holderLp.setMargins(0, 0, dp(16), dp(96));

        // The round button in the theme colour, with a speech-bubble icon.
        circle = new MaterialCardView(a);
        circle.setRadius(dp(28));
        circle.setCardElevation(dp(6));
        circle.setStrokeWidth(0);
        circle.setCardBackgroundColor(themeColor(androidx.appcompat.R.attr.colorPrimary));
        circle.setClickable(true);
        circle.setFocusable(true);
        circle.setContentDescription("Open chat");
        circle.setOnClickListener(v -> activity.startActivity(new Intent(activity, chatbot.class)));

        TextView icon = new TextView(a);
        icon.setText("💬");
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        circle.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        holder.addView(circle, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.START));

        // The red count, on the top-right edge of the button (hidden when 0).
        badge = new TextView(a);
        badge.setBackgroundResource(R.drawable.badge_unread);
        badge.setTextColor(ContextCompat.getColor(a, R.color.white));
        badge.setTextSize(12);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(22));
        badge.setPadding(dp(6), 0, dp(6), 0);
        badge.setElevation(dp(10));   // drawn above the button's shadow
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        badge.setVisibility(View.GONE);
        holder.addView(badge, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(22), Gravity.TOP | Gravity.END));

        // Lay it over whatever the screen already shows.
        ViewGroup content = a.findViewById(android.R.id.content);
        content.addView(holder, holderLp);
        container = holder;
    }

    /** Call from onResume: shows the button (only when logged in) and starts checking for messages. */
    public void start() {
        if (Session.bearer() == null) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        if (!running) {
            running = true;
            handler.post(refresh);
        }
    }

    /** Call from onPause: stops checking (saves battery and data). */
    public void stop() {
        running = false;
        handler.removeCallbacks(refresh);
    }

    private void load() {
        if (Session.bearer() == null) return;
        api.chatUnread(Session.bearer()).enqueue(new Callback<ChatDtos.UnreadResponse>() {
            @Override
            public void onResponse(Call<ChatDtos.UnreadResponse> call, Response<ChatDtos.UnreadResponse> response) {
                if (response.isSuccessful() && response.body() != null) show(response.body().total);
            }

            @Override
            public void onFailure(Call<ChatDtos.UnreadResponse> call, Throwable t) { }   // try again next time
        });
    }

    private void show(int total) {
        if (total > 0) {
            badge.setText(total > 99 ? "99+" : String.valueOf(total));
            badge.setVisibility(View.VISIBLE);
            circle.setContentDescription("Open chat, " + total + " unread messages");
        } else {
            badge.setVisibility(View.GONE);
            circle.setContentDescription("Open chat");
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        activity.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
}