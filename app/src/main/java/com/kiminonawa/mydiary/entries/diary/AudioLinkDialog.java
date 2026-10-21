package com.kiminonawa.mydiary.entries.diary;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.AudioLink;
import com.kiminonawa.mydiary.shared.ScreenHelper;

/**
 * Small window to paste the link of an audio (for example of Google Drive).
 * If the phone has a link copied, it is already written in the box.
 */
public class AudioLinkDialog {

    public interface Callback {
        void onAudioLink(String link);
    }

    public static void show(final Context context, final Callback callback) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint(R.string.audio_dialog_hint);
        String copied = readClipboard(context);
        if (AudioLink.isValid(copied)) {
            input.setText(copied.trim());
            input.setSelection(input.getText().length());
        }
        int margin = ScreenHelper.dpToPixel(context.getResources(), 20);
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        params.rightMargin = margin;
        container.addView(input, params);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.audio_button_title)
                .setMessage(R.string.audio_dialog_message)
                .setView(container)
                .setPositiveButton(R.string.dialog_button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        String link = input.getText().toString().trim();
                        if (AudioLink.isValid(link)) {
                            callback.onAudioLink(link);
                        } else {
                            Toast.makeText(context, R.string.audio_error_link, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    private static String readClipboard(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
                    return clip.getItemAt(0).getText().toString();
                }
            }
        } catch (Exception e) {
            //No clipboard: the box stays empty
        }
        return "";
    }
}
