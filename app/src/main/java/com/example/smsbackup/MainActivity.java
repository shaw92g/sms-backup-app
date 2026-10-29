package com.example.smsbackup;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class MainActivity extends Activity {
    private static final int SMS_PERMISSION_REQUEST = 10;
    private static final int INK = Color.rgb(25, 42, 38);
    private static final int MUTED = Color.rgb(80, 99, 93);
    private com.example.smsbackup.Settings settings;
    private EditText endpoint;
    private EditText token;
    private EditText backupDate;
    private Switch enabled;
    private TextView schedule;
    private TextView timing;
    private TextView status;
    private Button upload;
    private boolean refreshing;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (preferences, key) -> runOnUiThread(this::refresh);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new com.example.smsbackup.Settings(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(scroll);

        TextView title = text("SMS Backup", 30, INK);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title);
        TextView subtitle = text("DAILY ARCHIVE", 12, MUTED);
        content.addView(subtitle);

        heading(content, "Schedule");
        enabled = new Switch(this);
        enabled.setText("Nightly backup");
        enabled.setTextSize(18);
        enabled.setMinHeight(dp(56));
        content.addView(enabled, fullWidth());
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (refreshing) return;
            if (checked) confirmAndEnable();
            else {
                settings.disable();
                BackupScheduler.cancel(this);
                refresh();
            }
        });
        schedule = text("", 17, INK);
        content.addView(schedule);
        timing = text("", 14, MUTED);
        timing.setPadding(0, dp(8), 0, 0);
        content.addView(timing);
        if (Build.VERSION.SDK_INT >= 31) {
            Button alarms = button("Alarms & reminders", android.R.drawable.ic_lock_idle_alarm);
            content.addView(alarms, spaced());
            alarms.setOnClickListener(view -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (android.content.ActivityNotFoundException exception) {
                    Toast.makeText(this, "Open system settings to allow exact alarms.", Toast.LENGTH_LONG).show();
                }
            });
        }

        heading(content, "Destination");
        content.addView(text("Server endpoint", 14, MUTED));
        endpoint = new EditText(this);
        endpoint.setHint("https://your-server.example/sms");
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setSingleLine(true);
        endpoint.setText(settings.endpoint());
        endpoint.setMinHeight(dp(56));
        content.addView(endpoint, fullWidth());

        TextView tokenLabel = text("Bearer token (optional)", 14, MUTED);
        tokenLabel.setPadding(0, dp(16), 0, 0);
        content.addView(tokenLabel);
        token = new EditText(this);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setSingleLine(true);
        token.setSaveEnabled(false);
        token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        token.setMinHeight(dp(56));
        try {
            token.setText(settings.token());
        } catch (Exception exception) {
            Toast.makeText(this, "Saved token is unavailable. Enter it again.", Toast.LENGTH_LONG).show();
        }
        content.addView(token, fullWidth());
        Button save = button("Save & enable", android.R.drawable.ic_menu_save);
        content.addView(save, spaced());
        save.setOnClickListener(view -> confirmAndEnable());

        heading(content, "Activity");
        status = text("", 16, INK);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status, fullWidth());
        TextView dateLabel = text("Backup date (optional)", 14, MUTED);
        dateLabel.setPadding(0, dp(16), 0, 0);
        content.addView(dateLabel);
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        backupDate = new EditText(this);
        backupDate.setId(View.generateViewId());
        dateLabel.setLabelFor(backupDate.getId());
        backupDate.setHint("YYYY-MM-DD");
        backupDate.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        backupDate.setSingleLine(true);
        backupDate.setMinHeight(dp(56));
        if (state != null) backupDate.setText(state.getString("backup_date", ""));
        dateRow.addView(backupDate, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ImageButton calendar = new ImageButton(this);
        calendar.setImageResource(android.R.drawable.ic_menu_my_calendar);
        calendar.setContentDescription("Choose backup date");
        calendar.setTooltipText("Choose backup date");
        dateRow.addView(calendar, new LinearLayout.LayoutParams(dp(56), dp(56)));
        calendar.setOnClickListener(view -> {
            LocalDate initial;
            try {
                initial = BackupDates.uploadDay(backupDate.getText().toString(), LocalDate.now());
            } catch (IllegalArgumentException exception) {
                initial = LocalDate.now().minusDays(1);
            }
            new DatePickerDialog(this, (picker, year, month, dayOfMonth) -> {
                backupDate.setText(LocalDate.of(year, month + 1, dayOfMonth).toString());
                backupDate.setError(null);
            }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
        });
        content.addView(dateRow, fullWidth());
        upload = button("Upload SMS", android.R.drawable.ic_menu_upload);
        content.addView(upload, spaced());
        upload.setOnClickListener(view -> {
            try {
                LocalDate day = BackupDates.uploadDay(backupDate.getText().toString(), LocalDate.now());
                backupDate.setError(null);
                settings.status(settings.generation(), "Queued " + day + "; waiting for network.");
                BackupScheduler.enqueue(this, day);
            } catch (IllegalArgumentException exception) {
                backupDate.setError("Enter a valid date as YYYY-MM-DD, or leave blank for yesterday.");
                backupDate.requestFocus();
            }
        });
        refresh();
    }

    private void confirmAndEnable() {
        if (!settings.preferences.getBoolean("consent", false)) {
            new AlertDialog.Builder(this).setTitle("Allow SMS backup?")
                    .setMessage("Sender, message text, and timestamps for received SMS will be sent "
                            + "to your configured server, including sensitive messages and verification codes. "
                            + "Only use a server you trust and a device you are authorized to back up.")
                    .setNegativeButton("Cancel", (dialog, which) -> refresh())
                    .setPositiveButton("Continue", (dialog, which) -> {
                        settings.preferences.edit().putBoolean("consent", true).apply();
                        requestPermissionAndEnable();
                    }).setOnCancelListener(dialog -> refresh()).show();
        } else requestPermissionAndEnable();
    }

    private void requestPermissionAndEnable() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_SMS}, SMS_PERMISSION_REQUEST);
            refresh();
            return;
        }
        try {
            LocalDate firstDay = BackupDates.nextAlarm(Instant.now(), ZoneId.systemDefault())
                    .toLocalDate().minusDays(1);
            settings.enable(endpoint.getText().toString().trim(), token.getText().toString().trim(), firstDay);
            BackupScheduler.cancel(this);
            BackupScheduler.schedule(this);
            BackupScheduler.enqueue(this, null);
            Toast.makeText(this, "Backup settings saved.", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException exception) {
            endpoint.setError("Enter a valid server URL and single-line token. HTTP requires a debug build.");
        } catch (Exception exception) {
            Toast.makeText(this, "Could not save settings securely. Please try again.", Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == SMS_PERMISSION_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) requestPermissionAndEnable();
            else {
                new AlertDialog.Builder(this).setTitle("SMS permission required")
                        .setMessage("Allow SMS access in app settings. Some installers restrict this permission.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("App settings", (dialog, which) -> startActivity(
                                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + getPackageName())))).show();
                refresh();
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("backup_date", backupDate.getText().toString());
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        settings.preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        if (settings.enabled()) {
            BackupScheduler.schedule(this);
            BackupScheduler.enqueue(this, null);
        }
        refresh();
    }

    @Override protected void onPause() {
        settings.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        super.onPause();
    }

    private void refresh() {
        if (enabled == null || status == null) return;
        refreshing = true;
        enabled.setChecked(settings.enabled());
        refreshing = false;
        boolean permission = checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
        schedule.setText(settings.enabled()
                ? "Next: " + BackupDates.nextAlarm(Instant.now(), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm"))
                : "Paused");
        timing.setText((BackupScheduler.exactAllowed(this) ? "1:00 AM" : "Around 1:00 AM")
                + " / " + ZoneId.systemDefault().getId());
        status.setText(!permission && settings.enabled() ? "SMS permission required. Save & enable to grant access."
                : settings.status());
        upload.setEnabled(settings.enabled() && permission);
    }

    private void heading(LinearLayout parent, String label) {
        TextView heading = text(label, 18, INK);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, dp(32), 0, dp(12));
        parent.addView(heading);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, int icon) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(52));
        button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(12));
        return button;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(12);
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}