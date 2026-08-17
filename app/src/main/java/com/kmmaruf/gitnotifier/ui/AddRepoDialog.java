package com.kmmaruf.gitnotifier.ui;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;
import com.kmmaruf.gitnotifier.network.ApiClient;
import com.kmmaruf.gitnotifier.network.GitHubApi;
import com.kmmaruf.gitnotifier.network.model.Branch;

import retrofit2.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddRepoDialog {
    public interface Callback {
        void onSaved(RepoEntity r);
    }

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?github\\.com/([\\w.\\-]+)/([\\w.\\-]+?)(?:\\.git)?/?$",
            Pattern.CASE_INSENSITIVE);

    public static void show(Context ctx, RepoEntity edit, Callback cb) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_repo, null);
        b.setView(v);
        Dialog d = b.create();

        TextInputEditText etLink = v.findViewById(R.id.etLink);
        TextInputEditText etName = v.findViewById(R.id.etName);
        MaterialCheckBox cbCommits = v.findViewById(R.id.cbCommits);
        MaterialCheckBox cbReleases = v.findViewById(R.id.cbReleases);
        TextInputLayout layoutBranch = v.findViewById(R.id.layoutBranch);
        AutoCompleteTextView actvBranch = v.findViewById(R.id.actvBranch);
        ProgressBar progressBranches = v.findViewById(R.id.progressBranches);
        Button btnSave = v.findViewById(R.id.btnSave);
        Button btnCancel = v.findViewById(R.id.btnCancel);

        final RepoEntity r = (edit == null ? new RepoEntity() : edit);
        final boolean[] branchesLoaded = {false};
        final List<String> branchNames = new ArrayList<>();

        Runnable updateSaveEnabled = () -> {
            boolean hasRepo = r.owner != null && r.name != null;
            boolean commitsOk = !cbCommits.isChecked() || (actvBranch.getText() != null && actvBranch.getText().length() > 0);
            btnSave.setEnabled(hasRepo && commitsOk && (cbCommits.isChecked() || cbReleases.isChecked()));
        };

        cbCommits.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutBranch.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateSaveEnabled.run();
        });
        cbReleases.setOnCheckedChangeListener((buttonView, isChecked) -> updateSaveEnabled.run());
        actvBranch.setOnItemClickListener((parent, view, position, id) -> updateSaveEnabled.run());

        etLink.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int s, int b, int a) {}
            public void onTextChanged(CharSequence t, int s, int b, int a) {}

            public void afterTextChanged(Editable e) {
                String link = e.toString().trim();
                Matcher m = GITHUB_URL_PATTERN.matcher(link);
                if (m.find()) {
                    r.owner = m.group(1);
                    r.name = m.group(2);
                    r.fullName = r.owner + "/" + r.name;
                    r.url = "https://github.com/" + r.fullName;
                    etName.setText(r.fullName);
                    etLink.setError(null);

                    if (!branchesLoaded[0]) {
                        branchesLoaded[0] = true;
                        progressBranches.setVisibility(View.VISIBLE);
                        GitHubApi api = ApiClient.getApi(ctx);
                        api.listBranches(r.owner, r.name).enqueue(new retrofit2.Callback<List<Branch>>() {
                            public void onResponse(Call<List<Branch>> c, Response<List<Branch>> resp) {
                                progressBranches.setVisibility(View.GONE);
                                if (!resp.isSuccessful() || resp.body() == null) {
                                    String msg = resp.message() != null ? resp.message() : "HTTP " + resp.code();
                                    etLink.setError("Cannot load branches: " + msg);
                                    Toast.makeText(ctx, "Failed to load branches: " + msg, Toast.LENGTH_LONG).show();
                                    branchesLoaded[0] = false;
                                    return;
                                }
                                branchNames.clear();
                                for (Branch br : resp.body()) {
                                    branchNames.add(br.name);
                                }
                                ArrayAdapter<String> ad = new ArrayAdapter<>(ctx,
                                        android.R.layout.simple_dropdown_item_1line, branchNames);
                                actvBranch.setAdapter(ad);

                                if (r.branch != null && branchNames.contains(r.branch)) {
                                    actvBranch.setText(r.branch, false);
                                } else if (!branchNames.isEmpty()) {
                                    int mainIdx = branchNames.indexOf("main");
                                    if (mainIdx < 0) mainIdx = branchNames.indexOf("master");
                                    String preferred = branchNames.get(mainIdx >= 0 ? mainIdx : 0);
                                    actvBranch.setText(preferred, false);
                                }
                                updateSaveEnabled.run();
                            }

                            public void onFailure(Call<List<Branch>> c, Throwable t) {
                                progressBranches.setVisibility(View.GONE);
                                branchesLoaded[0] = false;
                                etLink.setError("Network error");
                                Toast.makeText(ctx, "Network error loading branches", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    updateSaveEnabled.run();
                } else if (!link.isEmpty()) {
                    etLink.setError(ctx.getString(R.string.valid_url_required));
                    r.owner = null;
                    r.name = null;
                    updateSaveEnabled.run();
                }
            }
        });

        if (edit != null) {
            etLink.setText(r.url);
            etLink.setEnabled(false);
            etName.setText(r.fullName);
            cbCommits.setChecked(r.notifyCommits);
            cbReleases.setChecked(r.notifyReleases);
            layoutBranch.setVisibility(r.notifyCommits ? View.VISIBLE : View.GONE);
            if (r.branch != null) {
                actvBranch.setText(r.branch, false);
            }
            // Force branch load
            branchesLoaded[0] = false;
            etLink.setText(r.url);
        } else {
            etLink.requestFocus();
        }

        btnCancel.setOnClickListener(x -> d.dismiss());

        btnSave.setOnClickListener(x -> {
            if (r.owner == null || r.name == null) {
                etLink.setError(ctx.getString(R.string.valid_url_required));
                return;
            }
            if (cbCommits.isChecked()) {
                String branch = actvBranch.getText() != null ? actvBranch.getText().toString().trim() : "";
                if (branch.isEmpty()) {
                    Toast.makeText(ctx, R.string.please_select_branch, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            RepoEntity updated = new RepoEntity();
            updated.id = r.id;
            updated.owner = r.owner;
            updated.name = r.name;
            updated.fullName = r.fullName;
            updated.url = r.url;
            updated.notifyCommits = cbCommits.isChecked();
            updated.branch = cbCommits.isChecked() && actvBranch.getText() != null
                    ? actvBranch.getText().toString().trim() : null;
            updated.notifyReleases = cbReleases.isChecked();
            updated.enabled = true;
            updated.lastChecked = r.lastChecked;
            updated.lastCommitSha = r.lastCommitSha;
            updated.lastReleaseId = r.lastReleaseId;
            updated.unreadCommitsCount = r.unreadCommitsCount;
            updated.unreadReleaseCount = r.unreadReleaseCount;

            cb.onSaved(updated);
            d.dismiss();
        });

        d.show();
        updateSaveEnabled.run();
    }
}
