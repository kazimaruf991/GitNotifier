package com.kmmaruf.gitnotifier.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.kmmaruf.gitnotifier.data.entity.ReleaseEntity;

import java.util.List;

@Dao
public interface ReleaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ReleaseEntity> releases);

    @Query("SELECT * FROM ReleaseEntity")
    List<ReleaseEntity> getAllSync();

    @Query("SELECT COUNT(*) FROM ReleaseEntity WHERE repoFullName = :repo")
    int getCountByRepo(String repo);

    @Query("DELETE FROM ReleaseEntity WHERE repoFullName = :repo")
    void clearByRepo(String repo);

    @Query("SELECT COUNT(*) FROM ReleaseEntity WHERE repoId = :repoId")
    int getCountByRepoId(int repoId);

    @Query("DELETE FROM ReleaseEntity WHERE repoId = :repoId")
    void clearByRepoId(int repoId);

    @Query("SELECT * FROM ReleaseEntity WHERE repoId = :repoId")
    List<ReleaseEntity> getAllByRepoId(int repoId);
}