package com.pexel.vibfinder.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;

import androidx.annotation.LayoutRes;
import androidx.annotation.MainThread;

public class DialogBuilder {

    public static final String TAG = DialogBuilder.class.getSimpleName();

    private static final int[] BUTTONS = {
            Dialog.BUTTON_NEGATIVE,
            Dialog.BUTTON_POSITIVE,
            Dialog.BUTTON_NEUTRAL};

    private static final OnClickListener DO_NOTHING = dialog -> {
    };

    private final Context context;
    private final AlertDialog.Builder builder;
    private boolean autoDismiss = true;
    private OnClickListener positiveOnClick = DO_NOTHING;
    private OnClickListener negativeOnClick = DO_NOTHING;
    private OnClickListener neutralOnClick = DO_NOTHING;
    private DialogInterface.OnShowListener onShowListener;
    private DialogInterface.OnCancelListener onCancelListener;

    /* Dialog mutations */

    private DialogBuilder(Context context) {
        this.context = context;
        this.builder = new AlertDialog.Builder(context);

        builder.setCancelable(false);
    }

    @MainThread
    public static DialogBuilder start(Context context) {
        return new DialogBuilder(context);
    }

    public DialogBuilder content(CharSequence content) {
        builder.setMessage(content);
        return this;
    }

    public DialogBuilder title(CharSequence title) {
        builder.setTitle(title);
        return this;
    }

    public DialogBuilder noAutoDismiss() {
        autoDismiss = false;
        return this;
    }

    public DialogBuilder cancelable() {
        builder.setCancelable(true);
        return this;
    }

    public DialogBuilder cancelable(boolean cancelable) {
        builder.setCancelable(cancelable);
        return this;
    }

    public DialogBuilder layout(@LayoutRes int view) {
        builder.setView(view);
        return this;
    }

    public DialogBuilder positive() {
        return positive("OK");
    }

    public DialogBuilder positive(String text) {
        builder.setPositiveButton(text, null);
        return this;
    }

    public DialogBuilder positive(String text, OnClickListener onClick) {
        builder.setPositiveButton(text, null);
        positiveOnClick = onClick;
        return this;
    }

    public DialogBuilder negative() {
        return negative("Abbruch");
    }

    public DialogBuilder negative(String text) {
        builder.setNegativeButton(text, null);
        return this;
    }

    public DialogBuilder negative(String text, OnClickListener onClick) {
        builder.setNegativeButton(text, null);
        negativeOnClick = onClick;
        return this;
    }

    public DialogBuilder neutral(String text) {
        builder.setNeutralButton(text, null);
        return this;
    }

    /* Build & show */

    public DialogBuilder onShow(Dialog.OnShowListener onShowListener) {
        this.onShowListener = onShowListener;
        return this;
    }

    public DialogBuilder onCancel(Dialog.OnCancelListener onCancelListener) {
        this.onCancelListener = onCancelListener;
        return this;
    }

    /* Stuff */

    public Dialog show() {
        return build();
    }

    public Dialog build() {

        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(di -> {
            View messageView = dialog.findViewById(android.R.id.message);

            for (final int button : BUTTONS) {
                Button btn = dialog.getButton(button);
                if (btn != null)
                    btn.setOnClickListener(v -> onButtonClicked(button, dialog));
            }

            if (onShowListener != null)
                onShowListener.onShow(dialog);
        });

        if (onCancelListener != null)
            dialog.setOnCancelListener(onCancelListener);

        return dialog;
    }

    private void onButtonClicked(int button, AlertDialog dialog) {
        if (button == Dialog.BUTTON_POSITIVE)
            positiveOnClick.onClick(dialog);

        if (button == Dialog.BUTTON_NEGATIVE)
            negativeOnClick.onClick(dialog);

        if (button == Dialog.BUTTON_NEUTRAL)
            neutralOnClick.onClick(dialog);

        if (autoDismiss)
            dialog.dismiss();
    }

    public interface OnClickListener {
        void onClick(Dialog dialog);
    }
}
