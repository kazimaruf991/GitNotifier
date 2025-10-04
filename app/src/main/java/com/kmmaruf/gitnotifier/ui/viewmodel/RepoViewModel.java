package com.kmmaruf.gitnotifier.ui.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.kmmaruf.gitnotifier.data.*;
import com.kmmaruf.gitnotifier.data.dao.RepoDao;
import com.kmmaruf.gitnotifier.data.entity.RepoEntity;
import com.kmmaruf.gitnotifier.worker.RefreshScheduler;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

public class RepoViewModel extends AndroidViewModel {

    private final RepoDao dao;
    MutableLiveData<UUID> currentWorkId = new MutableLiveData<>();

    public RepoViewModel(@NonNull Application app) {
        super(app);
        dao = AppDatabase.getInstance(app).repoDao();
    }

    public void trackWork(UUID id) {
        currentWorkId.setValue(id);
    }

    public LiveData<WorkInfo> getWorkInfo(Context context) {
        return Transformations.switchMap(currentWorkId, id -> WorkManager.getInstance(context).getWorkInfoByIdLiveData(id));
    }


    public LiveData<List<RepoEntity>> getAll(boolean showSorted) {
        if (showSorted) {
            return dao.getAllSorted();
        } else {
            return dao.getAll();
        }
    }

    public void insert(RepoEntity r) {
        Executors.newSingleThreadExecutor().execute(() -> {
            int repoId = (int) dao.insert(r);
            RefreshScheduler.scheduleOneTime(getApplication(), repoId);
        });
    }

    public void update(RepoEntity r) {
        Executors.newSingleThreadExecutor().execute(() -> {
            dao.update(r);
            RefreshScheduler.scheduleOneTime(getApplication(), r.id);
        });
    }

    public void delete(RepoEntity r) {
        Executors.newSingleThreadExecutor().execute(() -> dao.delete(r));
    }

    public UUID refreshAllImmediately() {
        return RefreshScheduler.scheduleOneTimeAll(getApplication());
    }

    public UUID refreshSingle(int repoId) {
        return RefreshScheduler.scheduleOneTime(getApplication(), repoId);
    }

    public void updateEnableState(int repoId, boolean isEnabled) {
        Executors.newSingleThreadExecutor().execute(() -> dao.updateEnabled(repoId, isEnabled));
    }
}
