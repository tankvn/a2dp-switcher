package com.googamaphone.compat;

import android.annotation.TargetApi;
import android.content.SharedPreferences.Editor;
import android.os.Build;

public class EditorCompatUtils {
    private static final Impl IMPL = getImpl();

    private static Impl getImpl() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return new EditorCompatUtilsImpl_Gingerbread();
        }

        return new EditorCompatUtilsImpl_Base();
    }

    public static void apply(Editor editor) {
        IMPL.apply(editor);
    }

    private interface Impl {
        public void apply(Editor editor);
    }

    private static class EditorCompatUtilsImpl_Base implements Impl {
        public void apply(Editor editor) {
            editor.commit();
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static class EditorCompatUtilsImpl_Gingerbread implements Impl {
        public void apply(Editor editor) {
            editor.apply();
        }
    }
}
