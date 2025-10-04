package com.kmmaruf.gitnotifier.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Objects;

@Entity(tableName = "repos")
public class RepoEntity implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String owner;
    public String name;
    public String fullName;    // owner/name
    public String url;         // original GitHub URL

    public boolean notifyCommits;
    public String branch;
    public boolean notifyReleases;

    public String lastCommitSha;
    public long lastReleaseId;

    public long lastChecked;     // timestamp in millis
    public boolean enabled;      // repo is active/inactive

    public int unreadCommitsCount;   // commit notifications badge
    public int unreadReleaseCount;   // release notifications badge

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RepoEntity)) return false;
        RepoEntity that = (RepoEntity) o;
        return id == that.id && notifyCommits == that.notifyCommits && notifyReleases == that.notifyReleases && lastReleaseId == that.lastReleaseId && lastChecked == that.lastChecked && enabled == that.enabled && unreadCommitsCount == that.unreadCommitsCount && unreadReleaseCount == that.unreadReleaseCount && Objects.equals(owner, that.owner) && Objects.equals(name, that.name) && Objects.equals(fullName, that.fullName) && Objects.equals(url, that.url) && Objects.equals(branch, that.branch) && Objects.equals(lastCommitSha, that.lastCommitSha);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, owner, name, fullName, url, notifyCommits, branch, notifyReleases, lastCommitSha, lastReleaseId, lastChecked, enabled, unreadCommitsCount, unreadReleaseCount);
    }
}
