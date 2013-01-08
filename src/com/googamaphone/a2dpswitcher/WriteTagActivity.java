/*
 * Copyright (C) 2013 Alan Viverette
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googamaphone.a2dpswitcher;

import com.googamaphone.utils.NfcUtils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

/**
 * Activity used for writing URIs to NFC tags.
 * <p>
 * Must specify the URI to write with extra {@link #EXTRA_URI}. Optionally, set
 * {@link #EXTRA_PACKAGE} to specify the app package that should be used to
 * handle the NFC tag.
 */
public class WriteTagActivity extends Activity {
    /** Extra representing the URI to write to an NFC tag. */
    public static final String EXTRA_URI = "uri";

    /** Extra representing the app package that should handle the NFC tag. */
    public static final String EXTRA_PACKAGE = "package";

    /** Broadcast action sent by the system when an NFC tag is detected. */
    private static final String BROADCAST_WRITE_TAG = "com.googlecode.eyesfree.nfc.WRITE_TAG";

    /** Delay in milliseconds before finishing after a successful write. */
    private static final long DELAY_SUCCESS = 1000;

    /** Delay in milliseconds before finishing after a failed write. */
    private static final long DELAY_FAILURE = 2000;

    /** The default NFC adapter. */
    private NfcAdapter mNfcAdapter;

    /** The URI to write to the NFC tag. */
    private Uri mUri;

    /** The package to write to the NFC tag. */
    private String mPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.dialog_waiting);

        final TextView message = (TextView) findViewById(R.id.message);
        message.setText(R.string.progress_waiting_for_tag);

        final Intent intent = getIntent();
        mUri = intent.getParcelableExtra(EXTRA_URI);
        mPackage = intent.getStringExtra(EXTRA_PACKAGE);

        if (mUri == null) {
            finish();
            return;
        }

        final NfcManager nfcManager = (NfcManager) getSystemService(NFC_SERVICE);
        if (nfcManager == null) {
            finish();
            return;
        }

        mNfcAdapter = nfcManager.getDefaultAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerForegroundDispatch();
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterForegroundDispatch();
    }

    /**
     * Enables foreground dispatch and registers a broadcast listener so that
     * the system sends {@link #BROADCAST_WRITE_TAG} and this activity is
     * notified when a supported NFC tag is detected.
     */
    private void registerForegroundDispatch() {
        final Intent intent = new Intent(BROADCAST_WRITE_TAG).setPackage(getPackageName());
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        final String[][] techFilters = new String[][] {
                {
                        Ndef.class.getName()
            }
        };

        registerReceiver(mNfcReceiver, new IntentFilter(BROADCAST_WRITE_TAG));

        mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, techFilters);
    }

    /**
     * Disables foreground dispatch and unregisters the broadcast listener.
     *
     * @see #registerForegroundDispatch()
     */
    private void unregisterForegroundDispatch() {
        mNfcAdapter.disableForegroundDispatch(this);

        unregisterReceiver(mNfcReceiver);
    }

    /**
     * Called when a supported NFC tag is detected. Attempts to write
     * {@link #mUri} and {@link #mPackage} to the tag.
     *
     * @param detectedTag The detected NFC tag.
     */
    private void onTagDetected(Tag detectedTag) {
        if (NfcUtils.writeUriToTag(detectedTag, mUri, mPackage)) {
            showSuccessAndFinish();
        } else {
            showFailureAndFinish(R.string.failure_write_tag);
        }
    }

    /**
     * Sets the layout to success, sets the activity result to okay, and
     * finishes after a delay.
     */
    private void showSuccessAndFinish() {
        setContentView(R.layout.dialog_success);

        setResult(RESULT_OK);
        mHandler.postDelayed(mDelayedFinish, DELAY_SUCCESS);
    }

    /**
     * Sets the layout to failure with the specified message, sets the activity
     * result to cancelled, and finishes after a delay.
     *
     * @param resId The message to display.
     */
    private void showFailureAndFinish(int resId) {
        setContentView(R.layout.dialog_failure);

        final TextView message = (TextView) findViewById(R.id.message);
        message.setText(resId);

        setResult(RESULT_CANCELED);
        mHandler.postDelayed(mDelayedFinish, DELAY_FAILURE);
    }

    /**
     * Broadcast received used to handle NFC tag detection.
     */
    private final BroadcastReceiver mNfcReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BROADCAST_WRITE_TAG.equals(action)) {
                final Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                if (detectedTag != null) {
                    onTagDetected(detectedTag);
                }
            }

        }
    };

    private final Handler mHandler = new Handler();

    /**
     * Runnable used to finish the app after a delay.
     */
    private final Runnable mDelayedFinish = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };
}
