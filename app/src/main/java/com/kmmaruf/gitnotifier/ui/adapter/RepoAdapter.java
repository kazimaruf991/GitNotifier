package com.kmmaruf.gitnotifier.ui.adapter;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import android.widget.CompoundButton;
import androidx.recyclerview.widget.*;

import com.google.android.material.chip.Chip;
import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;
import com.kmmaruf.gitnotifier.network.NetworkUtils;
import com.kmmaruf.gitnotifier.ui.DialogListActivity;
import com.kmmaruf.gitnotifier.ui.common.Utils;

import java.util.HashMap;
import java.util.List;
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
        void onMarkCommitsRead(RepoEntity r);
        void onMarkReleasesRead(RepoEntity r);
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
        h.swEnabled.setOnCheckedChangeListener(null);
        h.swEnabled.setChecked(r.enabled);
        h.tvName.setText(r.fullName);

        // Settings line
        StringBuilder settings = new StringBuilder();
        if (r.notifyCommits) {
            settings.append("Commits · ").append(r.branch != null ? r.branch : "?");
        }
        if (r.notifyReleases) {
            if (settings.length() > 0) settings.append("  ·  ");
            settings.append("Releases");
        }
        if (settings.length() == 0) {
            settings.append("No notifications enabled");
        }
        h.tvSettings.setText(settings.toString());

        h.tvLastChecked.setText(Utils.formatRelativeTime(r.lastChecked, h.itemView.getContext()));

        h.swEnabled.setOnCheckedChangeListener((buttonView, isChecked) ->
                actionHandler.onUpdateSwitchValue(r, isChecked));

        h.btnRefresh.setOnClickListener(x -> {
            if (!NetworkUtils.isInternetAvailable(x.getContext())) {
                Toast.makeText(x.getContext(), R.string.failed_no_internet_connection, Toast.LENGTH_LONG).show();
            } else {
                click.onClick(r, 3);
            }
        });

        // Overflow menu: Edit / Open on GitHub / Delete
        h.btnMore.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenu().add(0, 1, 0, R.string.edit);
            popup.getMenu().add(0, 2, 1, R.string.open_on_github);
            popup.getMenu().add(0, 3, 2, R.string.delete);
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) {
                    click.onClick(r, 1); // edit
                } else if (id == 2) {
                    String url = "https://github.com/" + r.owner + "/" + r.name;
                    v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } else if (id == 3) {
                    click.onClick(r, 2); // delete
                }
                return true;
            });
            popup.show();
        });

        boolean isSyncing = activeRepoId != null && activeRepoId == r.id;
        if (isSyncing) {
            startRotation(h.btnRefresh);
        } else {
            stopRotation(h.btnRefresh);
        }

        // Unread chips
        int unreadCommitsCount = r.unreadCommitsCount;
        int unreadReleaseCount = r.unreadReleaseCount;

        if (unreadCommitsCount == 0 && unreadReleaseCount == 0) {
            h.layoutUnreadBadgeContainer.setVisibility(View.GONE);
        } else {
            h.layoutUnreadBadgeContainer.setVisibility(View.VISIBLE);

            if (unreadCommitsCount > 0) {
                h.chipCommits.setVisibility(View.VISIBLE);
                h.chipCommits.setText(h.itemView.getContext().getString(R.string.commits_) + "  " + unreadCommitsCount);
                h.chipCommits.setOnClickListener(v -> openCommitList(v, r));
                h.chipCommits.setOnLongClickListener(v -> {
                    actionHandler.onMarkCommitsRead(r);
                    Toast.makeText(v.getContext(), R.string.mark_as_read, Toast.LENGTH_SHORT).show();
                    return true;
                });
            } else {
                h.chipCommits.setVisibility(View.GONE);
            }

            if (unreadReleaseCount > 0) {
                h.chipReleases.setVisibility(View.VISIBLE);
                h.chipReleases.setText(h.itemView.getContext().getString(R.string.releases_) + "  " + unreadReleaseCount);
                h.chipReleases.setOnClickListener(v -> openReleaseList(v, r));
                h.chipReleases.setOnLongClickListener(v -> {
                    actionHandler.onMarkReleasesRead(r);
                    Toast.makeText(v.getContext(), R.string.mark_as_read, Toast.LENGTH_SHORT).show();
                    return true;
                });
            } else {
                h.chipReleases.setVisibility(View.GONE);
            }
        }
    }

    private void openCommitList(View v, RepoEntity r) {
        Intent intent = new Intent(v.getContext(), DialogListActivity.class);
        intent.putExtra("repo_name", r.fullName);
        intent.putExtra("branch_name", r.branch);
        intent.putExtra("repo_id", r.id);
        intent.putExtra("type", "commit");
        v.getContext().startActivity(intent);
    }

    private void openReleaseList(View v, RepoEntity r) {
        Intent intent = new Intent(v.getContext(), DialogListActivity.class);
        intent.putExtra("repo_name", r.fullName);
        intent.putExtra("repo_id", r.id);
        intent.putExtra("type", "release");
        v.getContext().startActivity(intent);
    }

    static class VH extends RecyclerView.ViewHolder {
        CompoundButton swEnabled;
        TextView tvName, tvSettings, tvLastChecked;
        ImageButton btnRefresh, btnMore;
        LinearLayout layoutUnreadBadgeContainer;
        Chip chipCommits, chipReleases;

        VH(View v) {
            super(v);
            swEnabled = v.findViewById(R.id.switchEnabled);
            tvName = v.findViewById(R.id.tvRepoName);
            tvSettings = v.findViewById(R.id.tvSettings);
            tvLastChecked = v.findViewById(R.id.tvLastChecked);
            btnRefresh = v.findViewById(R.id.btnRefresh);
            btnMore = v.findViewById(R.id.btnMore);
            layoutUnreadBadgeContainer = v.findViewById(R.id.layoutUnreadBadgeContainer);
            chipCommits = v.findViewById(R.id.chipCommits);
            chipReleases = v.findViewById(R.id.chipReleases);
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
