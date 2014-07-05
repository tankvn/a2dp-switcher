package com.googamaphone.a2dpswitcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class RenameDialogFragment extends DialogFragment {
    private static final String KEY_ID = "device_id";
    private static final String KEY_NAME = "device_name";

    public static RenameDialogFragment newInstance(int deviceId, String deviceName) {
        final RenameDialogFragment renameFragment = new RenameDialogFragment();
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
                final AlertDialog alertDialog = (AlertDialog) dialog;
                final EditText editText = (EditText) alertDialog.findViewById(R.id.device_name);
                final CharSequence text = editText.getText();
                final String deviceName = (text == null ? null : text.toString());

                ((MainActivity) getActivity()).setDeviceName(deviceId, deviceName);
            }
        };

        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final View renameDialogView = inflater.inflate(R.layout.rename_dialog, null);

        final EditText editText = (EditText) renameDialogView.findViewById(R.id.device_name);
        editText.setText(deviceName);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.menu_rename)
                .setView(renameDialogView)
                .setPositiveButton(android.R.string.ok, onClickListener)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}