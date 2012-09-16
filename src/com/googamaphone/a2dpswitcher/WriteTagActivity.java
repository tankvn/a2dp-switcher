/*
 * Copyright (C) 2012 Google Inc.
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
import android.os.Message;
import android.widget.TextView;

import com.googamaphone.utils.NfcUtils;
import com.googamaphone.utils.WeakReferenceHandler;


public class WriteTagActivity extends Activity {
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_PACKAGE = "package";

    private static final long DELAY_SUCCESS = 1000;
    private static final long DELAY_FAILURE = 2000;

    private static final String BROADCAST_WRITE_TAG = "com.googlecode.eyesfree.nfc.WRITE_TAG";

    private NfcAdapter mNfcAdapter;
    private Uri mUri;
    private String mPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.dialog_waiting);

        final NfcManager nfcManager = (NfcManager) getSystemService(NFC_SERVICE);

        mNfcAdapter = nfcManager.getDefaultAdapter();
        mUri = getIntent().getParcelableExtra(EXTRA_URI);
        mPackage = getIntent().getStringExtra(EXTRA_PACKAGE);

        if (mUri == null) {
            finish();
        }
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

    private void registerForegroundDispatch() {
        final Intent intent = new Intent(BROADCAST_WRITE_TAG).setPackage(getPackageName());
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        final String[][] techFilters = new String[][] { { Ndef.class.getName() } };

        registerReceiver(mNfcReceiver, new IntentFilter(BROADCAST_WRITE_TAG));

        mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, techFilters);
    }

    private void unregisterForegroundDispatch() {
        mNfcAdapter.disableForegroundDispatch(this);

        unregisterReceiver(mNfcReceiver);
    }

    private void onTagDetected(Tag detectedTag) {
        if (NfcUtils.writeUriToTag(detectedTag, mUri, mPackage)) {
            setResult(RESULT_OK);
            showSuccess();
        } else {
            setResult(RESULT_CANCELED);
            showFailure(R.string.failure_write_tag);
        }
    }

    private void showSuccess() {
        setContentView(R.layout.dialog_success);

        mHandler.delayFinish(DELAY_SUCCESS);
    }

    private void showFailure(int resId) {
        setContentView(R.layout.dialog_failure);

        final TextView message = (TextView) findViewById(R.id.message);
        message.setText(resId);

        mHandler.delayFinish(DELAY_FAILURE);
    }

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

    private final WriteTagHandler mHandler = new WriteTagHandler(this);

    private class WriteTagHandler extends WeakReferenceHandler<WriteTagActivity> {
        private static final int DELAYED_FINISH = 1;

        public WriteTagHandler(WriteTagActivity parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, WriteTagActivity parent) {
            switch (msg.what) {
                case DELAYED_FINISH:
                    parent.finish();
                    break;
            }
        }

        public void delayFinish(long delayMillis) {
            sendEmptyMessageDelayed(DELAYED_FINISH, delayMillis);
        }
    }
}
