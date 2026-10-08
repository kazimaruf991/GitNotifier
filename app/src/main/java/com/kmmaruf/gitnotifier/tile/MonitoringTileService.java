package com.kmmaruf.gitnotifier.tile;

import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.ui.common.Keys;
import com.kmmaruf.gitnotifier.worker.RefreshScheduler;

/**
 * Quick Settings tile to toggle background monitoring (periodic WorkManager sync).
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class MonitoringTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false);
        if (enabled) {
            RefreshScheduler.cancelScheduledPeriodic(this);
            prefs.edit().putBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false).apply();
        } else {
            RefreshScheduler.schedulePeriodic(this, true);
            prefs.edit().putBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, true).apply();
        }
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false);
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setLabel(getString(R.string.qs_tile_label));
            tile.setSubtitle(enabled
                    ? getString(R.string.qs_tile_on)
                    : getString(R.string.qs_tile_off));
        } else {
            tile.setLabel(enabled
                    ? getString(R.string.qs_tile_label_on)
                    : getString(R.string.qs_tile_label_off));
        }
        tile.updateTile();
    }
}
