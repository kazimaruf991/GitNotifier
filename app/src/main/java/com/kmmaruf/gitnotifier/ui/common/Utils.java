package com.kmmaruf.gitnotifier.ui.common;

import android.graphics.drawable.ColorDrawable;
import android.icu.text.SimpleDateFormat;
import android.icu.util.TimeZone;
import android.net.ParseException;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class Utils {

    public static void syncStatusBarColorWithActionBar(AppCompatActivity activity, int colorResId) {

        int color = ContextCompat.getColor(activity, colorResId);
        activity.getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color));

        Window window = activity.getWindow();
        window.setStatusBarColor(ContextCompat.getColor(activity, colorResId));

        int nightModeFlags = activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;

        boolean isNightMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController insetsController = window.getInsetsController();
            if (insetsController != null) {
                insetsController.setSystemBarsAppearance(isNightMode ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
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
        try {
            // Parse ISO 8601 date-time in UTC
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = isoFormat.parse(isoDateTime);

            // Convert to desired format in Bangladesh Standard Time (BST is UTC+6)
            SimpleDateFormat desiredFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm a");
            desiredFormat.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));

            return desiredFormat.format(date);

        } catch (ParseException e) {
            e.printStackTrace();
            return "Invalid date format";
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        }
    }
}