package com.kmmaruf.gitnotifier.ui;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.icu.util.TimeZone;
import android.net.ParseException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.data.AppDatabase;
import com.kmmaruf.gitnotifier.data.entity.CommitEntity;
import com.kmmaruf.gitnotifier.data.entity.ReleaseEntity;
import com.kmmaruf.gitnotifier.databinding.DialogListBinding;
import com.kmmaruf.gitnotifier.ui.common.Utils;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

public class DialogListActivity extends AppCompatActivity {
    AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = AppDatabase.getInstance(getApplicationContext());
        setTitle("");

        String repoName = getIntent().getStringExtra("repo_name");
        String branchName = getIntent().getStringExtra("branch_name");
        int repoId = getIntent().getIntExtra("repo_id", -2);
        if (repoId == -2) {
            finish();
            return;
        }

        String type = getIntent().getStringExtra("type");
        if (type.equals("commit")) {
            new Thread(() -> {
                List<CommitEntity> commits = db.commitDao().getAllByRepoId(repoId);
                runOnUiThread(() -> {
                    DialogListBinding b = DialogListBinding.inflate(getLayoutInflater());
                    b.rvItems.setLayoutManager(new LinearLayoutManager(this));
                    b.rvItems.setAdapter(new DialogCommitAdapter(commits));

                    AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(repoName + " : " + branchName + " – " + commits.size() + " new commits").setView(b.getRoot()).setNegativeButton("Close", (d, w) -> finish()).setPositiveButton("Mark As Read", (d, w) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            db.repoDao().setUnreadCommitsById(repoId, 0);
                            db.commitDao().clearByRepoId(repoId);
                        });

                    }).setOnDismissListener(d -> finish()).create();

                    dialog.setOnShowListener(dialogInterface -> {
                        ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE)).cancel(repoId + 4000); // Use your actual notification ID
                    });

                    dialog.show();
                });
            }).start();

        } else if (type.equals("release")) {
            new Thread(() -> {
                List<ReleaseEntity> releases = db.releaseDao().getAllByRepoId(repoId);
                runOnUiThread(() -> {
                    DialogListBinding b = DialogListBinding.inflate(getLayoutInflater());
                    b.rvItems.setLayoutManager(new LinearLayoutManager(DialogListActivity.this));
                    b.rvItems.setAdapter(new DialogReleaseAdapter(releases));

                    AlertDialog dialog = new MaterialAlertDialogBuilder(DialogListActivity.this).setTitle(repoName + " – " + releases.size() + " new release").setView(b.getRoot()).setNegativeButton("Close", (d, w) -> finish()).setPositiveButton("Mark As Read", (d, w) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            db.repoDao().setUnreadReleasesById(repoId, 0);
                            db.releaseDao().clearByRepoId(repoId);
                        });
                    }).setOnDismissListener(d -> finish()).create();

                    dialog.setOnShowListener(dialogInterface -> {
                        ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE)).cancel(repoId + 5000); // Use your actual notification ID
                    });

                    dialog.show();
                });
            }).start();
        }
    }

    private class DialogCommitAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<DialogCommitAdapter.VH> {
        List<CommitEntity> list;

        DialogCommitAdapter(List<CommitEntity> l) {
            list = l;
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup p, int v) {
            View vew = getLayoutInflater().inflate(R.layout.item_commit, p, false);
            return new VH(vew);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            CommitEntity c = list.get(pos);
            h.imageViewLeft.setImageResource(R.drawable.ic_commit);
            h.tvMessage.setText(c.message);
            h.tvDate.setText(Utils.convertUtcToLocal(c.authorDate));
            h.imageViewRight.setImageResource(R.drawable.ic_web);
            h.imageViewRight.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(c.htmlUrl)));
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView imageViewLeft, imageViewRight;
            android.widget.TextView tvMessage, tvDate;

            VH(View v) {
                super(v);
                imageViewLeft = v.findViewById(R.id.iconLeft);
                imageViewRight = v.findViewById(R.id.iconRight);
                tvMessage = v.findViewById(R.id.tvMessage);
                tvDate = v.findViewById(R.id.tvDate);
            }
        }
    }

    private class DialogReleaseAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<DialogReleaseAdapter.VH> {
        List<ReleaseEntity> list;

        DialogReleaseAdapter(List<ReleaseEntity> l) {
            list = l;
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup p, int v) {
            View vew = getLayoutInflater().inflate(R.layout.item_commit, p, false);
            return new VH(vew);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            ReleaseEntity r = list.get(pos);
            h.imageViewLeft.setImageResource(R.drawable.ic_release);
            h.tvMessage.setText(r.name + " (" + r.tagName + ")");
            h.tvDate.setText(r.body != null ? r.body : "");
            h.imageViewRight.setImageResource(R.drawable.ic_web);
            h.imageViewRight.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.htmlUrl)));
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView imageViewLeft, imageViewRight;
            android.widget.TextView tvMessage, tvDate;

            VH(View v) {
                super(v);
                imageViewLeft = v.findViewById(R.id.iconLeft);
                imageViewRight = v.findViewById(R.id.iconRight);
                tvMessage = v.findViewById(R.id.tvMessage);
                tvDate = v.findViewById(R.id.tvDate);
            }
        }
    }
}


