package com.kmmaruf.gitnotifier.ui.common;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.kmmaruf.gitnotifier.R;
import com.kmmaruf.gitnotifier.ui.MainActivity;

public class Common {

    public static void showOkayDialog(Context context, String title, String message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static void showYesNoDialog(Context context, String title, String message,
                                       DialogInterface.OnClickListener yesListener,
                                       DialogInterface.OnClickListener noListener) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, yesListener)
                .setNegativeButton(android.R.string.cancel, noListener)
                .show();
    }

    public static void showPasswordPrompt(Context context, String title, MainActivity.OnPasswordEntered callback) {
        // Material 3 outlined TextInputLayout
        TextInputLayout inputLayout = new TextInputLayout(context, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        inputLayout.setHint(R.string.password);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText inputEditText = new TextInputEditText(inputLayout.getContext());
        inputEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputLayout.addView(inputEditText);

        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, pad / 2, pad, 0);
        container.addView(inputLayout, lp);

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String pwd = inputEditText.getText() != null ? inputEditText.getText().toString() : "";
                    if (!pwd.isEmpty()) {
                        callback.onPasswordProvided(pwd);
                    } else {
                        Toast.makeText(context, R.string.password_cannot_be_empty, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
