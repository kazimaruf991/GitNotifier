package com.kmmaruf.gitnotifier.data.dao;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.kmmaruf.gitnotifier.data.entity.CommitEntity;

import java.util.List;

@Dao
public interface CommitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CommitEntity> commits);

    @Query("SELECT * FROM CommitEntity")
    List<CommitEntity> getAllSync();

    // Existing methods using repoFullName
    @Query("SELECT COUNT(*) FROM CommitEntity WHERE repoFullName = :repo")
    int getCountByRepo(String repo);

    @Query("DELETE FROM CommitEntity WHERE repoFullName = :repo")
    void clearByRepo(String repo);

    // 🆕 Methods using repoId for better performance
    @Query("SELECT COUNT(*) FROM CommitEntity WHERE repoId = :repoId")
    int getCountByRepoId(int repoId);

    @Query("DELETE FROM CommitEntity WHERE repoId = :repoId")
    void clearByRepoId(int repoId);

    @Query("SELECT * FROM CommitEntity WHERE repoId = :repoId")
    List<CommitEntity> getAllByRepoId(int repoId);
}