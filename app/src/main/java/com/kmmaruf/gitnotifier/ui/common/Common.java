package com.kmmaruf.gitnotifier.ui.common;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
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

    /**
     * Password prompt with optional message (e.g. backup file name), visibility toggle,
     * and non-dismissing validation via {@link PasswordSubmitCallback}.
     */
    public static void showPasswordPrompt(Context context, String title, String message,
                                          PasswordSubmitCallback callback) {
        TextInputLayout inputLayout = new TextInputLayout(context, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        inputLayout.setHint(R.string.password);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        TextInputEditText inputEditText = new TextInputEditText(inputLayout.getContext());
        inputEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputLayout.addView(inputEditText);

        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int hPad = pad;
        column.setPadding(hPad, pad / 2, hPad, 0);

        if (message != null && !message.isEmpty()) {
            TextView msgView = new TextView(context);
            msgView.setText(message);
            msgView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            msgView.setPadding(0, 0, 0, pad / 2);
            column.addView(msgView);
        }
        column.addView(inputLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(column)
                .setPositiveButton(android.R.string.ok, null) // override below so we control dismiss
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(true)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button ok = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                inputLayout.setError(null);
                String pwd = inputEditText.getText() != null ? inputEditText.getText().toString() : "";
                if (pwd.isEmpty()) {
                    inputLayout.setError(context.getString(R.string.password_cannot_be_empty));
                    return;
                }
                callback.onSubmit(pwd, dialog, inputLayout);
            });
        });
        dialog.show();
    }

    /** Legacy overload used for backup (no file message). */
    public static void showPasswordPrompt(Context context, String title, MainActivity.OnPasswordEntered callback) {
        showPasswordPrompt(context, title, null, (pwd, dialog, layout) -> {
            dialog.dismiss();
            callback.onPasswordProvided(pwd);
        });
    }

    public interface PasswordSubmitCallback {
        /**
         * Called when the user taps OK with a non-empty password.
         * Dismiss {@code dialog} only when the password is accepted.
         * Call {@code layout.setError(...)} to keep the dialog open on failure.
         */
        void onSubmit(String password, androidx.appcompat.app.AlertDialog dialog, TextInputLayout layout);
    }
}

