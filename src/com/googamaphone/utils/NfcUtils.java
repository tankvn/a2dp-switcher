package com.googamaphone.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;

import java.io.IOException;

@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
public class NfcUtils {
    public static final int MIN_SDK = Build.VERSION_CODES.GINGERBREAD_MR1;

    public static boolean hasDefaultAdapter(Context context) {
        final NfcManager nfcManager = (NfcManager) context.getSystemService(Context.NFC_SERVICE);
        if (nfcManager == null) {
            return false;
        }

        final NfcAdapter nfcAdapter = nfcManager.getDefaultAdapter();
        return (nfcAdapter != null);
    }

    public static boolean writeUriToTag(Tag tag, Uri uri, String appPackage) {
        final Ndef ndefTag = Ndef.get(tag);
        if (ndefTag != null) {
            return writeUriToNdefTag(ndefTag, uri, appPackage);
        }

        final NdefFormatable ndefFormatableTag = NdefFormatable.get(tag);
        return (ndefFormatableTag != null) && writeUriToNdefFormatableTag(ndefFormatableTag, uri, appPackage);

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
        records[0] = NdefRecordCompatUtils.createUri(uri);

        if (appPackage != null) {
            records[1] = NdefRecordCompatUtils.createApplicationRecord(appPackage);
        }

        return new NdefMessage(records);
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
