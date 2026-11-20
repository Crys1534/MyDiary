package com.kiminonawa.mydiary.entries.diary.editor;

import android.app.Activity;
import android.content.ContentResolver;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Brings videos of the phone (chosen in the gallery) into the folder of the entry that is being written, like the
 * photos. The copy is made in the background, one video after the other.
 */
public class VideoImporter {

    public interface Listener {
        void onVideoReady(String fileName);
    }

    /**
     * A video bigger than this is not copied (the backup and the phone would suffer)
     */
    public static final long MAX_BYTES = 500L * 1024 * 1024;

    private final Fragment host;
    private final Listener listener;
    private FileManager targetFolder;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Uri> queue = new ArrayList<>();
    private boolean copying = false;

    public VideoImporter(Fragment host, Listener listener) {
        this.host = host;
        this.listener = listener;
    }

    public void setTargetFolder(FileManager targetFolder) {
        this.targetFolder = targetFolder;
    }

    public static boolean isVideo(ContentResolver resolver, Uri uri) {
        try {
            String type = resolver.getType(uri);
            if (type != null) {
                return type.startsWith("video/");
            }
        } catch (Exception ignored) {
        }
        String path = uri.toString().toLowerCase(Locale.US);
        return path.endsWith(".mp4") || path.endsWith(".3gp") || path.endsWith(".webm") || path.endsWith(".mkv")
                || path.endsWith(".mov") || path.contains("/video/");
    }

    public void addUris(List<Uri> uris) {
        queue.addAll(uris);
        Activity activity = host.getActivity();
        if (activity != null && !uris.isEmpty()) {
            Toast.makeText(activity, R.string.video_copying, Toast.LENGTH_SHORT).show();
        }
        next();
    }

    private void next() {
        if (copying || queue.isEmpty()) {
            return;
        }
        Activity activity = host.getActivity();
        if (activity == null || targetFolder == null) {
            queue.clear();
            return;
        }
        copying = true;
        final Uri uri = queue.remove(0);
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
                }
                if (size > MAX_BYTES) {
                    finish(null, R.string.video_too_big);
                    return;
                }
                String fileName = UUID.randomUUID().toString() + "." + extensionOf(resolver, uri, displayName);
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
                    byte[] buffer = new byte[128 * 1024];
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
                    finish(fileName, 0);
                } catch (TooBigException e) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    finish(null, R.string.video_too_big);
                } catch (Exception e) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    finish(null, R.string.video_error);
                } finally {
                    close(in);
                    close(out);
                }
            }
        }).start();
    }

    private void finish(final String fileName, final int errorMessage) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                copying = false;
                Activity activity = host.getActivity();
                if (activity == null || !host.isAdded()) {
                    queue.clear();
                    return;
                }
                if (fileName != null) {
                    listener.onVideoReady(fileName);
                } else {
                    Toast.makeText(activity, errorMessage, Toast.LENGTH_LONG).show();
                }
                next();
            }
        });
    }

    private static String extensionOf(ContentResolver resolver, Uri uri, String displayName) {
        String extension = null;
        if (displayName != null && displayName.lastIndexOf('.') > 0) {
            extension = displayName.substring(displayName.lastIndexOf('.') + 1);
        }
        if (extension == null || !extension.matches("[A-Za-z0-9]{1,5}")) {
            String type = null;
            try {
                type = resolver.getType(uri);
            } catch (Exception ignored) {
            }
            extension = type == null ? null : MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
        }
        if (extension == null || !extension.matches("[A-Za-z0-9]{1,5}")) {
            extension = "mp4";
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
