package com.kiminonawa.mydiary.entries.diary.editor;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.FileManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Brings an audio file of the phone into the folder of the entry that is being written (like the photos). The system
 * window "choose a file" is used, so no permission is needed. The copy is made in the background.
 */
public class AudioFileImporter {

    public interface Listener {
        /**
         * The audio is ready in the folder of the entry.
         *
         * @param fileName    name of the file inside the folder (it is made up here)
         * @param displayName the name the file had on the phone (to show it)
         */
        void onAudioReady(String fileName, String displayName);
    }

    public static final int REQUEST_AUDIO_FILE = 7741;
    /**
     * The same limit as the audios that are downloaded from a link
     */
    public static final long MAX_BYTES = 150L * 1024 * 1024;

    private final Fragment host;
    private final Listener listener;
    private FileManager targetFolder;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean copying = false;

    public AudioFileImporter(Fragment host, Listener listener) {
        this.host = host;
        this.listener = listener;
    }

    public void setTargetFolder(FileManager targetFolder) {
        this.targetFolder = targetFolder;
    }

    /**
     * Opens the window to choose an audio file of the phone.
     */
    public void pick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        try {
            host.startActivityForResult(intent, REQUEST_AUDIO_FILE);
        } catch (Exception e) {
            Activity activity = host.getActivity();
            if (activity != null) {
                Toast.makeText(activity, R.string.audio_file_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Call it from onActivityResult of the fragment.
     *
     * @return true when the result was about the audio file (the fragment has nothing else to do with it)
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_AUDIO_FILE) {
            return false;
        }
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            copy(data.getData());
        }
        return true;
    }

    private void copy(final Uri uri) {
        final Activity activity = host.getActivity();
        if (activity == null || targetFolder == null) {
            return;
        }
        if (copying) {
            return;
        }
        copying = true;
        Toast.makeText(activity, R.string.audio_file_copying, Toast.LENGTH_SHORT).show();
        final ContentResolver resolver = activity.getContentResolver();
        final File folder = targetFolder.getDir();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String displayName = null;
                long size = -1;
                try {
                    Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                displayName = cursor.getString(0);
                                size = cursor.isNull(1) ? -1 : cursor.getLong(1);
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                } catch (Exception ignored) {
                    //The name is only to show it
                }
                if (size > MAX_BYTES) {
                    finish(null, null, R.string.audio_file_too_big);
                    return;
                }
                String extension = extensionOf(resolver, uri, displayName);
                String fileName = UUID.randomUUID().toString() + "." + extension;
                File target = new File(folder, fileName);
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    //noinspection ResultOfMethodCallIgnored
                    folder.mkdirs();
                    in = resolver.openInputStream(uri);
                    if (in == null) {
                        throw new IOException("no stream");
                    }
                    out = new FileOutputStream(target);
                    byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        total += read;
                        if (total > MAX_BYTES) {
                            throw new TooBigException();
                        }
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                    finish(fileName, displayName == null || displayName.length() == 0 ? fileName : displayName, 0);
                } catch (TooBigException e) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    finish(null, null, R.string.audio_file_too_big);
                } catch (Exception e) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    finish(null, null, R.string.audio_file_error);
                } finally {
                    close(in);
                    close(out);
                }
            }
        }).start();
    }

    private void finish(final String fileName, final String displayName, final int errorMessage) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                copying = false;
                Activity activity = host.getActivity();
                if (activity == null || !host.isAdded()) {
                    return;
                }
                if (fileName != null) {
                    listener.onAudioReady(fileName, displayName);
                } else {
                    Toast.makeText(activity, errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private static String extensionOf(ContentResolver resolver, Uri uri, String displayName) {
        String extension = null;
        if (displayName != null && displayName.lastIndexOf('.') > 0) {
            extension = displayName.substring(displayName.lastIndexOf('.') + 1);
        }
        if (extension == null || !extension.matches("[A-Za-z0-9]{1,5}")) {
            String type = resolver.getType(uri);
            extension = type == null ? null : MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
        }
        if (extension == null || !extension.matches("[A-Za-z0-9]{1,5}")) {
            extension = "m4a";
        }
        return extension.toLowerCase(Locale.US);
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static class TooBigException extends IOException {
    }
}
