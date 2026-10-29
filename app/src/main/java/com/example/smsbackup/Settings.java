package com.example.smsbackup;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.time.LocalDate;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class Settings {
    private static final String KEY_ALIAS = "sms_backup_token";
    private static final Object LOCK = new Object();
    final SharedPreferences preferences;
    private final boolean allowHttp;

    public Settings(Context context) {
        preferences = context.getSharedPreferences("backup", Context.MODE_PRIVATE);
        allowHttp = (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public boolean enabled() { return preferences.getBoolean("enabled", false); }
    public String endpoint() { return preferences.getString("endpoint", ""); }
    public String generation() { return preferences.getString("generation", ""); }
    public String status() { return preferences.getString("status", "No uploads yet."); }
    public String nextDay() { return preferences.getString("next_day", ""); }

    public String token() throws Exception {
        String encoded = preferences.getString("token", "");
        if (encoded.isEmpty()) return "";
        String[] parts = encoded.split(":", 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128,
                Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    public void enable(String endpoint, String token, LocalDate firstDay) throws Exception {
        EndpointPolicy.validate(endpoint, allowHttp);
        if (token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Bearer token must be a single line.");
        }
        String encrypted = "";
        if (!token.isEmpty()) {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(cipher.doFinal(token.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
        }
        synchronized (LOCK) {
            String next = enabled() && !nextDay().isEmpty() ? nextDay() : firstDay.toString();
            boolean saved = preferences.edit().putString("endpoint", endpoint)
                    .putString("token", encrypted).putBoolean("enabled", true)
                    .putString("generation", UUID.randomUUID().toString())
                    .putString("install_id", preferences.getString("install_id", UUID.randomUUID().toString()))
                    .putString("next_day", next).putString("status", "Waiting for the next backup.")
                    .commit();
            if (!saved) throw new IllegalStateException("Could not save settings.");
        }
    }

    public void disable() {
        synchronized (LOCK) {
            preferences.edit().putBoolean("enabled", false)
                    .putString("generation", UUID.randomUUID().toString()).commit();
        }
    }

    public boolean active(String generation) {
        return enabled() && generation().equals(generation);
    }

    public String installId() { return preferences.getString("install_id", ""); }

    public void status(String generation, String message) {
        synchronized (LOCK) {
            if (active(generation)) preferences.edit().putString("status", message).apply();
        }
    }

    public boolean complete(String generation, LocalDate day, boolean manual, String message) {
        synchronized (LOCK) {
            if (!active(generation)) return false;
            SharedPreferences.Editor editor = preferences.edit().putString("status", message);
            if (!manual) editor.putString("next_day", day.plusDays(1).toString());
            return editor.commit();
        }
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}