package com.kmmaruf.gitnotifier.ui;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.icu.text.DateFormat;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;

import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import androidx.work.WorkInfo;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.backup.CryptoUtils;
import com.kmmaruf.gitnotifier.backup.model.BackupPayload;
import com.kmmaruf.gitnotifier.data.AppDatabase;
import com.kmmaruf.gitnotifier.data.entity.CommitEntity;
import com.kmmaruf.gitnotifier.data.entity.ReleaseEntity;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;
import com.kmmaruf.gitnotifier.databinding.ActivityMainBinding;
import com.kmmaruf.gitnotifier.network.ApiClient;
import com.kmmaruf.gitnotifier.network.NetworkUtils;
import com.kmmaruf.gitnotifier.network.model.RateLimitResponse;
import com.kmmaruf.gitnotifier.ui.adapter.RepoAdapter;
import com.kmmaruf.gitnotifier.ui.common.Common;
import com.kmmaruf.gitnotifier.ui.common.Keys;
import com.kmmaruf.gitnotifier.ui.common.Utils;
import com.kmmaruf.gitnotifier.ui.viewmodel.RepoViewModel;
import com.kmmaruf.gitnotifier.worker.RefreshScheduler;

import com.kmmaruf.gitnotifier.worker.SyncTracker;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    public interface OnPasswordEntered {
        void onPasswordProvided(String password);
    }

    private LiveData<List<RepoEntity>> repoLiveData;
    private ActivityMainBinding binding;
    private RepoViewModel viewModel;
    private RepoAdapter repoAdapter;
    private MenuItem infoMenuItem;

    private boolean lastSortedValue;
    private int currentRepoCount;

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;


    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.topAppBar);
        binding.topAppBar.setSubtitle(null);

        // Enlarge navigation icon and give it left breathing room
        binding.topAppBar.post(() -> {
            float density = getResources().getDisplayMetrics().density;
            int sizePx = (int) (36 * density);
            int leftMarginPx = (int) (18 * density);
            for (int i = 0; i < binding.topAppBar.getChildCount(); i++) {
                View child = binding.topAppBar.getChildAt(i);
                if (child instanceof android.widget.ImageButton) {
                    ViewGroup.MarginLayoutParams lp =
                            (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                    lp.width = sizePx;
                    lp.height = sizePx;
                    lp.setMarginStart(leftMarginPx);
                    child.setLayoutParams(lp);
                    child.setPadding(0, 0, 0, 0);
                    break;
                }
            }
        });

        requestNotificationPermission();


        Utils.syncStatusBarColorWithActionBar(this, Utils.resolveThemeColor(this, com.google.android.material.R.attr.colorSurface));

        viewModel = new ViewModelProvider(this).get(RepoViewModel.class);
        repoAdapter = new RepoAdapter(this::onRepoClicked, new RepoAdapter.RepoActionHandler() {
            @Override
            public void onUpdateSwitchValue(RepoEntity r, boolean isEnabled) {
                viewModel.updateEnableState(r.id, isEnabled);
            }

            @Override
            public void onMarkCommitsRead(RepoEntity r) {
                viewModel.markCommitsRead(r.id);
            }

            @Override
            public void onMarkReleasesRead(RepoEntity r) {
                viewModel.markReleasesRead(r.id);
            }
        });

        binding.recyclerRepos.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRepos.setAdapter(repoAdapter);

        binding.fabAdd.setOnClickListener(v -> AddRepoDialog.show(this, null, repo -> viewModel.insert(repo)));

        View btnEmptyAdd = findViewById(R.id.btnEmptyAdd);
        if (btnEmptyAdd != null) {
            btnEmptyAdd.setOnClickListener(v -> AddRepoDialog.show(this, null, repo -> viewModel.insert(repo)));
        }
        binding.swipeRefresh.setOnRefreshListener(() -> {
            Common.showYesNoDialog(this, getString(R.string.confirm_refresh_all), getString(R.string.do_you_want_to_sync_all_repositories), (dialog, which) -> {
                if (!NetworkUtils.isInternetAvailable(MainActivity.this)) {
                    binding.swipeRefresh.setRefreshing(false);
                    Toast.makeText(MainActivity.this, R.string.failed_no_internet_connection, Toast.LENGTH_LONG).show();
                    return;
                }
                // Each repo may use up to 2 API calls (commits + releases)
                int required = Math.max(1, currentRepoCount * 2);
                String rateError = Utils.checkRateLimitBeforeSync(MainActivity.this, required);
                if (rateError != null) {
                    binding.swipeRefresh.setRefreshing(false);
                    Common.showOkayDialog(MainActivity.this, getString(R.string.rate_limit_exceeded_title), rateError);
                    return;
                }
                UUID workId = viewModel.refreshAllImmediately();
                viewModel.trackWork(workId);
            }, (dialog, which) -> binding.swipeRefresh.setRefreshing(false));
        });

        lastSortedValue = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Keys.PREFS_KEY_UNREAD_TOP, true);

        observeLiveData();

        SyncTracker.getActiveRepoId().observe(this, activeId -> {
            repoAdapter.setActiveRepoId(activeId);
        });

        viewModel.getWorkInfo(this).observe(this, workInfo -> {
            if (workInfo != null && workInfo.getState().isFinished()) {
                binding.swipeRefresh.setRefreshing(false);
                if (workInfo.getState() == WorkInfo.State.FAILED) {
                    Common.showOkayDialog(this, "Failed! ", workInfo.getOutputData().getString("error"));
                }
            }
        });

    }


    private void observeLiveData() {
        if (repoAdapter != null) {
            if (repoLiveData != null) {
                repoLiveData.removeObservers(this);
            }
            repoLiveData = viewModel.getAll(lastSortedValue);
            repoLiveData.observe(this, this::updateAdapterAndTitle);
        }
    }

    private void updateAdapterAndTitle(List<RepoEntity> repos) {
        repoAdapter.submitList(repos);
        int unread = 0;
        int disabled = 0;
        for (RepoEntity repoEntity : repos) {
            if (repoEntity.unreadCommitsCount > 0 || repoEntity.unreadReleaseCount > 0) {
                unread++;
            }
            if (!repoEntity.enabled) {
                disabled++;
            }
        }
        currentRepoCount = repos.size();

        // Empty state
        boolean isEmpty = repos == null || repos.isEmpty();
        View empty = findViewById(R.id.emptyState);
        if (empty != null) {
            empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        binding.recyclerRepos.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.fabAdd.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        if (binding.tvStatRepos != null) {
            binding.tvStatRepos.setText(String.valueOf(currentRepoCount));
        }
        if (binding.tvStatUnread != null) {
            binding.tvStatUnread.setText(String.valueOf(unread));
            // Emphasize when there is something to read
            int unreadColor = unread > 0
                    ? Utils.resolveThemeColor(this, com.google.android.material.R.attr.colorPrimary)
                    : Utils.resolveThemeColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
            binding.tvStatUnread.setTextColor(unreadColor);
        }
        if (binding.tvStatDisabled != null) {
            binding.tvStatDisabled.setText(String.valueOf(disabled));
        }
    }

    private void onRepoClicked(RepoEntity repo, int option) {
        if (option == 1) {
            AddRepoDialog.show(this, repo, viewModel::update);
        } else if (option == 2) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.delete_repository).setMessage(R.string.are_you_sure_you_want_to_delete_this_repository).setPositiveButton(R.string.delete, (dialog, which) -> {
                viewModel.delete(repo);
            }).setNegativeButton(R.string.cancel, (dialog, which) -> {
                dialog.dismiss();
            }).show();
        } else if (option == 3) {
            if (!NetworkUtils.isInternetAvailable(this)) {
                Toast.makeText(this, R.string.failed_no_internet_connection, Toast.LENGTH_LONG).show();
                return;
            }
            // Single repo: up to 2 calls (commits + releases)
            String rateError = Utils.checkRateLimitBeforeSync(this, 2);
            if (rateError != null) {
                Common.showOkayDialog(this, getString(R.string.rate_limit_exceeded_title), rateError);
                return;
            }
            UUID workId = viewModel.refreshSingle(repo.id);
            viewModel.trackWork(workId);
            Snackbar.make(binding.getRoot(), getString(R.string.checking) + repo.fullName, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.notifications_permission_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.notifications_permission_denied_notification_for_new_updates_might_not_be_shown, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        infoMenuItem = menu.findItem(R.id.action_information);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean backgroundCheck = prefs.getBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false);

        if (backgroundCheck) {
            MenuItem menuItem = menu.findItem(R.id.action_start_stop);
            menuItem.setTitle(R.string.stop_background_service);
            menuItem.setIcon(R.drawable.ic_stop);
        } else {
            MenuItem menuItem = menu.findItem(R.id.action_start_stop);
            menuItem.setTitle(R.string.start_background_service);
            menuItem.setIcon(R.drawable.ic_play);
        }

        animateInfoButton();
        return true;
    }

    private final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            boolean newSorted = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Keys.PREFS_KEY_UNREAD_TOP, true);
            if (newSorted != lastSortedValue) {
                lastSortedValue = newSorted;
                observeLiveData();
            }
        }
    });

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                Uri fileUri = data.getData();
                Common.showPasswordPrompt(this, getString(R.string.enter_backup_password), password -> {
                    restoreEncryptedBackup(this, fileUri, password);
                });
            }
        }
    });

    private final ActivityResultLauncher<Intent> folderPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Uri treeUri = result.getData().getData();
            if (treeUri != null) {
                Common.showPasswordPrompt(this, getString(R.string.enter_password), password -> {
                    backupEncryptedToLocal(this, treeUri, password);
                });
            }
        }
    });

    private String formatResetTime(String epochSecondsString) {
        try {
            long epochSeconds = Long.parseLong(epochSecondsString);
            Date resetTime = new Date(epochSeconds * 1000L);
            DateFormat format = DateFormat.getTimeInstance(DateFormat.SHORT);
            return format.format(resetTime);
        } catch (Exception e) {
            Common.showOkayDialog(this, e.getMessage(), getString(R.string.error));
            return getString(R.string.unknown);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            AddRepoDialog.show(this, null, repo -> viewModel.insert(repo));
            return true;

        } else if (item.getItemId() == R.id.action_information) {
            View popupView = LayoutInflater.from(this).inflate(R.layout.rate_detail_popup, null);

            TextView tvAu = popupView.findViewById(R.id.tvUserState);
            TextView tvL = popupView.findViewById(R.id.tvLimit);
            TextView tvR = popupView.findViewById(R.id.tvRemaining);
            TextView tvU = popupView.findViewById(R.id.tvUsed);
            TextView tvT = popupView.findViewById(R.id.tvReset);
            TextView tvS = popupView.findViewById(R.id.tvResource);
            TextView tvUp = popupView.findViewById(R.id.tvUpdated);
            TextView tvNs = popupView.findViewById(R.id.tvNextSchedule);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            boolean isEnabled = prefs.getBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false);
            String token = prefs.getString(Keys.PREFS_KEY_TOKEN, null);
            String limit = prefs.getString(Keys.PREFS_KEY_RATE_LIMIT, "--");
            String remaining = prefs.getString(Keys.PREFS_KEY_RATE_REMAINING, "--");
            String used = prefs.getString(Keys.PREFS_KEY_RATE_USED, "--");
            String reset = prefs.getString(Keys.PREFS_KEY_RATE_RESET, "--");
            String resource = prefs.getString(Keys.PREFS_KEY_RATE_RESOURCE, "--");
            String lastUpdated = "";

            if (!reset.equals("--")) {
                reset = formatResetTime(reset);
            }

            long lastCheckedTime = prefs.getLong(Keys.PREFS_KEY_LAST_UPDATE_TIME, 0);
            if (lastCheckedTime == 0) {
                lastUpdated = "Never";
            } else {

                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
                String formattedDate = sdf.format(new Date(lastCheckedTime));

                lastUpdated = formattedDate;
            }

            // Fill with last known values
            tvAu.setText(getString(R.string.user) + (token == null || token == "" ? getString(R.string.unauthenticated) : getString(R.string.authenticated)));
            tvL.setText(getString(R.string.limit) + limit);
            tvR.setText(getString(R.string.remaining) + remaining);
            tvU.setText(getString(R.string.used) + used);
            tvT.setText(getString(R.string.reset) + reset);
            tvS.setText(getString(R.string.resource) + resource);
            tvUp.setText(getString(R.string.updated) + lastUpdated);

            if (isEnabled) {
                tvNs.setVisibility(View.VISIBLE);
                long nextSchedule = prefs.getLong(Keys.PREFS_KEY_NEXT_SCHEDULED_TIME, 0);
                if (nextSchedule != 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
                    String formattedDate = sdf.format(new Date(nextSchedule));
                    tvNs.setText(getString(R.string.next_schedule) + formattedDate);
                }
            } else {
                tvNs.setVisibility(View.GONE);
            }

            PopupWindow popup = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

            popup.setElevation(8);

            MaterialToolbar toolbar = findViewById(R.id.topAppBar);
            popup.showAtLocation(toolbar, Gravity.TOP | Gravity.END, 100, toolbar.getHeight() + 50);
            return true;

        } else if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            settingsLauncher.launch(intent);
            return true;
        } else if (item.getItemId() == R.id.action_start_stop) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean backgroundCheck = prefs.getBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false);

            if (backgroundCheck) {
                RefreshScheduler.cancelScheduledPeriodic(this);
                prefs.edit().putBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false).apply();
                item.setTitle(R.string.start_background_service);
                item.setIcon(R.drawable.ic_play);
            } else {
                RefreshScheduler.schedulePeriodic(this, true);
                prefs.edit().putBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, true).apply();
                item.setTitle(R.string.stop_background_service);
                item.setIcon(R.drawable.ic_stop);
            }
} else if (item.getItemId() == R.id.action_backup) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            folderPickerLauncher.launch(intent);

        } else if (item.getItemId() == R.id.action_restore) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            filePickerLauncher.launch(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean backgroundCheck = prefs.getBoolean(Keys.PREFS_KEY_BACKGROUND_CHECK, false);

        if (backgroundCheck) {
            MenuItem menuItem = menu.findItem(R.id.action_start_stop);
            menuItem.setTitle("Stop background service");
            menuItem.setIcon(R.drawable.ic_stop);
        } else {
            MenuItem menuItem = menu.findItem(R.id.action_start_stop);
            menuItem.setTitle("Start background service");
            menuItem.setIcon(R.drawable.ic_play);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (menu != null && menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                Method method = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", boolean.class);
                method.setAccessible(true);
                method.invoke(menu, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRateLimitFromApi();
        animateInfoButton();
    }

    /**
     * When a token is set (e.g. user just returned from Settings), query
     * GET /rate_limit so the info popup and toolbar icon reflect the
     * authenticated quota instead of stale unauthenticated numbers.
     */
    private void refreshRateLimitFromApi() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String token = prefs.getString(Keys.PREFS_KEY_TOKEN, null);
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        if (!NetworkUtils.isInternetAvailable(this)) {
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                retrofit2.Response<RateLimitResponse> response =
                        ApiClient.getApi(this).getRateLimit().execute();
                if (!response.isSuccessful() || response.body() == null
                        || response.body().resources == null
                        || response.body().resources.core == null) {
                    return;
                }
                RateLimitResponse.Core core = response.body().resources.core;
                prefs.edit()
                        .putString(Keys.PREFS_KEY_RATE_LIMIT, String.valueOf(core.limit))
                        .putString(Keys.PREFS_KEY_RATE_REMAINING, String.valueOf(core.remaining))
                        .putString(Keys.PREFS_KEY_RATE_USED, String.valueOf(core.used))
                        .putString(Keys.PREFS_KEY_RATE_RESET, String.valueOf(core.reset))
                        .putString(Keys.PREFS_KEY_RATE_RESOURCE, "core")
                        .putLong(Keys.PREFS_KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
                        .apply();
                runOnUiThread(this::animateInfoButton);
            } catch (Exception ignored) {
                // Keep previous stored values on failure
            }
        });
    }

    private void animateInfoButton() {
        if (infoMenuItem != null && infoMenuItem.getIcon() != null) {
            Drawable icon = infoMenuItem.getIcon().mutate();

            int remainToken = 0;
            try {
                String rem = PreferenceManager.getDefaultSharedPreferences(this)
                        .getString(Keys.PREFS_KEY_RATE_REMAINING, "0");
                if (rem != null && !rem.isEmpty() && !rem.equals("--")) {
                    remainToken = Integer.parseInt(rem.trim());
                }
            } catch (NumberFormatException ignored) {
            }

            if (remainToken < (currentRepoCount * 3)) {
                int fromColor = Utils.resolveThemeColor(this, com.google.android.material.R.attr.colorPrimary);
                int toColor = Color.RED;

                ValueAnimator colorAnim = ValueAnimator.ofArgb(fromColor, toColor);
                colorAnim.setDuration(1000);
                colorAnim.setRepeatMode(ValueAnimator.REVERSE);
                colorAnim.setRepeatCount(3);

                colorAnim.addUpdateListener(anim -> {
                    int animatedColor = (int) anim.getAnimatedValue();
                    icon.setTint(animatedColor);
                });
                colorAnim.start();
            }

        }
    }

    public void backupEncryptedToLocal(Context context, Uri treeUri, String password) {
        AppDatabase db = AppDatabase.getInstance(getApplication());
        new Thread(() -> {
            List<RepoEntity> repos = db.repoDao().getAllSync();
            List<ReleaseEntity> releases = db.releaseDao().getAllSync();
            List<CommitEntity> commits = db.commitDao().getAllSync();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            // Copy prefs but omit live rate-limit / schedule timestamps (device-specific, stale after restore)
            Map<String, Object> settings = new java.util.HashMap<>();
            for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                if (isRateLimitOrEphemeralPref(e.getKey())) {
                    continue;
                }
                settings.put(e.getKey(), e.getValue());
            }

            BackupPayload payload = new BackupPayload(repos, releases, commits, settings);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(payload);

            try {
                byte[] encrypted = CryptoUtils.encrypt(json, password);
                String timestamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(new Date());
                String filename = "git_notifier_backup_" + timestamp + ".enc";
                DocumentFile pickedDir = DocumentFile.fromTreeUri(context, treeUri);
                DocumentFile backupFile = pickedDir.createFile("application/octet-stream", filename);

                try (OutputStream out = context.getContentResolver().openOutputStream(backupFile.getUri())) {
                    out.write(encrypted);
                    runOnUiThread(() -> {
                        Toast.makeText(context, getString(R.string.backup_saved) + filename, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(context, R.string.backup_failed, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    public void restoreEncryptedBackup(Context context, Uri fileUri, String password) {
        AppDatabase db = AppDatabase.getInstance(getApplication());
        try (InputStream is = context.getContentResolver().openInputStream(fileUri)) {
            byte[] encrypted = Utils.readAllBytes(is);
            String decryptedJson = CryptoUtils.decrypt(encrypted, password);
            BackupPayload payload = new Gson().fromJson(decryptedJson, BackupPayload.class);

            if (payload == null) {
                Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                // Clear existing data to avoid duplicates / ID conflicts
                List<RepoEntity> existing = db.repoDao().getAllSync();
                if (existing != null) {
                    for (RepoEntity repo : existing) {
                        db.commitDao().clearByRepoId(repo.id);
                        db.releaseDao().clearByRepoId(repo.id);
                        db.seenCommitDao().clearByRepoId(repo.id);
                        db.seenReleaseDao().clearByRepoId(repo.id);
                        db.repoDao().delete(repo);
                    }
                }
                db.seenCommitDao().clearAll();
                db.seenReleaseDao().clearAll();

                if (payload.repos != null) {
                    db.repoDao().insertAll(payload.repos);
                }
                if (payload.releases != null) {
                    db.releaseDao().insertAll(payload.releases);
                }
                if (payload.commits != null) {
                    db.commitDao().insertAll(payload.commits);
                }

                // Restore preferences (token, interval, background flag, etc.)
                if (payload.preferences != null && !payload.preferences.isEmpty()) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    SharedPreferences.Editor editor = prefs.edit();
                    for (Map.Entry<String, ?> entry : payload.preferences.entrySet()) {
                        String key = entry.getKey();
                        if (isRateLimitOrEphemeralPref(key)) {
                            continue; // ignore rate limit from old backups too
                        }
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            editor.putString(key, (String) value);
                        } else if (value instanceof Boolean) {
                            editor.putBoolean(key, (Boolean) value);
                        } else if (value instanceof Integer) {
                            editor.putInt(key, (Integer) value);
                        } else if (value instanceof Long) {
                            editor.putLong(key, (Long) value);
                        } else if (value instanceof Float) {
                            editor.putFloat(key, (Float) value);
                        }
                        // skip other types
                    }
                    editor.apply();
                    // Make sure the network client picks up a restored token
                    com.kmmaruf.gitnotifier.network.ApiClient.reset();
                }

                runOnUiThread(() -> {
                    Toast.makeText(context, R.string.restore_complete, Toast.LENGTH_LONG).show();
                    // Force UI refresh
                    observeLiveData();
                });
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** Rate-limit and schedule timestamps must not travel with backup/restore. */
    private static boolean isRateLimitOrEphemeralPref(String key) {
        if (key == null) return true;
        return key.equals(Keys.PREFS_KEY_RATE_LIMIT)
                || key.equals(Keys.PREFS_KEY_RATE_REMAINING)
                || key.equals(Keys.PREFS_KEY_RATE_USED)
                || key.equals(Keys.PREFS_KEY_RATE_RESET)
                || key.equals(Keys.PREFS_KEY_RATE_RESOURCE)
                || key.equals(Keys.PREFS_KEY_LAST_UPDATE_TIME)
                || key.equals(Keys.PREFS_KEY_NEXT_SCHEDULED_TIME);
    }
}
