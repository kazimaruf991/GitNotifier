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
import com.kmmaruf.gitnotifier.data.dao.RepoDao;
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
import java.util.Collections;
import java.util.List;

import okhttp3.Headers;
import retrofit2.Response;

public class RepoWorker extends Worker {
    Context context;
    AppDatabase db;
    private RepoDao repoDao;
    private GitHubApi api;
    private NotificationManager nm;

    public RepoWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
        context = ctx;
        db = AppDatabase.getInstance(ctx);
        repoDao = db.repoDao();
        api = ApiClient.getApi(ctx);
        nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @NonNull
    @Override
    public Result doWork() {

        if (!NetworkUtils.hasActiveInternetConnection()) {
            return Result.failure(new Data.Builder().putString("error", context.getString(R.string.no_active_internet_connection)).build());
        }

        int repoId = getInputData().getInt("key_repo_id", -2);
        if (repoId != -2) {
            RepoEntity r = repoDao.getRepoEntityById(repoId);
            if (r == null) {
                return Result.failure(new Data.Builder().putString("error", context.getString(R.string.entity_not_found_for_repo_id) + repoId).build());
            }

            SyncTracker.setActiveRepoId(r.id);

            try {
                RateLimitInfo rateLimitInfo = null;
                if (r.notifyCommits && r.branch != null) {
                    Response<List<Commit>> response = api.listCommits(r.owner, r.name, r.branch, 10).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        rateLimitInfo = parseRateLimitHeaders(response);
                        List<Commit> commits = response.body();
                        if (!commits.isEmpty()) {
                            // find how many are new since lastCommitSha
                            int cutoff = 0;
                            while (cutoff < commits.size() && !commits.get(cutoff).sha.equals(r.lastCommitSha)) {
                                cutoff++;
                            }
                            if (cutoff > 0) {
                                // update lastCommitSha
                                r.lastCommitSha = commits.get(0).sha;

                                List<Commit> newCommits = commits.subList(0, cutoff);

                                saveCommitsToDb(r, newCommits);
                                r.unreadCommitsCount += newCommits.size();
                                repoDao.update(r);
                                // pass only the new ones
                                notifyCommits(r, newCommits);
                            }
                        }
                    }
                }

                // Check releases
                if (r.notifyReleases) {
                    Response<List<Release>> response = api.listReleases(r.owner, r.name).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        rateLimitInfo = parseRateLimitHeaders(response);
                        List<Release> rels = response.body();
                        if (!rels.isEmpty()) {
                            List<Release> newReleases = new ArrayList<>();

                            if (r.lastReleaseId > 0) {
                                boolean matchedOldReleaseId = false;
                                for (int i = rels.size() - 1; i >= 0; i--) {
                                    Release release = rels.get(i);

                                    if (matchedOldReleaseId) {
                                        newReleases.add(release);
                                    }
                                    if (release.id == r.lastReleaseId) {
                                        matchedOldReleaseId = true;
                                    }
                                }

                                if (!matchedOldReleaseId) {
                                    newReleases = rels;
                                }
                            } else {
                                // First-time sync: treat all as new
                                newReleases = rels;
                            }

                            if (!newReleases.isEmpty()) {
                                r.lastReleaseId = newReleases.get(0).id;
                                saveReleasesToDb(r, newReleases);
                                r.unreadReleaseCount += newReleases.size();
                                repoDao.update(r);
                                notifyReleases(r, newReleases);
                            }
                        }
                    }
                }

                if (rateLimitInfo != null) {
                    updateRateLimitPreference(rateLimitInfo);
                }

                repoDao.updateLastChecked(r.id, System.currentTimeMillis());

            } catch (IOException e) {
                return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
            }
        } else {
            List<RepoEntity> repos = repoDao.getAllSync();   // <-- sync call
            if (repos == null || repos.isEmpty()) {
                return Result.failure(new Data.Builder().putString("error", context.getString(R.string.no_repo_found)).build());
            }

            RateLimitInfo rateLimitInfo = null;

            for (RepoEntity r : repos) {
                if (r.enabled) {

                    SyncTracker.setActiveRepoId(r.id);

                    try {
                        // Check commits
                        // inside the loop, replacing the old count-based logic
                        if (r.notifyCommits && r.branch != null) {
                            Response<List<Commit>> response = api.listCommits(r.owner, r.name, r.branch, 10).execute();
                            if (response.isSuccessful() && response.body() != null) {
                                rateLimitInfo = parseRateLimitHeaders(response);
                                List<Commit> commits = response.body();
                                if (!commits.isEmpty()) {
                                    // find how many are new since lastCommitSha
                                    int cutoff = 0;
                                    while (cutoff < commits.size() && !commits.get(cutoff).sha.equals(r.lastCommitSha)) {
                                        cutoff++;
                                    }
                                    if (cutoff > 0) {
                                        // update lastCommitSha
                                        r.lastCommitSha = commits.get(0).sha;

                                        List<Commit> newCommits = commits.subList(0, cutoff);
                                        saveCommitsToDb(r, newCommits);
                                        r.unreadCommitsCount += newCommits.size();
                                        repoDao.update(r);
                                        // pass only the new ones
                                        notifyCommits(r, newCommits);
                                    }
                                }
                            }
                        }

                        // Check releases
                        if (r.notifyReleases) {
                            Response<List<Release>> response = api.listReleases(r.owner, r.name).execute();
                            if (response.isSuccessful() && response.body() != null) {
                                rateLimitInfo = parseRateLimitHeaders(response);
                                List<Release> rels = response.body();
                                if (!rels.isEmpty()) {
                                    List<Release> newReleases = new ArrayList<>();

                                    if (r.lastReleaseId > 0) {
                                        boolean matchedOldReleaseId = false;
                                        for (int i = rels.size() - 1; i >= 0; i--) {
                                            Release release = rels.get(i);

                                            if (matchedOldReleaseId) {
                                                newReleases.add(release);
                                            }
                                            if (release.id == r.lastReleaseId) {
                                                matchedOldReleaseId = true;
                                            }
                                        }

                                        if (!matchedOldReleaseId) {
                                            newReleases = new ArrayList<>(rels);
                                            Collections.reverse(newReleases);
                                        }
                                    } else {
                                        // First-time sync: treat all as new
                                        newReleases = new ArrayList<>(rels);
                                        Collections.reverse(newReleases);
                                    }

                                    if (!newReleases.isEmpty()) {
                                        r.lastReleaseId = newReleases.get(0).id;
                                        saveReleasesToDb(r, newReleases);
                                        r.unreadReleaseCount += newReleases.size();
                                        repoDao.update(r);
                                        notifyReleases(r, newReleases);
                                    }
                                }
                            }
                        }


                        repoDao.updateLastChecked(r.id, System.currentTimeMillis());

                    } catch (IOException e) {
                        return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
                    }
                }
            }

            if (rateLimitInfo != null) {
                updateRateLimitPreference(rateLimitInfo);
            }

        }

        String workName = getInputData().getString("work_name");
        if (workName != null && workName.equals("git_refresh")) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String interval = prefs.getString("pref_interval", "3600000");
            long millis = Long.parseLong(interval);
            prefs.edit().putLong(Keys.PREFS_KEY_NEXT_SCHEDULED_TIME, System.currentTimeMillis() + millis).apply();
        }
        SyncTracker.clear();
        return Result.success();
    }

    private void updateRateLimitPreference(RateLimitInfo info) {
        if (info != null) {
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
    }

    private int countNew(List<Commit> commits, String lastSha) {
        int count = 0;
        for (Commit c : commits) {
            if (c.sha.equals(lastSha)) break;
            count++;
        }
        return count == 0 ? commits.size() : count;
    }

    public void saveCommitsToDb(RepoEntity repo, List<Commit> commits) {
        if (commits == null || commits.isEmpty()) return;

        List<CommitEntity> entities = new ArrayList<>();
        for (Commit c : commits) {
            CommitEntity ce = new CommitEntity();
            ce.repoId = repo.id;
            ce.repoFullName = repo.fullName;
            ce.sha = c.sha;
            ce.message = c.commit.message;
            ce.authorDate = c.commit.author.date;
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

        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), r.id + 4000, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = r.fullName + " : " + r.branch;
        NotificationCompat.Builder nb = new NotificationCompat.Builder(getApplicationContext(), App.CHANNEL_COMMITS).setSmallIcon(R.drawable.ic_commit).setContentTitle(title).setContentText(newCommits.size() + " new commits").setContentIntent(pi).setAutoCancel(true);

        nm.notify(r.id + 4000, nb.build());
    }


    private void notifyReleases(RepoEntity r, List<Release> newReleases) {
        Intent intent = new Intent(getApplicationContext(), DialogListActivity.class);
        intent.putExtra("repo_name", r.fullName);
        intent.putExtra("repo_id", r.id);
        intent.putExtra("type", "release");

        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), r.id + 5000, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = r.fullName;
        NotificationCompat.Builder nb = new NotificationCompat.Builder(getApplicationContext(), App.CHANNEL_RELEASES).setSmallIcon(R.drawable.ic_release).setContentTitle(title).setContentText(newReleases.size() + " new release").setContentIntent(pi).setAutoCancel(true);

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