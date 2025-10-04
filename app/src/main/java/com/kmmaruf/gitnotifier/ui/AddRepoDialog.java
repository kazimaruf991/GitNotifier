package com.kmmaruf.gitnotifier.ui;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;
import com.kmmaruf.gitnotifier.network.ApiClient;
import com.kmmaruf.gitnotifier.network.GitHubApi;
import com.kmmaruf.gitnotifier.network.model.Branch;

import retrofit2.*;

import java.util.ArrayList;
import java.util.List;

public class AddRepoDialog {
    public interface Callback {
        void onSaved(RepoEntity r);
    }

    public static void show(Context ctx, RepoEntity edit, Callback cb) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_repo, null);
        b.setView(v);
        Dialog d = b.create();
        EditText etLink = v.findViewById(R.id.etLink);
        EditText etName = v.findViewById(R.id.etName);
        CheckBox cbCommits = v.findViewById(R.id.cbCommits);
        Spinner spBranch = v.findViewById(R.id.spinnerBranch);
        CheckBox cbReleases = v.findViewById(R.id.cbReleases);
        Button btnSave = v.findViewById(R.id.btnSave);
        Button btnCancel = v.findViewById(R.id.btnCancel);

        RepoEntity r = (edit == null ? new RepoEntity() : edit);

        etLink.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int s, int b, int a) {
            }

            public void onTextChanged(CharSequence t, int s, int b, int a) {
            }

            public void afterTextChanged(Editable e) {
                String link = e.toString();
                String[] parts = link.replace("https://github.com/", "").split("/");
                if (parts.length >= 2) {
                    r.owner = parts[0];
                    r.name = parts[1];
                    r.fullName = parts[0] + "/" + parts[1];
                    r.url = link;
                    etName.setText(r.fullName);
                    // fetch branches
                    GitHubApi api = ApiClient.getApi(ctx);
                    api.listBranches(r.owner, r.name).enqueue(new retrofit2.Callback<List<Branch>>() {
                        public void onResponse(Call<List<Branch>> c, Response<List<Branch>> resp) {
                            if (resp.body() == null) {
                                Toast.makeText(ctx, resp.message(), Toast.LENGTH_LONG).show();
                                etLink.setError("Error");
                                return;
                            }
                            List<String> names = new ArrayList<>();
                            for (Branch b : resp.body()) names.add(b.name);
                            ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, names);
                            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            spBranch.setAdapter(ad);
                            // select existing
                            if (r.branch != null) {
                                int idx = names.indexOf(r.branch);
                                if (idx >= 0) spBranch.setSelection(idx);
                            }
                        }

                        public void onFailure(Call<List<Branch>> c, Throwable t) {
                        }
                    });
                }
            }
        });


        if (edit != null) {
            etLink.setText(r.url);
            etLink.setEnabled(false);
            etName.setText(r.fullName);
            cbCommits.setChecked(r.notifyCommits);
            cbReleases.setChecked(r.notifyReleases);

        }


        btnCancel.setOnClickListener(x -> d.dismiss());

        btnSave.setOnClickListener(x -> {
            RepoEntity updated = new RepoEntity();
            updated.id = r.id; // if editing
            updated.owner = r.owner;
            updated.name = r.name;
            updated.fullName = etName.getText().toString();
            updated.url = r.url;
            updated.notifyCommits = cbCommits.isChecked();
            updated.branch = (spBranch.getSelectedItem() != null) ? spBranch.getSelectedItem().toString() : null;
            updated.notifyReleases = cbReleases.isChecked();
            updated.enabled = true;
            updated.lastChecked = r.lastChecked;
            updated.lastCommitSha = r.lastCommitSha;
            updated.lastReleaseId = r.lastReleaseId;

            cb.onSaved(updated);
            d.dismiss();
        });

        d.show();
    }
}