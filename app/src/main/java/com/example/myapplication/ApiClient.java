package com.example.myapplication;

import android.os.Build;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // EMULATOR:   10.0.2.2  = "the laptop the emulator runs on".
    // REAL PHONE: 127.0.0.1 = the phone itself, but "adb reverse tcp:3000 tcp:3000"
    //             (run automatically by build.gradle) tunnels it through the USB cable
    //             to the laptop's port 3000. No Wi-Fi or IP address needed.
    private static final String EMULATOR_URL = "http://10.0.2.2:3000/";
    private static final String USB_PHONE_URL = "http://127.0.0.1:3000/";

    public static final String BASE_URL = isEmulator() ? EMULATOR_URL : USB_PHONE_URL;

    private static Retrofit retrofit = null;

    public static synchronized Retrofit getClient() {
        if (retrofit == null) {
            OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)   // fail fast if the server is off
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
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