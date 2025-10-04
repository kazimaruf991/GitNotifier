package com.kmmaruf.gitnotifier;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class App extends Application {
    public static final String CHANNEL_COMMITS = "channel_commits";
    public static final String CHANNEL_RELEASES = "channel_releases";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        //DynamicColors.applyToActivitiesIfAvailable(this);
    }

    private void createNotificationChannels() {
        NotificationChannel commits = new NotificationChannel(CHANNEL_COMMITS, "Commit Notifications", NotificationManager.IMPORTANCE_DEFAULT);
        commits.setDescription(getString(R.string.notify_when_new_commits_arrive));

        NotificationChannel releases = new NotificationChannel(CHANNEL_RELEASES, "Release Notifications", NotificationManager.IMPORTANCE_DEFAULT);
        releases.setDescription(getString(R.string.notify_when_new_releases_arrive));

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(commits);
        manager.createNotificationChannel(releases);
    }
}