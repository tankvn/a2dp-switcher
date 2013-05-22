
package com.googamaphone.utils;

import android.content.SharedPreferences;
import android.util.SparseArray;

import java.util.Collection;

public class PreferencesUtils {
    private static final String ESCAPE_CHARACTER = "\\";
    private static final String ITEM_SEPARATOR = ",";
    private static final String GROUP_SEPARATOR = ";";

    private static final String[] ESCAPE_FIND = {
            ESCAPE_CHARACTER, ITEM_SEPARATOR, GROUP_SEPARATOR
    };

    private static final String[] ESCAPE_REPLACE = {
            ESCAPE_CHARACTER + ESCAPE_CHARACTER, ESCAPE_CHARACTER + ITEM_SEPARATOR,
            ESCAPE_CHARACTER + GROUP_SEPARATOR
    };

    public static void putSparseArray(SharedPreferences.Editor editor, String key,
                                      SparseArray<String> array) {
        final StringBuilder value = new StringBuilder();

        for (int i = 0; i < array.size(); i++) {
            value.append(Integer.toString(array.keyAt(i)));
            value.append(ITEM_SEPARATOR);
            value.append(escape(array.valueAt(i)));
            value.append(GROUP_SEPARATOR);
        }

        editor.putString(key, value.toString());
    }

    public static void getSparseArray(SharedPreferences prefs, String key, SparseArray<String> array) {
        final String value = prefs.getString(key, "");
        final String[] groups = value.split(GROUP_SEPARATOR);

        for (String group : groups) {
            if (group.length() == 0) {
                continue;
            }

            final String[] items = group.split(ITEM_SEPARATOR);

            array.put(Integer.parseInt(items[0]), unescape(items[1]));
        }
    }

    public static void putCollection(SharedPreferences.Editor editor, String key,
                                     Collection<Integer> collection) {
        final StringBuilder value = new StringBuilder();

        for (Integer item : collection) {
            value.append(item.toString());
            value.append(ITEM_SEPARATOR);
        }

        editor.putString(key, value.toString());
    }

    public static void getCollection(SharedPreferences prefs, String key,
                                     Collection<Integer> collection) {
        final String value = prefs.getString(key, "");
        final String[] items = value.split(ITEM_SEPARATOR);

        for (String item : items) {
            if (item.length() == 0) {
                continue;
            }

            collection.add(Integer.parseInt(item));
        }
    }

    private static String escape(String text) {
        for (int i = 0; i < ESCAPE_FIND.length; i++) {
            text = text.replace(ESCAPE_FIND[i], ESCAPE_REPLACE[i]);
        }

        return text;
    }

    private static String unescape(String text) {
        for (int i = 0; i < ESCAPE_REPLACE.length; i++) {
            text = text.replace(ESCAPE_REPLACE[i], ESCAPE_FIND[i]);
        }

        return text;
    }
}
