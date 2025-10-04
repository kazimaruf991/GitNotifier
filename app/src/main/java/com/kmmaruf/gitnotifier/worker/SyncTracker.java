package com.kmmaruf.gitnotifier.worker;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class SyncTracker {
    private static final MutableLiveData<Integer> activeRepoId = new MutableLiveData<>();

    public static LiveData<Integer> getActiveRepoId() {
        return activeRepoId;
    }

    public static void setActiveRepoId(int repoId) {
        activeRepoId.postValue(repoId);
    }

    public static void clear() {
        activeRepoId.postValue(null);
    }
}