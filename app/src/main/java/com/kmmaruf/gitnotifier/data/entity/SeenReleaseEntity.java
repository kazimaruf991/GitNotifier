package com.kmmaruf.gitnotifier.data.entity;

import androidx.room.Entity;
import androidx.room.Index;

/**
 * Release id already processed for a repo. Releases are few, so the cap
 * can be larger than for commits.
 */
@Entity(tableName = "seen_releases",
        primaryKeys = {"repoId", "releaseId"},
        indices = {@Index("repoId"), @Index("seenAt")})
public class SeenReleaseEntity {
    public int repoId;
    public long releaseId;
    public long seenAt;

    public SeenReleaseEntity(int repoId, long releaseId, long seenAt) {
        this.repoId = repoId;
        this.releaseId = releaseId;
        this.seenAt = seenAt;
    }
}
