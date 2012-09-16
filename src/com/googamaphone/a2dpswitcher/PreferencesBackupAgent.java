
package com.googamaphone.a2dpswitcher;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

public class PreferencesBackupAgent extends BackupAgentHelper {
    private static final String PREFS_BACKUP_KEY = "prefs";

    @Override
    public void onCreate() {
        addHelper(PREFS_BACKUP_KEY, new SharedPreferencesBackupHelper(this, getPackageName()));
    }
}
