package com.ayazqureshi.dailywallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads the picture of the day, darkens it and hands it to the launcher as its wallpaper.jpg, through the
 * launcher's own theme provider (the route tools/theme.sh uses). Everything here blocks: call it off the main thread.
 */
final class Updater {
    static final String PREFS = "daily_wallpaper";
    static final String LAUNCHER = "com.gathin.tvhome";
    static final int DEFAULT_DIM = 30;
    /** What the last run came to, by name, in the "status" preference. */
    static final String UPDATED = "updated", CURRENT = "current", NO_NETWORK = "no_network",
            NO_PERMISSION = "no_permission", NO_LAUNCHER = "no_launcher", FAILED = "failed";

    private static final String TAG = "DailyWallpaper";
    private static final Uri TARGET = Uri.parse("content://" + LAUNCHER + ".theme/wallpaper.jpg");
    /** A new picture is looked for once this long after the last look that worked. */
    private static final long FRESH_MS = 20 * 60 * 60_000L;
    /** A failed try (no network, TV half asleep) is not repeated sooner than this. */
    private static final long RETRY_MS = 30 * 60_000L;
    private static final int MAX_FEED = 256 * 1024, MAX_IMAGE = 6 * 1024 * 1024;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private Updater() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The job's turn: a download when one is due. */
    static void tick(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getBoolean("enabled", true) && due(prefs)) run(context, false);
    }

    private static boolean due(SharedPreferences prefs) {
        long now = System.currentTimeMillis();
        long checked = prefs.getLong("checked", 0), tried = prefs.getLong("tried", 0);
        // A clock that was set back (no network time yet) must not block the download for ever.
        if (checked > 0 && checked <= now && now - checked < FRESH_MS) return false;
        return tried > now || now - tried >= RETRY_MS;
    }

    /** One download. force: fetch and write even when the feed still names the picture that is already set. */
    static String run(Context context, boolean force) {
        if (!RUNNING.compareAndSet(false, true)) return CURRENT;
        SharedPreferences prefs = prefs(context);
        try {
            long now = System.currentTimeMillis();
            prefs.edit().putLong("tried", now).apply();
            String json = new String(get(Feed.url(Feed.market(Locale.getDefault())), MAX_FEED), StandardCharsets.UTF_8);
            String base = Feed.imageBase(json);
            String address = Feed.imageUrl(json);
            if (base == null || address == null) return finish(prefs, FAILED);
            if (!force && base.equals(prefs.getString("base", ""))) {
                prefs.edit().putLong("checked", now).apply();
                return finish(prefs, CURRENT);
            }
            byte[] jpeg = darken(get(address, MAX_IMAGE), prefs.getInt("dim", DEFAULT_DIM));
            if (jpeg == null) return finish(prefs, FAILED);
            try (OutputStream out = context.getContentResolver().openOutputStream(TARGET, "w")) {
                if (out == null) return finish(prefs, NO_LAUNCHER);
                out.write(jpeg);
            } catch (SecurityException refused) {
                return finish(prefs, NO_PERMISSION);
            } catch (FileNotFoundException | IllegalArgumentException missing) {
                return finish(prefs, NO_LAUNCHER);
            }
            prefs.edit().putString("base", base).putString("caption", Feed.caption(json))
                    .putLong("checked", now).putLong("updated", now).apply();
            return finish(prefs, UPDATED);
        } catch (IOException offline) {
            Log.w(TAG, "Picture of the day not updated: " + offline);
            return finish(prefs, NO_NETWORK);
        } catch (Exception | OutOfMemoryError failure) {
            Log.w(TAG, "Picture of the day not updated: " + failure);
            return finish(prefs, FAILED);
        } finally {
            RUNNING.set(false);
        }
    }

    private static String finish(SharedPreferences prefs, String status) {
        prefs.edit().putString("status", status).apply();
        return status;
    }

    /**
     * The picture as a JPEG with a black shade of dim percent over all of it, and a little more toward the bottom
     * where the dock sits, painted in so the launcher draws no shade layer of its own. Null when it is not a picture.
     */
    static byte[] darken(byte[] image, int dim) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, options);
        if (options.outWidth < 640 || options.outHeight < 360) return null;
        options.inJustDecodeBounds = false;
        options.inSampleSize = options.outWidth > 2 * 1920 ? 2 : 1;
        options.inMutable = true;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length, options);
        if (bitmap == null) return null;
        try {
            dim = Math.max(0, Math.min(80, dim));
            if (dim > 0) {
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor((dim * 255 / 100) << 24);
                Paint lower = new Paint();
                lower.setShader(new LinearGradient(0, bitmap.getHeight() * 0.55f, 0, bitmap.getHeight(),
                        0x00000000, 0x59000000, Shader.TileMode.CLAMP));
                canvas.drawRect(0, bitmap.getHeight() * 0.55f, bitmap.getWidth(), bitmap.getHeight(), lower);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(512 * 1024);
            return bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out) ? out.toByteArray() : null;
        } finally {
            bitmap.recycle();
        }
    }

    private static byte[] get(String address, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (out.size() + read > limit) throw new IOException("larger than " + limit + " bytes");
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
