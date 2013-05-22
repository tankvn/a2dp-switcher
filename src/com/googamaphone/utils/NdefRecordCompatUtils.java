package com.googamaphone.utils;

import android.annotation.TargetApi;
import android.net.Uri;
import android.nfc.NdefRecord;
import android.os.Build;

import java.nio.charset.Charset;
import java.util.Locale;

public class NdefRecordCompatUtils {
    private static final Impl IMPL = getImpl();

    private static Impl getImpl() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return new Impl_IceCreamSandwich();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return new Impl_Gingerbread();
        } else {
            return new Impl_Base();
        }
    }

    /**
     * Create a new NDEF Record containing a URI.<p>
     * Use this method to encode a URI (or URL) into an NDEF Record.<p>
     * Uses the well known URI type representation: {@link NdefRecord#TNF_WELL_KNOWN}
     * and {@link NdefRecord#RTD_URI}. This is the most efficient encoding
     * of a URI into NDEF.<p>
     * The uri parameter will be normalized with
     * {@link Uri#normalizeScheme} to set the scheme to lower case to
     * follow Android best practices for intent filtering.
     * However the unchecked exception
     * {@link IllegalArgumentException} may be thrown if the uri
     * parameter has serious problems, for example if it is empty, so always
     * catch this exception if you are passing user-generated data into this
     * method.<p>
     * <p/>
     * Reference specification: NFCForum-TS-RTD_URI_1.0
     *
     * @param uri URI to encode.
     * @return an NDEF Record containing the URI
     * @throws IllegalArgumentException if the uri is empty or invalid
     */
    public static NdefRecord createUri(Uri uri) {
        return IMPL.createUri(uri);
    }

    /**
     * Create a new Android Application Record (AAR).
     * <p/>
     * This record indicates to other Android devices the package
     * that should be used to handle the entire NDEF message.
     * You can embed this record anywhere into your message
     * to ensure that the intended package receives the message.
     * <p/>
     * When an Android device dispatches an {@link android.nfc.NdefMessage}
     * containing one or more Android application records,
     * the applications contained in those records will be the
     * preferred target for the {@link android.nfc.NfcAdapter#ACTION_NDEF_DISCOVERED}
     * intent, in the order in which they appear in the message.
     * This dispatch behavior was first added to Android in
     * Ice Cream Sandwich.
     * <p/>
     * If none of the applications have a are installed on the device,
     * a Market link will be opened to the first application.
     * <p/>
     * Note that Android application records do not overrule
     * applications that have called
     * {@link android.nfc.NfcAdapter#enableForegroundDispatch}.
     *
     * @param packageName Android package name
     * @return Android application NDEF record
     */
    public static NdefRecord createApplicationRecord(String packageName) {
        return IMPL.createApplicationRecord(packageName);
    }

    private interface Impl {
        public NdefRecord createUri(Uri uri);

        public NdefRecord createApplicationRecord(String packageName);
    }

    private static class Impl_Base implements Impl {
        @Override
        public NdefRecord createUri(Uri uri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NdefRecord createApplicationRecord(String packageName) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static class Impl_Gingerbread implements Impl {
        /**
         * RTD Android app type. For use with {@literal TNF_EXTERNAL}.
         * <p/>
         * The payload of a record with type RTD_ANDROID_APP
         * should be the package name identifying an application.
         * Multiple RTD_ANDROID_APP records may be included
         * in a single {@link android.nfc.NdefMessage}.
         * <p/>
         * Use {@link #createApplicationRecord(String)} to create
         * RTD_ANDROID_APP records.
         */
        private static final byte[] RTD_ANDROID_APP = "android.com:pkg".getBytes();

        /**
         * NFC Forum "URI Record Type Definition"<p>
         * This is a mapping of "URI Identifier Codes" to URI string prefixes,
         * per section 3.2.2 of the NFC Forum URI Record Type Definition document.
         */
        private static final String[] URI_PREFIX_MAP = new String[]{
                "", // 0x00
                "http://www.", // 0x01
                "https://www.", // 0x02
                "http://", // 0x03
                "https://", // 0x04
                "tel:", // 0x05
                "mailto:", // 0x06
                "ftp://anonymous:anonymous@", // 0x07
                "ftp://ftp.", // 0x08
                "ftps://", // 0x09
                "sftp://", // 0x0A
                "smb://", // 0x0B
                "nfs://", // 0x0C
                "ftp://", // 0x0D
                "dav://", // 0x0E
                "news:", // 0x0F
                "telnet://", // 0x10
                "imap:", // 0x11
                "rtsp://", // 0x12
                "urn:", // 0x13
                "pop:", // 0x14
                "sip:", // 0x15
                "sips:", // 0x16
                "tftp:", // 0x17
                "btspp://", // 0x18
                "btl2cap://", // 0x19
                "btgoep://", // 0x1A
                "tcpobex://", // 0x1B
                "irdaobex://", // 0x1C
                "file://", // 0x1D
                "urn:epc:id:", // 0x1E
                "urn:epc:tag:", // 0x1F
                "urn:epc:pat:", // 0x20
                "urn:epc:raw:", // 0x21
                "urn:epc:", // 0x22
        };

        @Override
        public NdefRecord createUri(Uri uri) {
            if (uri == null) throw new NullPointerException("uri is null");

            uri = normalizeScheme(uri);
            String uriString = uri.toString();
            if ((uriString == null) || (uriString.length() == 0)) throw new IllegalArgumentException("uri is empty");

            byte prefix = 0;
            for (int i = 1; i < URI_PREFIX_MAP.length; i++) {
                if (uriString.startsWith(URI_PREFIX_MAP[i])) {
                    prefix = (byte) i;
                    uriString = uriString.substring(URI_PREFIX_MAP[i].length());
                    break;
                }
            }
            byte[] uriBytes = uriString.getBytes(Charset.defaultCharset());
            byte[] recordBytes = new byte[uriBytes.length + 1];
            recordBytes[0] = prefix;
            System.arraycopy(uriBytes, 0, recordBytes, 1, uriBytes.length);
            return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_URI, null, recordBytes);
        }

        @Override
        public NdefRecord createApplicationRecord(String packageName) {
            if (packageName == null) throw new NullPointerException("packageName is null");
            if (packageName.length() == 0) throw new IllegalArgumentException("packageName is empty");

            return new NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE, RTD_ANDROID_APP, null,
                    packageName.getBytes(Charset.defaultCharset()));
        }

        private static Uri normalizeScheme(Uri uri) {
            String scheme = uri.getScheme();
            if (scheme == null) return uri;  // give up
            String lowerScheme = scheme.toLowerCase(Locale.US);
            if (scheme.equals(lowerScheme)) return uri;  // no change

            return uri.buildUpon().scheme(lowerScheme).build();
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static class Impl_IceCreamSandwich implements Impl {
        @Override
        public NdefRecord createUri(Uri uri) {
            return NdefRecord.createUri(uri);
        }

        @Override
        public NdefRecord createApplicationRecord(String packageName) {
            return NdefRecord.createApplicationRecord(packageName);
        }
    }
}
