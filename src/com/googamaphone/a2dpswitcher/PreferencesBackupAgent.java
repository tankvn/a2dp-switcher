
package com.googamaphone.a2dpswitcher;

import android.annotation.TargetApi;
import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class PreferencesBackupAgent extends BackupAgentHelper {
    private static final String PREFS_BACKUP_KEY = "prefs";

    @Override
    public void onCreate() {
        addHelper(PREFS_BACKUP_KEY, new SharedPreferencesBackupHelper(this, getPackageName()));
    }
}
