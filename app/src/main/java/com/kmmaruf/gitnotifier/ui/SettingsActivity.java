package com.kmmaruf.gitnotifier.ui;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.RestrictTo;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.ui.common.Keys;
import com.kmmaruf.gitnotifier.ui.common.Utils;
import com.kmmaruf.gitnotifier.worker.RefreshScheduler;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.settings_activity);

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);

        Utils.syncStatusBarColorWithActionBar(this, R.color.md_theme_surfaceBright);

        getSupportActionBar().setTitle(R.string.settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setResult(RESULT_OK);
                finish();
            }
        });


        getSupportFragmentManager().beginTransaction().replace(R.id.settings, new PrefsFragment()).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class PrefsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreatePreferences(Bundle s, String k) {
            setPreferencesFromResource(R.xml.preferences, k);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            if (key.equals("pref_interval")) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                boolean backgroundCheck = prefs.getBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false);
                if (backgroundCheck) RefreshScheduler.schedulePeriodic(getContext(), true);
            }
        }

        @SuppressLint("RestrictedApi")
        @Override
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        protected void onBindPreferences() {
            super.onBindPreferences();
            EditTextPreference tokenPref = findPreference(Keys.PREFS_KEY_TOKEN);
            if (tokenPref != null) {
                tokenPref.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                });

                tokenPref.setSummaryProvider(preference -> {
                    String value = ((EditTextPreference) preference).getText();
                    return value == null || value.isEmpty() ? getString(R.string.set_your_github_token) : "ghp_••••••";
                });
            }
        }

    }
}
