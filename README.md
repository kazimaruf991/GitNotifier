# Git Notifier

![Android CI](https://github.com/kazimaruf991/GitNotifier/actions/workflows/android.yml/badge.svg)

A lightweight Android app that monitors GitHub repositories for new **commits** and **releases**, and notifies you in the background.

---

## Features

### Monitoring
- Track commits (per branch) and/or releases for any public GitHub repo
- Background periodic sync with a customizable interval
- Manual refresh: pull-to-refresh all repos, or sync a single repo
- Enable / disable monitoring per repository
- Unread badges for new commits and releases
- Quick Settings tile to turn background monitoring on or off

### Smart sync
- First sync only sets a baseline (no flood of old history when you add a repo)
- Hybrid seen-set of commit SHAs and release IDs so deleted tips or force-pushes do not re-notify the whole history
- GitHub API rate-limit checks before sync; clear messages when quota is too low
- Optional personal access token for higher rate limits
- Rate-limit status refreshes when you return to the app with a token set

### Share & add
- Share a GitHub link into Git Notifier from any app
- Open `github.com` links with Git Notifier
- Valid repo links open the **Add repository** dialog only (no full main screen)
- Invalid links show a clear error

### Notifications & detail
- System notifications for new commits and releases
- Tap a notification to open a floating commits/releases dialog without launching the main UI
- Markdown rendering and clickable links in commit messages and release notes

### Backup & restore
- Encrypted backup of repositories and preferences
- Restore with password prompt showing the selected file name
- Show/hide password toggle; wrong password keeps the dialog open with an error
- Live rate-limit values are not stored in backups (refreshed on device)

### UI
- Material 3 light / dark theme
- Status summary (repos / unread / disabled) under the toolbar
- Auto-hiding FAB while scrolling
- Empty state when no repositories are configured

---

## Getting started

1. Open the latest release:  
   **[Git Notifier – Latest Release](https://github.com/kazimaruf991/GitNotifier/releases/latest)**
2. Download the APK from **Assets**.
3. On your device, allow install from unknown sources if needed, then install the APK.
4. Launch the app, optionally add a GitHub token in **Settings**, add repositories, and enable background monitoring.

### Tips
- For private repos or higher API limits, add a [GitHub personal access token](https://github.com/settings/tokens) in Settings.
- After install, you can add the **Git monitoring** tile from the system Quick Settings edit panel.
- Share any `https://github.com/owner/repo` link to Git Notifier to add it quickly.

---

## Build from source

```bash
git clone https://github.com/kazimaruf991/GitNotifier.git
cd GitNotifier
./gradlew :app:assembleDebug
```

Requirements: Android Studio / JDK suitable for your Android Gradle Plugin version.

---

## License

See the repository for license details.

---

## Star the project

If Git Notifier is useful to you, please give the repo a ⭐ — it helps others find it.
