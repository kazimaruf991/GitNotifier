package com.kmmaruf.gitnotifier.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class ReleaseEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int repoId;
    public String repoFullName;
    public String name;
    public String tagName;
    public String body;
    public String htmlUrl;
    public long timestamp;
}