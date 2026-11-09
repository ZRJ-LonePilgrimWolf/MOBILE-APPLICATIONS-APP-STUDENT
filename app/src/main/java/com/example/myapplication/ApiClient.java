package com.example.myapplication;

import android.os.Build;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // ===== SERVER =====
    // false = LAPTOP server on the local Wi-Fi (XAMPP + node index.js), as the lecturer asked.
    // true  = old ONLINE server (Render + TiDB), kept as a backup.
    public static final boolean USE_ONLINE = false;

    private static final String ONLINE_URL = "https://nexa-api-26d7.onrender.com/";
    // The laptop's Wi-Fi IPv4 address (ipconfig -> Wireless LAN adapter Wi-Fi).
    // It can be changed inside the app: login screen -> "Server address".
    private static final String DEFAULT_LOCAL_URL = "http://172.26.44.97:3000/";

    private static final String PREFS = "nexa_server", KEY_URL = "server_url";

    public static String BASE_URL = USE_ONLINE ? ONLINE_URL : DEFAULT_LOCAL_URL;

    private static Retrofit retrofit = null;

    public static synchronized Retrofit getClient() {
        if (retrofit == null) {
            // Free online server sleeps after 15 min idle and needs ~50 s to wake up,
            // so allow up to 60 s online. The laptop answers fast, so 15 s is plenty there.
            int wait = USE_ONLINE ? 60 : 15;
            OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(wait, TimeUnit.SECONDS)
                    .readTimeout(wait, TimeUnit.SECONDS)
                    .writeTimeout(wait, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(http)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /** Call at the start of each screen: uses the server address saved on this phone. */
    public static synchronized void load(android.content.Context c) {
        if (USE_ONLINE) return;
        String saved = c.getApplicationContext().getSharedPreferences(PREFS, 0).getString(KEY_URL, null);
        String url = saved != null ? saved : DEFAULT_LOCAL_URL;
        if (!url.equals(BASE_URL)) { BASE_URL = url; retrofit = null; }
    }

    /** Saves an address like "192.168.1.45" or "http://192.168.1.45:3000". Returns false if invalid. */
    public static synchronized boolean setServer(android.content.Context c, String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) return false;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        String scheme = s.substring(0, s.indexOf("://"));
        String host = s.substring(s.indexOf("://") + 3);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        if (host.isEmpty()) return false;
        if (!host.contains(":")) host = host + ":3000";
        String url = scheme + "://" + host + "/";
        if (okhttp3.HttpUrl.parse(url) == null) return false;
        c.getApplicationContext().getSharedPreferences(PREFS, 0).edit().putString(KEY_URL, url).apply();
        BASE_URL = url;
        retrofit = null;   // rebuild the client with the new address
        return true;
    }

    /** Dialog to view or change the server address (used on the login screen). */
    public static void showServerDialog(android.app.Activity a) {
        final android.widget.EditText in = new android.widget.EditText(a);
        in.setText(BASE_URL);
        in.setSingleLine(true);
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Server address")
                .setMessage("The laptop's Wi-Fi IPv4 address (from ipconfig), e.g. 192.168.1.45. "
                        + "The phone and the laptop must be on the same Wi-Fi.")
                .setView(in)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    if (setServer(a, in.getText().toString())) {
                        android.widget.Toast.makeText(a, "Server set to " + BASE_URL, android.widget.Toast.LENGTH_LONG).show();
                    } else {
                        android.widget.Toast.makeText(a, "That address is not valid", android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    /** True on the Android Studio emulator, false on a real phone. */
    static boolean isEmulator() {
        String fp = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        String product = Build.PRODUCT == null ? "" : Build.PRODUCT;
        String hardware = Build.HARDWARE == null ? "" : Build.HARDWARE;
        return fp.startsWith("generic") || fp.contains("emulator") || fp.contains("sdk_gphone")
                || model.contains("Emulator") || model.contains("Android SDK built for")
                || model.startsWith("sdk_gphone")
                || product.startsWith("sdk") || product.contains("emulator")
                || hardware.equals("goldfish") || hardware.equals("ranchu");
    }

    /** Wakes the Render server early (free plan sleeps after 15 min). Fire-and-forget. */
    public static void wakeServer() {
        if (!USE_ONLINE) return;
        getClient().create(ApiService.class).ping().enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override public void onResponse(retrofit2.Call<okhttp3.ResponseBody> c, retrofit2.Response<okhttp3.ResponseBody> r) { }
            @Override public void onFailure(retrofit2.Call<okhttp3.ResponseBody> c, Throwable t) { }
        });
    }
}