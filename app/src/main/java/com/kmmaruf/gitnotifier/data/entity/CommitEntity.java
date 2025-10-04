package com.kmmaruf.gitnotifier.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class CommitEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int repoId;
    public String repoFullName;
    public String sha;
    public String message;
    public String authorDate;
    public String htmlUrl;
    public long timestamp;
}