package com.kmmaruf.gitnotifier.ui.adapter;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.*;

import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;
import com.kmmaruf.gitnotifier.network.NetworkUtils;
import com.kmmaruf.gitnotifier.ui.DialogListActivity;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class RepoAdapter extends ListAdapter<RepoEntity, RepoAdapter.VH> {

    private Integer activeRepoId = null;
    private final Map<View, ObjectAnimator> animators = new HashMap<>();


    public interface ClickHandler {
        void onClick(RepoEntity r, int option);
    }

    public interface RepoActionHandler {
        void onUpdateSwitchValue(RepoEntity r, boolean isEnabled);
    }

    private final RepoActionHandler actionHandler;
    private final ClickHandler click;

    public RepoAdapter(ClickHandler c, RepoActionHandler h) {
        super(new DiffUtil.ItemCallback<RepoEntity>() {
            public boolean areItemsTheSame(@NonNull RepoEntity a, @NonNull RepoEntity b) {
                return a.id == b.id;
            }

            public boolean areContentsTheSame(@NonNull RepoEntity a, @NonNull RepoEntity b) {
                return a.equals(b);
            }
        });
        actionHandler = h;
        click = c;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        View vew = LayoutInflater.from(p.getContext()).inflate(R.layout.item_repo, p, false);
        return new VH(vew);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {

        RepoEntity r = getItem(pos);
        h.swEnabled.setChecked(r.enabled);
        h.tvName.setText(r.fullName);
        h.tvLink.setText("🔗  " + r.url);
        h.tvSettings.setText((r.notifyCommits ? h.tvSettings.getContext().getString(R.string.notify_on_commits) + r.branch : "") + (r.notifyReleases ? h.tvSettings.getContext().getString(R.string.releases) : ""));
        long lastCheckedTime = r.lastChecked;
        if (lastCheckedTime == 0) {
            h.tvLastChecked.setText(R.string.last_checked_none);
        } else {

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss a", Locale.getDefault());
            String formattedDate = sdf.format(new Date(lastCheckedTime));

            h.tvLastChecked.setText(h.tvLastChecked.getContext().getString(R.string.last_checked) + formattedDate);
        }


        h.swEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> actionHandler.onUpdateSwitchValue(r, isChecked));

        h.btnEdit.setOnClickListener(x -> click.onClick(r, 1));

        h.btnDelete.setOnClickListener(x -> click.onClick(r, 2));

        h.btnBrowse.setOnClickListener(x -> {
            String url = "https://github.com/" + r.owner + "/" + r.name;
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            x.getContext().startActivity(i);
        });

        h.btnRefresh.setOnClickListener(x -> {
            if (!NetworkUtils.isInternetAvailable(x.getContext())) {
                Toast.makeText(x.getContext(), R.string.failed_no_internet_connection, Toast.LENGTH_LONG).show();
            } else {
                click.onClick(r, 3);
            }
        });


        boolean isSyncing = activeRepoId != null && activeRepoId == r.id;

        if (isSyncing) {
            startRotation(h.btnRefresh);
        } else {
            stopRotation(h.btnRefresh);
        }

        int unreadCommitsCount = r.unreadCommitsCount;
        int unreadReleaseCount = r.unreadReleaseCount;

        if (unreadCommitsCount == 0 && unreadReleaseCount == 0) {
            h.layoutUnreadBadgeContainer.setVisibility(View.GONE);
        } else {
            h.layoutUnreadBadgeContainer.setVisibility(View.VISIBLE);
            if (unreadCommitsCount != 0) {
                h.layoutCommitCount.setVisibility(View.VISIBLE);
                h.tvCommitCount.setText(Integer.toString(r.unreadCommitsCount));

                h.tvCommit.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), DialogListActivity.class);
                    intent.putExtra("repo_name", r.fullName);
                    intent.putExtra("branch_name", r.branch);
                    intent.putExtra("repo_id", r.id);
                    intent.putExtra("type", "commit");
                    v.getContext().startActivity(intent);
                });

                h.layoutCommitCount.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), DialogListActivity.class);
                    intent.putExtra("repo_name", r.fullName);
                    intent.putExtra("branch_name", r.branch);
                    intent.putExtra("repo_id", r.id);
                    intent.putExtra("type", "commit");
                    v.getContext().startActivity(intent);
                });
            } else {
                h.layoutCommitCount.setVisibility(View.GONE);
            }

            if (unreadReleaseCount != 0) {
                h.layoutReleaseCount.setVisibility(View.VISIBLE);
                h.tvReleaseCount.setText(Integer.toString(r.unreadReleaseCount));

                h.tvRelease.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), DialogListActivity.class);
                    intent.putExtra("repo_name", r.fullName);
                    intent.putExtra("repo_id", r.id);
                    intent.putExtra("type", "release");
                    v.getContext().startActivity(intent);
                });

                h.layoutReleaseCount.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), DialogListActivity.class);
                    intent.putExtra("repo_name", r.fullName);
                    intent.putExtra("repo_id", r.id);
                    intent.putExtra("type", "release");
                    v.getContext().startActivity(intent);
                });

            } else {
                h.layoutReleaseCount.setVisibility(View.GONE);
            }
        }
    }

    RepoEntity getAt(int pos) {
        return getItem(pos);
    }

    static class VH extends RecyclerView.ViewHolder {
        SwitchCompat swEnabled;
        TextView tvLink, tvName, tvSettings, tvLastChecked;
        ImageButton btnEdit, btnDelete, btnBrowse, btnRefresh;
        LinearLayout layoutUnreadBadgeContainer, layoutCommitCount, layoutReleaseCount;
        TextView tvCommit, tvRelease, tvCommitCount, tvReleaseCount;

        VH(View v) {
            super(v);
            swEnabled = v.findViewById(R.id.switchEnabled);
            tvLink = v.findViewById(R.id.tvLink);
            tvName = v.findViewById(R.id.tvRepoName);
            tvSettings = v.findViewById(R.id.tvSettings);
            tvLastChecked = v.findViewById(R.id.tvLastChecked);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnDelete = v.findViewById(R.id.btnDelete);
            btnBrowse = v.findViewById(R.id.btnBrowse);
            btnRefresh = v.findViewById(R.id.btnRefresh);
            layoutUnreadBadgeContainer = v.findViewById(R.id.layoutUnreadBadgeContainer);
            layoutCommitCount = v.findViewById(R.id.layoutCommitCount);
            layoutReleaseCount = v.findViewById(R.id.layoutReleaseCount);
            tvCommit = v.findViewById(R.id.tvCommits);
            tvRelease = v.findViewById(R.id.tvRelease);
            tvCommitCount = v.findViewById(R.id.tvBadgeCommits);
            tvReleaseCount = v.findViewById(R.id.tvBadgeReleases);
        }
    }


    public void setActiveRepoId(Integer id) {
        if (Objects.equals(activeRepoId, id)) return;

        Integer previousId = activeRepoId;
        activeRepoId = id;

        if (previousId != null) {
            int oldPos = getPositionForRepoId(previousId);
            if (oldPos != RecyclerView.NO_POSITION) notifyItemChanged(oldPos);
        }

        if (activeRepoId != null) {
            int newPos = getPositionForRepoId(activeRepoId);
            if (newPos != RecyclerView.NO_POSITION) notifyItemChanged(newPos);
        }
    }

    public int getPositionForRepoId(int id) {
        List<RepoEntity> currentList = getCurrentList();
        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).id == id) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    public void startRotation(View view) {
        if (!animators.containsKey(view)) {
            ObjectAnimator rotation = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f);
            rotation.setDuration(1000);
            rotation.setRepeatCount(ValueAnimator.INFINITE);
            rotation.setInterpolator(new LinearInterpolator());
            rotation.start();
            animators.put(view, rotation);
        }
    }

    public void stopRotation(View view) {
        ObjectAnimator animator = animators.remove(view);
        if (animator != null) {
            animator.cancel();
            view.setRotation(0f);
        }
    }
}