package com.kmmaruf.gitnotifier.ui;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.data.AppDatabase;
import com.kmmaruf.gitnotifier.data.entity.CommitEntity;
import com.kmmaruf.gitnotifier.data.entity.ReleaseEntity;
import com.kmmaruf.gitnotifier.ui.common.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Dialog-themed activity used both from in-app chips and from notification taps.
 * Theme is AppDialogTheme so it appears as a floating dialog without launching MainActivity.
 */
public class DialogListActivity extends AppCompatActivity {

    private AppDatabase db;
    private int repoId;
    private String type;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog_list);

        // Cap dialog height so long lists scroll inside the window
        final View root = findViewById(android.R.id.content);
        root.post(() -> {
            int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.72f);
            View panel = ((ViewGroup) root).getChildAt(0);
            if (panel != null && panel.getHeight() > maxH) {
                ViewGroup.LayoutParams lp = panel.getLayoutParams();
                lp.height = maxH;
                panel.setLayoutParams(lp);
            }
        });

        db = AppDatabase.getInstance(getApplicationContext());

        String repoName = getIntent().getStringExtra("repo_name");
        String branchName = getIntent().getStringExtra("branch_name");
        repoId = getIntent().getIntExtra("repo_id", -2);
        type = getIntent().getStringExtra("type");

        if (repoId == -2 || type == null) {
            finish();
            return;
        }

        ImageView headerIcon = findViewById(R.id.headerIcon);
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvSubtitle = findViewById(R.id.tvSubtitle);
        RecyclerView rvItems = findViewById(R.id.rvItems);
        MaterialButton btnDismiss = findViewById(R.id.btnDismiss);
        MaterialButton btnMarkRead = findViewById(R.id.btnMarkRead);
        ImageButton btnClose = findViewById(R.id.btnClose);

        tvTitle.setText(repoName != null ? repoName : "");
        rvItems.setLayoutManager(new LinearLayoutManager(this));
        rvItems.setHasFixedSize(false);

        btnDismiss.setOnClickListener(v -> finish());
        btnClose.setOnClickListener(v -> finish());

        if ("commit".equals(type)) {
            headerIcon.setImageResource(R.drawable.ic_commit);
            String sub = branchName != null ? branchName : "";
            tvSubtitle.setText(sub);

            Executors.newSingleThreadExecutor().execute(() -> {
                List<CommitEntity> commits = db.commitDao().getAllByRepoId(repoId);
                if (commits == null) commits = new ArrayList<>();
                final List<CommitEntity> finalCommits = commits;
                runOnUiThread(() -> {
                    tvSubtitle.setText(
                            (branchName != null ? branchName + "  ·  " : "") +
                                    getString(R.string.new_commits_count, finalCommits.size()));
                    rvItems.setAdapter(new DialogCommitAdapter(finalCommits));
                    cancelNotification(repoId + 4000);
                });
            });

            btnMarkRead.setOnClickListener(v -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    db.repoDao().setUnreadCommitsById(repoId, 0);
                    db.commitDao().clearByRepoId(repoId);
                    runOnUiThread(this::finish);
                });
            });

        } else if ("release".equals(type)) {
            headerIcon.setImageResource(R.drawable.ic_release);

            Executors.newSingleThreadExecutor().execute(() -> {
                List<ReleaseEntity> releases = db.releaseDao().getAllByRepoId(repoId);
                if (releases == null) releases = new ArrayList<>();
                final List<ReleaseEntity> finalReleases = releases;
                runOnUiThread(() -> {
                    tvSubtitle.setText(getString(R.string.new_releases_count, finalReleases.size()));
                    rvItems.setAdapter(new DialogReleaseAdapter(finalReleases));
                    cancelNotification(repoId + 5000);
                });
            });

            btnMarkRead.setOnClickListener(v -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    db.repoDao().setUnreadReleasesById(repoId, 0);
                    db.releaseDao().clearByRepoId(repoId);
                    runOnUiThread(this::finish);
                });
            });
        } else {
            finish();
        }
    }

    private void cancelNotification(int id) {
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(id);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    // ── Commit adapter ──────────────────────────────────────────────
    private class DialogCommitAdapter extends RecyclerView.Adapter<ItemVH> {
        private final List<CommitEntity> list;

        DialogCommitAdapter(List<CommitEntity> l) {
            list = l;
        }

        @Override
        public ItemVH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_commit, parent, false);
            return new ItemVH(v);
        }

        @Override
        public void onBindViewHolder(ItemVH h, int pos) {
            CommitEntity c = list.get(pos);
            h.iconLeft.setImageResource(R.drawable.ic_commit);
            String msg = c.message != null ? c.message.trim() : "";
            h.tvMessage.setText(msg);
            h.tvDate.setText(Utils.convertUtcToLocal(c.authorDate));
            h.iconRight.setOnClickListener(v -> {
                if (c.htmlUrl != null && !c.htmlUrl.isEmpty()) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(c.htmlUrl)));
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    // ── Release adapter ─────────────────────────────────────────────
    private class DialogReleaseAdapter extends RecyclerView.Adapter<ItemVH> {
        private final List<ReleaseEntity> list;

        DialogReleaseAdapter(List<ReleaseEntity> l) {
            list = l;
        }

        @Override
        public ItemVH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_commit, parent, false);
            return new ItemVH(v);
        }

        @Override
        public void onBindViewHolder(ItemVH h, int pos) {
            ReleaseEntity r = list.get(pos);
            h.iconLeft.setImageResource(R.drawable.ic_release);
            String title = r.name != null && !r.name.isEmpty() ? r.name : r.tagName;
            if (r.tagName != null && r.name != null && !r.name.contains(r.tagName)) {
                title = title + "  (" + r.tagName + ")";
            }
            h.tvMessage.setText(title != null ? title : "");
            String body = r.body != null ? r.body.trim() : "";
            h.tvDate.setText(body);
            h.tvDate.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
            h.iconRight.setOnClickListener(v -> {
                if (r.htmlUrl != null && !r.htmlUrl.isEmpty()) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.htmlUrl)));
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        ImageView iconLeft;
        ImageButton iconRight;
        TextView tvMessage, tvDate;

        ItemVH(View v) {
            super(v);
            iconLeft = v.findViewById(R.id.iconLeft);
            iconRight = v.findViewById(R.id.iconRight);
            tvMessage = v.findViewById(R.id.tvMessage);
            tvDate = v.findViewById(R.id.tvDate);
        }
    }
}
