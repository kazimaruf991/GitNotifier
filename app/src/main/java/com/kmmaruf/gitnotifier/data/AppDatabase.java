package com.kmmaruf.gitnotifier.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.kmmaruf.gitnotifier.data.dao.CommitDao;
import com.kmmaruf.gitnotifier.data.dao.ReleaseDao;
import com.kmmaruf.gitnotifier.data.dao.RepoDao;
import com.kmmaruf.gitnotifier.data.dao.SeenCommitDao;
import com.kmmaruf.gitnotifier.data.dao.SeenReleaseDao;
import com.kmmaruf.gitnotifier.data.entity.CommitEntity;
import com.kmmaruf.gitnotifier.data.entity.ReleaseEntity;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;
import com.kmmaruf.gitnotifier.data.entity.SeenCommitEntity;
import com.kmmaruf.gitnotifier.data.entity.SeenReleaseEntity;

@Database(
        entities = {
                RepoEntity.class,
                CommitEntity.class,
                ReleaseEntity.class,
                SeenCommitEntity.class,
                SeenReleaseEntity.class
        },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract RepoDao repoDao();

    public abstract CommitDao commitDao();

    public abstract ReleaseDao releaseDao();

    public abstract SeenCommitDao seenCommitDao();

    public abstract SeenReleaseDao seenReleaseDao();

    public static AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "gitnotifier.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
