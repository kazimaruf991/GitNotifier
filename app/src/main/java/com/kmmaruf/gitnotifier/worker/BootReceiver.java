package com.kmmaruf.gitnotifier.worker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.kmmaruf.gitnotifier.ui.common.Keys;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean backgroundCheck = prefs.getBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false);
            if (backgroundCheck) RefreshScheduler.schedulePeriodic(context, true);
        }
    }
}