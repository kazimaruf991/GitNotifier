package com.kmmaruf.gitnotifier.ui.common;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.ui.MainActivity;

public class Common {

    public static void showOkayDialog(Context context, String title, String message) {
        new MaterialAlertDialogBuilder(context).setTitle(title).setMessage(message).setCancelable(false).setPositiveButton("OK", (dialog, which) -> dialog.dismiss()).show();
    }

    public static void showYesNoDialog(Context context, String title, String message, DialogInterface.OnClickListener yesListener, DialogInterface.OnClickListener noListener) {
        new MaterialAlertDialogBuilder(context).setTitle(title).setMessage(message).setCancelable(false).setPositiveButton("Yes", yesListener).setNegativeButton("No", noListener).show();
    }


    public static void showPasswordPrompt(Context context, String title, MainActivity.OnPasswordEntered callback) {
        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setHint(R.string.password);

        TextInputEditText inputEditText = new TextInputEditText(context);
        inputEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputLayout.addView(inputEditText);

        inputLayout.setPadding(32, 16, 32, 8);

        new MaterialAlertDialogBuilder(context).setTitle(title).setView(inputLayout).setPositiveButton("OK", (dialog, which) -> {
            String pwd = inputEditText.getText() != null ? inputEditText.getText().toString() : "";
            if (!pwd.isEmpty()) {
                callback.onPasswordProvided(pwd);
            } else {
                Toast.makeText(context, R.string.password_cannot_be_empty, Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton(R.string.cancel, null).show();
    }
}
