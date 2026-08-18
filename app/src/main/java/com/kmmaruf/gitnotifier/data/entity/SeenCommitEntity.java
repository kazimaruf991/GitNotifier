package com.kmmaruf.gitnotifier.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * SHA that has already been processed for a repo (baseline or notified).
 * Used so a deleted tip can fall back to an older known SHA instead of
 * treating the whole API page as new. Capped per repo in the DAO.
 */
@Entity(tableName = "seen_commits",
        primaryKeys = {"repoId", "sha"},
        indices = {@Index("repoId"), @Index("seenAt")})
public class SeenCommitEntity {
    public int repoId;

    @NonNull
    public String sha;

    /** Wall-clock time when recorded; used for pruning oldest entries. */
    public long seenAt;

    public SeenCommitEntity(int repoId, @NonNull String sha, long seenAt) {
        this.repoId = repoId;
        this.sha = sha;
        this.seenAt = seenAt;
    }
}
