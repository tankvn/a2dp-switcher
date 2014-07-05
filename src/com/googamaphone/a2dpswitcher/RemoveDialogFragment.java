package com.googamaphone.a2dpswitcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class RemoveDialogFragment extends DialogFragment {
    private static final String KEY_ID = "device_id";
    private static final String KEY_NAME = "device_name";

    public static RemoveDialogFragment newInstance(int deviceId, String deviceName) {
        final RemoveDialogFragment renameFragment = new RemoveDialogFragment();
        final Bundle args = new Bundle();

        args.putInt(KEY_ID, deviceId);
        args.putString(KEY_NAME, deviceName);

        renameFragment.setArguments(args);

        return renameFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final int deviceId = args.getInt(KEY_ID);
        final String deviceName = args.getString(KEY_NAME);

        final OnClickListener onClickListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((MainActivity) getActivity()).setDeviceVisibility(deviceId, false);
            }
        };

        final String removeMessage = getString(R.string.confirm_hide_device, deviceName);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.menu_hide)
                .setMessage(removeMessage)
                .setPositiveButton(android.R.string.ok, onClickListener)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}