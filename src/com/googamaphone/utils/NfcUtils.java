package com.googamaphone.utils;

import java.io.IOException;

import android.annotation.TargetApi;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;

@TargetApi(14)
public class NfcUtils {
    public static final int MIN_SDK_VERSION = Build.VERSION_CODES.ICE_CREAM_SANDWICH;

    public static boolean writeUriToTag(Tag tag, Uri uri, String appPackage) {
        final Ndef ndefTag = Ndef.get(tag);

        if (ndefTag != null) {
            return writeUriToNdefTag(ndefTag, uri, appPackage);
        }

        final NdefFormatable ndefFormatableTag = NdefFormatable.get(tag);

        if (ndefFormatableTag != null) {
            return writeUriToNdefFormatableTag(ndefFormatableTag, uri, appPackage);
        }

        return false;
    }

    private static boolean writeUriToNdefFormatableTag(NdefFormatable ndefFormatableTag, Uri uri,
            String appPackage) {
        final NdefMessage msg = obtainNdefMessage(uri, appPackage);

        try {
            ndefFormatableTag.connect();
            ndefFormatableTag.format(msg);
            ndefFormatableTag.close();

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
        } finally {
            try {
                ndefFormatableTag.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    private static NdefMessage obtainNdefMessage(Uri uri, String appPackage) {
        final int recordCount = ((appPackage != null) ? 2 : 1);
        final NdefRecord[] records = new NdefRecord[recordCount];
        records[0] = NdefRecord.createUri(uri);

        if (appPackage != null) {
            records[1] = NdefRecord.createApplicationRecord(appPackage);
        }

        final NdefMessage msg = new NdefMessage(records);
        return msg;
    }

    private static boolean writeUriToNdefTag(Ndef ndefTag, Uri uri, String appPackage) {
        final NdefMessage msg = obtainNdefMessage(uri, appPackage);

        try {
            ndefTag.connect();
            ndefTag.writeNdefMessage(msg);
            ndefTag.close();

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
        } finally {
            try {
                ndefTag.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }
}
