package com.melonloader.installer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class GenericDialogFrament extends DialogFragment {
    private String message;
    public Runnable after = null;
    public Runnable afterPositive = null;
    public String negativeAnswer = null;

    private boolean positiveAnswer = false;

    public GenericDialogFrament(String message)
    {
        this.message = message;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message)
                .setPositiveButton("ok", (DialogInterface dialog, int id) -> {
                    positiveAnswer = true;
                });

        if (negativeAnswer != null) {
            builder.setNegativeButton(negativeAnswer, (DialogInterface dialog, int id) -> {});
        }
//                .setOnCancelListener((DialogInterface dialog) -> after.run())
//                .setOnDismissListener((DialogInterface dialog) -> after.run());

        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onDestroy() {
        if (positiveAnswer && afterPositive != null) {
            afterPositive.run();
        }

        if (after != null) {
            after.run();
        }
        super.onDestroy();
    }
}
