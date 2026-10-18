package com.example.myapplication;

import android.os.Build;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // ===== SWITCH =====
    // true  = ONLINE server (Render + TiDB): works on any phone, anywhere. Use this for the APK.
    // false = LAPTOP server (node index.js): emulator via 10.0.2.2, phone via USB/Wi-Fi debugging.
    public static final boolean USE_ONLINE = true;

    private static final String ONLINE_URL = "https://nexa-api-26d7.onrender.com/";

    // EMULATOR:   10.0.2.2  = "the laptop the emulator runs on".
    // REAL PHONE: 127.0.0.1 = tunnelled to the laptop by "adb reverse" (build.gradle does it).
    private static final String EMULATOR_URL = "http://10.0.2.2:3000/";
    private static final String USB_PHONE_URL = "http://127.0.0.1:3000/";

    public static final String BASE_URL =
            USE_ONLINE ? ONLINE_URL : (isEmulator() ? EMULATOR_URL : USB_PHONE_URL);

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
}