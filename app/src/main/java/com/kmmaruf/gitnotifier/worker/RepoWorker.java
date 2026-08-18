package com.kmmaruf.gitnotifier.worker;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.kmmaruf.gitnotifier.App;
import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.data.AppDatabase;
import com.kmmaruf.gitnotifier.data.entity.CommitEntity;
import com.kmmaruf.gitnotifier.data.entity.ReleaseEntity;
import com.kmmaruf.gitnotifier.data.entity.SeenCommitEntity;
import com.kmmaruf.gitnotifier.data.entity.SeenReleaseEntity;
import com.kmmaruf.gitnotifier.data.dao.RepoDao;
import com.kmmaruf.gitnotifier.data.dao.SeenCommitDao;
import com.kmmaruf.gitnotifier.data.dao.SeenReleaseDao;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;
import com.kmmaruf.gitnotifier.network.ApiClient;
import com.kmmaruf.gitnotifier.network.GitHubApi;
import com.kmmaruf.gitnotifier.network.model.Commit;
import com.kmmaruf.gitnotifier.network.model.RateLimitInfo;
import com.kmmaruf.gitnotifier.network.model.Release;
import com.kmmaruf.gitnotifier.network.NetworkUtils;
import com.kmmaruf.gitnotifier.ui.DialogListActivity;
import com.kmmaruf.gitnotifier.ui.common.Keys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Headers;
import retrofit2.Response;

/**
 * Background worker that checks GitHub for new commits and releases.
 *
 * Key design decisions (fixes applied):
 * - First-time sync only establishes a baseline (newest SHA / release id).
 *   It does NOT generate notifications or unread badges.
 * - Seen-set of SHAs / release ids (capped) so deleted tips fall back safely.
 * - Subsequent checks only report items until a known id is hit.
 * - Release detection logic is identical for single-repo and all-repos paths.
 * - Token is always read fresh from preferences (via ApiClient).
 */
public class RepoWorker extends Worker {
    private final Context context;
    private final AppDatabase db;
    private final RepoDao repoDao;
    private final SeenCommitDao seenCommitDao;
    private final SeenReleaseDao seenReleaseDao;
    private final GitHubApi api;
    private final NotificationManager nm;

    public RepoWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
        context = ctx;
        db = AppDatabase.getInstance(ctx);
        repoDao = db.repoDao();
        seenCommitDao = db.seenCommitDao();
        seenReleaseDao = db.seenReleaseDao();
        api = ApiClient.getApi(ctx);
        nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!NetworkUtils.hasActiveInternetConnection()) {
            return Result.failure(new Data.Builder()
                    .putString("error", context.getString(R.string.no_active_internet_connection))
                    .build());
        }

        int repoId = getInputData().getInt("key_repo_id", -2);
        RateLimitInfo rateLimitInfo = null;

        try {
            if (repoId != -2) {
                // Single-repo refresh (allowed even if the repo is currently disabled)
                RepoEntity r = repoDao.getRepoEntityById(repoId);
                if (r == null) {
                    return Result.failure(new Data.Builder()
                            .putString("error", context.getString(R.string.entity_not_found_for_repo_id) + repoId)
                            .build());
                }
                SyncTracker.setActiveRepoId(r.id);
                rateLimitInfo = processRepo(r);
                repoDao.updateLastChecked(r.id, System.currentTimeMillis());
            } else {
                // All enabled repos
                List<RepoEntity> repos = repoDao.getAllEnabledSync();
                if (repos == null || repos.isEmpty()) {
                    return Result.failure(new Data.Builder()
                            .putString("error", context.getString(R.string.no_repo_found))
                            .build());
                }

                for (RepoEntity r : repos) {
                    SyncTracker.setActiveRepoId(r.id);
                    RateLimitInfo info = processRepo(r);
                    if (info != null) {
                        rateLimitInfo = info;
                    }
                    repoDao.updateLastChecked(r.id, System.currentTimeMillis());
                }
            }

            if (rateLimitInfo != null) {
                updateRateLimitPreference(rateLimitInfo);
            }

            // Update next scheduled time for the periodic work
            String workName = getInputData().getString("work_name");
            if (workName != null && workName.equals("git_refresh")) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String interval = prefs.getString("pref_interval", "3600000");
                long millis = Long.parseLong(interval);
                prefs.edit().putLong(Keys.PREFS_KEY_NEXT_SCHEDULED_TIME,
                        System.currentTimeMillis() + millis).apply();
            }

            SyncTracker.clear();
            return Result.success();

        } catch (IOException e) {
            SyncTracker.clear();
            return Result.failure(new Data.Builder()
                    .putString("error", e.getMessage() != null ? e.getMessage() : "Network error")
                    .build());
        } catch (Exception e) {
            SyncTracker.clear();
            return Result.failure(new Data.Builder()
                    .putString("error", e.getMessage() != null ? e.getMessage() : "Unexpected error")
                    .build());
        }
    }

    /**
     * Process one repository. Returns the latest rate-limit info seen (or null).
     */
    private RateLimitInfo processRepo(RepoEntity r) throws IOException {
        RateLimitInfo rateLimitInfo = null;

        // ----- Commits -----
        if (r.notifyCommits && r.branch != null && !r.branch.isEmpty()) {
            Response<List<Commit>> response = api.listCommits(r.owner, r.name, r.branch, 10).execute();
            if (response.isSuccessful() && response.body() != null) {
                rateLimitInfo = parseRateLimitHeaders(response);
                List<Commit> commits = response.body();
                if (!commits.isEmpty()) {
                    handleNewCommits(r, commits);
                }
            } else if (response.code() == 401 || response.code() == 403) {
                // Auth / rate-limit problems – surface a clear message
                throw new IOException("GitHub API error " + response.code() +
                        (response.message() != null ? ": " + response.message() : "") +
                        ". Check your token or rate limit.");
            }
        }

        // ----- Releases -----
        if (r.notifyReleases) {
            Response<List<Release>> response = api.listReleases(r.owner, r.name).execute();
            if (response.isSuccessful() && response.body() != null) {
                rateLimitInfo = parseRateLimitHeaders(response);
                List<Release> releases = response.body();
                if (!releases.isEmpty()) {
                    handleNewReleases(r, releases);
                }
            } else if (response.code() == 401 || response.code() == 403) {
                throw new IOException("GitHub API error " + response.code() +
                        (response.message() != null ? ": " + response.message() : "") +
                        ". Check your token or rate limit.");
            }
        }

        return rateLimitInfo;
    }

    /** Max stored SHAs per repo (ring buffer of recent history). */
    private static final int SEEN_COMMIT_CAP = 100;
    /** Max stored release ids per repo. */
    private static final int SEEN_RELEASE_CAP = 200;

    /**
     * GitHub returns commits newest-first.
     * Hybrid: stop at the first SHA already in the seen set (or legacy lastCommitSha).
     * If nothing on the page is known (tip deleted + set empty/gap), re-baseline only.
     */
    private void handleNewCommits(RepoEntity r, List<Commit> commits) {
        String newestSha = commits.get(0).sha;
        long now = System.currentTimeMillis();

        Set<String> seen = new HashSet<>(seenCommitDao.getShasForRepo(r.id));
        // Migrate / keep tip field in sync with the set
        if (r.lastCommitSha != null && !r.lastCommitSha.isEmpty()) {
            seen.add(r.lastCommitSha);
        }

        // First-time baseline: mark entire page as seen, no notifications
        if (seen.isEmpty()) {
            markCommitsSeen(r.id, commits, now);
            r.lastCommitSha = newestSha;
            repoDao.update(r);
            return;
        }

        // Already up-to-date (newest tip already known)
        if (seen.contains(newestSha)) {
            r.lastCommitSha = newestSha;
            repoDao.update(r);
            return;
        }

        List<Commit> newCommits = new ArrayList<>();
        boolean foundKnown = false;
        for (Commit c : commits) {
            if (seen.contains(c.sha)) {
                foundKnown = true;
                break;
            }
            newCommits.add(c);
        }

        if (!foundKnown) {
            // Tip deleted / history rewrite / huge gap: re-baseline, do not flood
            markCommitsSeen(r.id, commits, now);
            r.lastCommitSha = newestSha;
            repoDao.update(r);
            return;
        }

        if (!newCommits.isEmpty()) {
            markCommitsSeen(r.id, newCommits, now);
            // Also keep the matched older SHAs already in DB
            r.lastCommitSha = newestSha;
            saveCommitsToDb(r, newCommits);
            r.unreadCommitsCount += newCommits.size();
            repoDao.update(r);
            notifyCommits(r, newCommits);
        }
    }

    private void markCommitsSeen(int repoId, List<Commit> commits, long now) {
        List<SeenCommitEntity> batch = new ArrayList<>();
        for (Commit c : commits) {
            if (c.sha != null && !c.sha.isEmpty()) {
                batch.add(new SeenCommitEntity(repoId, c.sha, now));
            }
        }
        if (!batch.isEmpty()) {
            seenCommitDao.insertAll(batch);
            pruneSeenCommits(repoId);
        }
    }

    private void pruneSeenCommits(int repoId) {
        int count = seenCommitDao.countForRepo(repoId);
        if (count > SEEN_COMMIT_CAP) {
            List<String> oldest = seenCommitDao.getOldestShas(repoId, count - SEEN_COMMIT_CAP);
            if (oldest != null && !oldest.isEmpty()) {
                seenCommitDao.deleteShas(repoId, oldest);
            }
        }
    }

    /**
     * GitHub returns releases newest-first.
     * Hybrid: stop at first release id already in the seen set (or legacy lastReleaseId).
     * If none on the page are known, re-baseline only (no flood).
     */
    private void handleNewReleases(RepoEntity r, List<Release> releases) {
        long newestId = releases.get(0).id;
        long now = System.currentTimeMillis();

        Set<Long> seen = new HashSet<>(seenReleaseDao.getIdsForRepo(r.id));
        if (r.lastReleaseId > 0) {
            seen.add(r.lastReleaseId);
        }

        // First-time baseline
        if (seen.isEmpty()) {
            markReleasesSeen(r.id, releases, now);
            r.lastReleaseId = newestId;
            repoDao.update(r);
            return;
        }

        if (seen.contains(newestId)) {
            r.lastReleaseId = newestId;
            repoDao.update(r);
            return;
        }

        List<Release> newReleases = new ArrayList<>();
        boolean foundKnown = false;
        for (Release rel : releases) {
            if (seen.contains(rel.id)) {
                foundKnown = true;
                break;
            }
            newReleases.add(rel);
        }

        if (!foundKnown) {
            markReleasesSeen(r.id, releases, now);
            r.lastReleaseId = newestId;
            repoDao.update(r);
            return;
        }

        if (!newReleases.isEmpty()) {
            markReleasesSeen(r.id, newReleases, now);
            r.lastReleaseId = newestId;
            saveReleasesToDb(r, newReleases);
            r.unreadReleaseCount += newReleases.size();
            repoDao.update(r);
            notifyReleases(r, newReleases);
        }
    }

    private void markReleasesSeen(int repoId, List<Release> releases, long now) {
        List<SeenReleaseEntity> batch = new ArrayList<>();
        for (Release rel : releases) {
            batch.add(new SeenReleaseEntity(repoId, rel.id, now));
        }
        if (!batch.isEmpty()) {
            seenReleaseDao.insertAll(batch);
            pruneSeenReleases(repoId);
        }
    }

    private void pruneSeenReleases(int repoId) {
        int count = seenReleaseDao.countForRepo(repoId);
        if (count > SEEN_RELEASE_CAP) {
            List<Long> oldest = seenReleaseDao.getOldestIds(repoId, count - SEEN_RELEASE_CAP);
            if (oldest != null && !oldest.isEmpty()) {
                seenReleaseDao.deleteIds(repoId, oldest);
            }
        }
    }

    private void updateRateLimitPreference(RateLimitInfo info) {
        if (info == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Keys.PREFS_KEY_RATE_LIMIT, info.limit);
        editor.putString(Keys.PREFS_KEY_RATE_REMAINING, info.remaining);
        editor.putString(Keys.PREFS_KEY_RATE_USED, info.used);
        editor.putString(Keys.PREFS_KEY_RATE_RESET, info.reset);
        editor.putString(Keys.PREFS_KEY_RATE_RESOURCE, info.resource);
        editor.putLong(Keys.PREFS_KEY_LAST_UPDATE_TIME, System.currentTimeMillis());
        editor.apply();
    }

    public void saveCommitsToDb(RepoEntity repo, List<Commit> commits) {
        if (commits == null || commits.isEmpty()) return;

        List<CommitEntity> entities = new ArrayList<>();
        for (Commit c : commits) {
            CommitEntity ce = new CommitEntity();
            ce.repoId = repo.id;
            ce.repoFullName = repo.fullName;
            ce.sha = c.sha;
            ce.message = (c.commit != null) ? c.commit.message : "";
            ce.authorDate = (c.commit != null && c.commit.author != null) ? c.commit.author.date : "";
            ce.htmlUrl = c.html_url;
            ce.timestamp = System.currentTimeMillis();
            entities.add(ce);
        }
        db.commitDao().insertAll(entities);
    }

    public void saveReleasesToDb(RepoEntity repo, List<Release> releases) {
        if (releases == null || releases.isEmpty()) return;

        List<ReleaseEntity> entities = new ArrayList<>();
        for (Release r : releases) {
            ReleaseEntity re = new ReleaseEntity();
            re.repoId = repo.id;
            re.repoFullName = repo.fullName;
            re.name = r.name;
            re.tagName = r.tag_name;
            re.body = r.body;
            re.htmlUrl = r.html_url;
            re.timestamp = System.currentTimeMillis();
            entities.add(re);
        }
        db.releaseDao().insertAll(entities);
    }

    private void notifyCommits(RepoEntity r, List<Commit> newCommits) {
        Intent intent = new Intent(getApplicationContext(), DialogListActivity.class);
        intent.putExtra("repo_name", r.fullName);
        intent.putExtra("branch_name", r.branch);
        intent.putExtra("repo_id", r.id);
        intent.putExtra("type", "commit");

        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                r.id + 4000, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = r.fullName + " : " + r.branch;
        NotificationCompat.Builder nb = new NotificationCompat.Builder(getApplicationContext(), App.CHANNEL_COMMITS)
                .setSmallIcon(R.drawable.ic_commit)
                .setContentTitle(title)
                .setContentText(newCommits.size() + " new commit" + (newCommits.size() > 1 ? "s" : ""))
                .setContentIntent(pi)
                .setAutoCancel(true);

        nm.notify(r.id + 4000, nb.build());
    }

    private void notifyReleases(RepoEntity r, List<Release> newReleases) {
        Intent intent = new Intent(getApplicationContext(), DialogListActivity.class);
        intent.putExtra("repo_name", r.fullName);
        intent.putExtra("repo_id", r.id);
        intent.putExtra("type", "release");

        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                r.id + 5000, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = r.fullName;
        NotificationCompat.Builder nb = new NotificationCompat.Builder(getApplicationContext(), App.CHANNEL_RELEASES)
                .setSmallIcon(R.drawable.ic_release)
                .setContentTitle(title)
                .setContentText(newReleases.size() + " new release" + (newReleases.size() > 1 ? "s" : ""))
                .setContentIntent(pi)
                .setAutoCancel(true);

        nm.notify(r.id + 5000, nb.build());
    }

    private RateLimitInfo parseRateLimitHeaders(Response<?> response) {
        Headers headers = response.headers();
        RateLimitInfo rate = new RateLimitInfo();
        rate.limit = headers.get("X-RateLimit-Limit");
        rate.remaining = headers.get("X-RateLimit-Remaining");
        rate.used = headers.get("X-RateLimit-Used");
        rate.reset = headers.get("X-RateLimit-Reset");
        rate.resource = headers.get("X-RateLimit-Resource");
        return rate;
    }
}
