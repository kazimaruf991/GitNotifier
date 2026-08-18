package com.kmmaruf.gitnotifier.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.icu.text.DateFormat;
import android.icu.text.SimpleDateFormat;
import android.icu.util.TimeZone;
import android.net.ParseException;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.kmmaruf.gitnotifier.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Utils {

    public static void syncStatusBarColorWithActionBar(AppCompatActivity activity, int color) {
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color));
        }

        Window window = activity.getWindow();
        window.setStatusBarColor(color);

        int nightModeFlags = activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isNightMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController insetsController = window.getInsetsController();
            if (insetsController != null) {
                insetsController.setSystemBarsAppearance(
                        isNightMode ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            if (!isNightMode) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decorView.setSystemUiVisibility(flags);
        }
    }

    public static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    public static String convertUtcToLocal(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty()) {
            return "";
        }
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = isoFormat.parse(isoDateTime);

            SimpleDateFormat desiredFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm a");
            desiredFormat.setTimeZone(TimeZone.getDefault());
            return desiredFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return "Invalid date format";
        } catch (java.text.ParseException e) {
            try {
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = isoFormat.parse(isoDateTime);
                SimpleDateFormat desiredFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm a");
                desiredFormat.setTimeZone(TimeZone.getDefault());
                return desiredFormat.format(date);
            } catch (Exception ex) {
                return isoDateTime;
            }
        }
    }

    public static String formatRelativeTime(long timestampMs, Context context) {
        if (timestampMs <= 0) {
            return context.getString(R.string.last_checked_none);
        }
        long now = System.currentTimeMillis();
        long diff = now - timestampMs;

        if (diff < TimeUnit.MINUTES.toMillis(1)) {
            return context.getString(R.string.last_checked) + context.getString(R.string.just_now);
        } else if (diff < TimeUnit.HOURS.toMillis(1)) {
            long mins = TimeUnit.MILLISECONDS.toMinutes(diff);
            return context.getString(R.string.last_checked)
                    + context.getString(R.string.minutes_ago, (int) mins);
        } else if (diff < TimeUnit.DAYS.toMillis(1)) {
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            return context.getString(R.string.last_checked)
                    + context.getString(R.string.hours_ago, (int) hours);
        } else if (diff < TimeUnit.DAYS.toMillis(7)) {
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            return context.getString(R.string.last_checked)
                    + context.getString(R.string.days_ago, (int) days);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            return context.getString(R.string.last_checked) + sdf.format(new Date(timestampMs));
        }
    }

    /** Remaining GitHub API requests from last known headers, or -1 if unknown. */
    public static int getRateLimitRemaining(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String remaining = prefs.getString(Keys.PREFS_KEY_RATE_REMAINING, null);
        if (remaining == null || remaining.isEmpty() || remaining.equals("--")) {
            return -1;
        }
        try {
            return Integer.parseInt(remaining.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Human-readable reset time from stored epoch seconds, or null if unknown. */
    public static String getRateLimitResetTime(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String reset = prefs.getString(Keys.PREFS_KEY_RATE_RESET, null);
        if (reset == null || reset.isEmpty() || reset.equals("--")) {
            return null;
        }
        try {
            long epochSeconds = Long.parseLong(reset.trim());
            DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
            return format.format(new Date(epochSeconds * 1000L));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param requiredApprox minimum requests this operation is expected to use
     * @return null if OK to proceed, otherwise a user-facing error message
     */
    public static String checkRateLimitBeforeSync(Context context, int requiredApprox) {
        int remaining = getRateLimitRemaining(context);
        if (remaining < 0) {
            // Unknown – allow sync; worker will surface API errors
            return null;
        }
        if (remaining <= 0) {
            String resetAt = getRateLimitResetTime(context);
            if (resetAt != null) {
                return context.getString(R.string.rate_limit_exceeded_message, resetAt);
            }
            return context.getString(R.string.rate_limit_exceeded_message_unknown);
        }
        if (remaining < requiredApprox) {
            String resetAt = getRateLimitResetTime(context);
            if (resetAt == null) {
                resetAt = context.getString(R.string.unknown);
            }
            return context.getString(R.string.rate_limit_insufficient_message,
                    remaining, requiredApprox, resetAt);
        }
        return null;
    }

    /** Resolve a theme attribute (e.g. colorPrimary) to a color int. */
    public static int resolveThemeColor(Context context, int attrResId) {
        android.util.TypedValue tv = new android.util.TypedValue();
        context.getTheme().resolveAttribute(attrResId, tv, true);
        if (tv.resourceId != 0) {
            return ContextCompat.getColor(context, tv.resourceId);
        }
        return tv.data;
    }
}
