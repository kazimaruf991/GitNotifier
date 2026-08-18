package com.kmmaruf.gitnotifier.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.kmmaruf.gitnotifier.data.entity.SeenCommitEntity;

import java.util.List;

@Dao
public interface SeenCommitDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<SeenCommitEntity> items);

    @Query("SELECT sha FROM seen_commits WHERE repoId = :repoId")
    List<String> getShasForRepo(int repoId);

    @Query("SELECT COUNT(*) FROM seen_commits WHERE repoId = :repoId")
    int countForRepo(int repoId);

    @Query("SELECT sha FROM seen_commits WHERE repoId = :repoId ORDER BY seenAt ASC LIMIT :limit")
    List<String> getOldestShas(int repoId, int limit);

    @Query("DELETE FROM seen_commits WHERE repoId = :repoId AND sha IN (:shas)")
    void deleteShas(int repoId, List<String> shas);

    @Query("DELETE FROM seen_commits WHERE repoId = :repoId")
    void clearByRepoId(int repoId);

    @Query("DELETE FROM seen_commits")
    void clearAll();
}
