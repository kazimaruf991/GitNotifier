package com.kmmaruf.gitnotifier.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.kmmaruf.gitnotifier.data.entity.RepoEntity;

import java.util.List;

@Dao
public interface RepoDao {
    @Query("SELECT * FROM repos")
    LiveData<List<RepoEntity>> getAll();

    @Query("SELECT * FROM repos")
    List<RepoEntity> getAllSync();

    @Query("SELECT * FROM repos WHERE fullName = :fullName LIMIT 1")
    RepoEntity getByFullName(String fullName);

    @Query("SELECT * FROM repos WHERE fullName = :fullName LIMIT 1")
    LiveData<RepoEntity> getLiveByFullName(String fullName);

    @Query("SELECT * FROM repos WHERE id = :id LIMIT 1")
    RepoEntity getRepoEntityById(int id);

    @Query("SELECT * FROM repos WHERE id = :id LIMIT 1")
    LiveData<RepoEntity> getRepoEntityLiveById(int id);

    @Query("SELECT * FROM repos WHERE owner = :owner AND name = :name LIMIT 1")
    RepoEntity getByOwnerAndName(String owner, String name);

    @Query("SELECT * FROM repos WHERE owner = :owner AND name = :name LIMIT 1")
    LiveData<RepoEntity> getLiveByOwnerAndName(String owner, String name);

    @Query("SELECT * FROM repos WHERE enabled = 1")
    LiveData<List<RepoEntity>> getAllEnabled();

    @Query("SELECT * FROM repos WHERE enabled = 1")
    List<RepoEntity> getAllEnabledSync();

    @Query("UPDATE repos SET lastChecked = :timestamp, enabled = :enabled WHERE id = :repoId")
    void updateStatus(int repoId, long timestamp, boolean enabled);

    @Query("UPDATE repos SET lastChecked = :timestamp WHERE id = :repoId")
    void updateLastChecked(int repoId, long timestamp);

    @Query("UPDATE repos SET enabled = :enabled WHERE id = :repoId")
    void updateEnabled(int repoId, boolean enabled);

    @Query("SELECT unreadCommitsCount FROM repos WHERE id = :repoId")
    int getUnreadCommitsById(int repoId);

    @Query("SELECT unreadReleaseCount FROM repos WHERE id = :repoId")
    int getUnreadReleasesById(int repoId);

    @Query("UPDATE repos SET unreadCommitsCount = :count WHERE id = :repoId")
    void setUnreadCommitsById(int repoId, int count);

    @Query("UPDATE repos SET unreadReleaseCount = :count WHERE id = :repoId")
    void setUnreadReleasesById(int repoId, int count);

    @Query("SELECT unreadCommitsCount FROM repos WHERE owner = :owner AND name = :name LIMIT 1")
    int getUnreadCommitsByOwnerAndName(String owner, String name);

    @Query("SELECT unreadReleaseCount FROM repos WHERE owner = :owner AND name = :name LIMIT 1")
    int getUnreadReleasesByOwnerAndName(String owner, String name);

    @Query("SELECT * FROM repos ORDER BY (CASE WHEN unreadCommitsCount > 0 OR unreadReleaseCount > 0 THEN 0 ELSE 1 END), id ASC")
    LiveData<List<RepoEntity>> getAllSorted();


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RepoEntity> repos);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RepoEntity repo);

    @Update
    void update(RepoEntity repo);

    @Delete
    void delete(RepoEntity repo);
}