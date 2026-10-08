package com.kmmaruf.gitnotifier.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.data.AppDatabase;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;
import com.kmmaruf.gitnotifier.worker.RefreshScheduler;

import java.util.concurrent.Executors;

/**
 * Lightweight dialog-only entry for share / open-with GitHub links.
 * Does not show the main app UI.
 */
public class ShareAddRepoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String raw = extractRawFromIntent(getIntent());
        if (raw == null || raw.trim().isEmpty()) {
            Toast.makeText(this, R.string.not_a_github_repo_link, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String repoUrl = MainActivity.extractGithubRepoUrl(raw.trim());
        if (repoUrl == null) {
            Toast.makeText(this, R.string.not_a_github_repo_link, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        AddRepoDialog.showWithUrl(this, repoUrl, this::saveAndFinish, this::finish);
    }

    private void saveAndFinish(RepoEntity r) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            int repoId = (int) db.repoDao().insert(r);
            RefreshScheduler.scheduleOneTime(getApplicationContext(), repoId);
            runOnUiThread(this::finish);
        });
    }

    private static String extractRawFromIntent(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            String type = intent.getType();
            if (type != null && type.startsWith("text/")) {
                return intent.getStringExtra(Intent.EXTRA_TEXT);
            }
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            return intent.getData().toString();
        }
        return null;
    }
}
