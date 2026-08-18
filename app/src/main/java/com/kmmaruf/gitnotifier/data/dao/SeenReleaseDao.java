package com.kmmaruf.gitnotifier.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.kmmaruf.gitnotifier.data.entity.SeenReleaseEntity;

import java.util.List;

@Dao
public interface SeenReleaseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<SeenReleaseEntity> items);

    @Query("SELECT releaseId FROM seen_releases WHERE repoId = :repoId")
    List<Long> getIdsForRepo(int repoId);

    @Query("SELECT COUNT(*) FROM seen_releases WHERE repoId = :repoId")
    int countForRepo(int repoId);

    @Query("SELECT releaseId FROM seen_releases WHERE repoId = :repoId ORDER BY seenAt ASC LIMIT :limit")
    List<Long> getOldestIds(int repoId, int limit);

    @Query("DELETE FROM seen_releases WHERE repoId = :repoId AND releaseId IN (:ids)")
    void deleteIds(int repoId, List<Long> ids);

    @Query("DELETE FROM seen_releases WHERE repoId = :repoId")
    void clearByRepoId(int repoId);

    @Query("DELETE FROM seen_releases")
    void clearAll();
}
