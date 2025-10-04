package com.kmmaruf.gitnotifier.backup.model;

import com.kmmaruf.gitnotifier.data.entity.CommitEntity;
import com.kmmaruf.gitnotifier.data.entity.ReleaseEntity;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;

import java.util.List;
import java.util.Map;

public class BackupPayload {
    public List<RepoEntity> repos;
    public List<ReleaseEntity> releases;
    public List<CommitEntity> commits;
    public Map<String, ?> preferences;

    public BackupPayload(List<RepoEntity> repos, List<ReleaseEntity> releases, List<CommitEntity> commits, Map<String, ?> preferences) {
        this.repos = repos;
        this.releases = releases;
        this.commits = commits;
        this.preferences = preferences;
    }
}
