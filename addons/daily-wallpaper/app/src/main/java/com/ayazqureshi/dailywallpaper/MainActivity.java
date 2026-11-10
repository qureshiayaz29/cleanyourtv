package com.ayazqureshi.dailywallpaper;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The card's screen: what the add-on did last, and its three choices. Built in code; there is no layout file. */
public final class MainActivity extends Activity {
    private static final int[] DIM_STEPS = {0, 15, 30, 45, 60};

    private SharedPreferences prefs;
    private TextView status, caption;
    private Button update, dim, daily;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Updater.prefs(this);
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setBackgroundColor(0xFF0B1320);
        int side = Math.round(96 * dp);
        page.setPadding(side, Math.round(48 * dp), side, Math.round(48 * dp));

        page.addView(text(getString(R.string.app_name), 34, 0xFFFFFFFF), block(0));
        page.addView(text(getString(R.string.intro), 18, 0xFFB8C4D6), block(Math.round(8 * dp)));
        status = text("", 20, 0xFFFFFFFF);
        page.addView(status, block(Math.round(28 * dp)));
        caption = text("", 16, 0xFF8FA0B8);
        page.addView(caption, block(Math.round(6 * dp)));

        update = button(page, Math.round(28 * dp));
        update.setOnClickListener(v -> updateNow());
        dim = button(page, Math.round(10 * dp));
        dim.setOnClickListener(v -> nextDim());
        daily = button(page, Math.round(10 * dp));
        daily.setOnClickListener(v -> toggleDaily());

        setContentView(page);
        update.requestFocus();
        UpdateJob.schedule(this, prefs.getBoolean("enabled", true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void updateNow() {
        if (busy) return;
        busy = true;
        render();
        new Thread(() -> {
            Updater.run(getApplicationContext(), true);
            runOnUiThread(() -> {
                busy = false;
                render();
            });
        }, "daily-wallpaper-now").start();
    }

    /** The next darkening step. It is painted into the picture, so it shows with the next download or Update now. */
    private void nextDim() {
        int current = prefs.getInt("dim", Updater.DEFAULT_DIM), next = DIM_STEPS[0];
        for (int i = 0; i < DIM_STEPS.length; i++) {
            if (DIM_STEPS[i] == current) next = DIM_STEPS[(i + 1) % DIM_STEPS.length];
        }
        prefs.edit().putInt("dim", next).apply();
        render();
    }

    private void toggleDaily() {
        boolean enabled = !prefs.getBoolean("enabled", true);
        prefs.edit().putBoolean("enabled", enabled).apply();
        UpdateJob.schedule(this, enabled);
        render();
    }

    private void render() {
        update.setEnabled(!busy);
        update.setText(busy ? R.string.updating : R.string.update_now);
        dim.setText(getString(R.string.dim, prefs.getInt("dim", Updater.DEFAULT_DIM)));
        daily.setText(prefs.getBoolean("enabled", true) ? R.string.daily_on : R.string.daily_off);
        status.setText(statusLine());
        caption.setText(prefs.getString("caption", ""));
    }

    private String statusLine() {
        long updated = prefs.getLong("updated", 0);
        String when = updated == 0 ? "" : DateUtils.getRelativeTimeSpanString(updated, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString();
        switch (prefs.getString("status", "")) {
            case Updater.UPDATED: return getString(R.string.status_updated, when);
            case Updater.CURRENT:
                return updated == 0 ? getString(R.string.status_never) : getString(R.string.status_current, when);
            case Updater.NO_NETWORK: return getString(R.string.status_no_network);
            case Updater.NO_PERMISSION: return getString(R.string.status_no_permission);
            case Updater.NO_LAUNCHER: return getString(R.string.status_no_launcher);
            case Updater.FAILED: return getString(R.string.status_failed);
            default: return getString(R.string.status_never);
        }
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(LinearLayout page, int top) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(420 * getResources().getDisplayMetrics().density), ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        page.addView(button, params);
        return button;
    }

    private static LinearLayout.LayoutParams block(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        return params;
    }
}
