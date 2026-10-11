package com.example.myapplication;

// Keeps the login token in memory only (never written to disk, cleared on logout / app close).
public class Session {
    private static String token;
    private static int accountId;
    private static String role;

    public static void start(String t, int id, String r) { token = t; accountId = id; role = r; }
    public static void clear() { token = null; accountId = 0; role = null; }
    public static String bearer() { return token == null ? null : "Bearer " + token; }
    public static int accountId() { return accountId; }
    public static String role() { return role; }
}
