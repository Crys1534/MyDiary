package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Downloads the audio of a link to the temporary memory of the app (the cache of the phone) so it can be
 * played from there: that way every format works (m4a, mp3, ...), the bar can be moved, and the second
 * time it is played there is nothing to download. The cache is cleaned when it gets big (and Android
 * also cleans it when the phone needs space). The audio is NOT part of the diary backup: only the link is.
 */
public class AudioDownloader {

    public static final int ERROR_NOT_SHARED = 1;   // Google (or the web) does not allow to download it
    public static final int ERROR_PAGE = 2;         // it answered with a web page, not with an audio
    public static final int ERROR_TOO_BIG = 3;
    public static final int ERROR_NETWORK = 4;
    public static final int ERROR_NOT_HTTPS = 5;

    public static final long MAX_BYTES = 150L * 1024 * 1024;
    private static final long CACHE_LIMIT_BYTES = 300L * 1024 * 1024;
    private static final long CACHE_KEEP_BYTES = 150L * 1024 * 1024;

    public interface Listener {
        /**
         * @param percent 0..100, or -1 when the size is not known
         */
        void onProgress(int percent, long bytes);

        void onDone(File file);

        void onError(int reason);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled = false;

    /**
     * The file where the audio of that address is (or will be) saved.
     */
    public static File cacheFileFor(Context context, String url) {
        File dir = new File(context.getCacheDir(), "audio");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, sha1(url) + ".audio");
    }

    /**
     * Starts the download in the background. If the file is already in the cache, it is answered right away.
     */
    public void start(final Context context, final String url, final Listener listener) {
        final File target = cacheFileFor(context, url);
        if (target.exists() && target.length() > 0) {
            //Used now: it is the newest one for the cleaning
            //noinspection ResultOfMethodCallIgnored
            target.setLastModified(System.currentTimeMillis());
            listener.onDone(target);
            return;
        }
        cancelled = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                pruneCache(target.getParentFile());
                download(url, target, listener);
            }
        }).start();
    }

    public void cancel() {
        cancelled = true;
    }

    private void download(String url, File target, Listener listener) {
        File part = new File(target.getPath() + ".part");
        HttpURLConnection connection = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) MyDiary");
            int code = connection.getResponseCode();
            if (code == 401 || code == 403 || code == 404) {
                fail(listener, ERROR_NOT_SHARED);
                return;
            }
            if (code != 200) {
                fail(listener, ERROR_PAGE);
                return;
            }
            String type = connection.getContentType();
            if (type != null && type.toLowerCase(Locale.US).startsWith("text/html")) {
                //For example the page of Google Drive that asks to sign in or to confirm
                fail(listener, ERROR_PAGE);
                return;
            }
            long total = connection.getContentLength();
            if (total > MAX_BYTES) {
                fail(listener, ERROR_TOO_BIG);
                return;
            }
            in = connection.getInputStream();
            out = new FileOutputStream(part);
            byte[] buffer = new byte[16 * 1024];
            long done = 0;
            long lastReport = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled) {
                    return;
                }
                out.write(buffer, 0, read);
                done += read;
                if (done > MAX_BYTES) {
                    fail(listener, ERROR_TOO_BIG);
                    return;
                }
                long now = SystemClock.uptimeMillis();
                if (now - lastReport > 250) {
                    lastReport = now;
                    report(listener, total > 0 ? (int) (done * 100 / total) : -1, done);
                }
            }
            out.close();
            out = null;
            if (done == 0) {
                fail(listener, ERROR_PAGE);
                return;
            }
            if (!part.renameTo(target)) {
                fail(listener, ERROR_NETWORK);
                return;
            }
            deliver(listener, target);
        } catch (IOException e) {
            String message = e.getMessage();
            if (message != null && message.toLowerCase(Locale.US).contains("cleartext")) {
                fail(listener, ERROR_NOT_HTTPS);
            } else {
                fail(listener, ERROR_NETWORK);
            }
        } catch (RuntimeException e) {
            fail(listener, ERROR_NETWORK);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ignored) {
            }
            if (connection != null) {
                connection.disconnect();
            }
            if (part.exists()) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
            }
        }
    }

    private void report(final Listener listener, final int percent, final long bytes) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (!cancelled) {
                    listener.onProgress(percent, bytes);
                }
            }
        });
    }

    private void deliver(final Listener listener, final File file) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (!cancelled) {
                    listener.onDone(file);
                }
            }
        });
    }

    private void fail(final Listener listener, final int reason) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (!cancelled) {
                    listener.onError(reason);
                }
            }
        });
    }

    /**
     * When the downloaded audios use too much space, the oldest ones are deleted.
     */
    private static void pruneCache(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        long total = 0;
        for (File file : files) {
            total += file.length();
        }
        if (total <= CACHE_LIMIT_BYTES) {
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (File file : files) {
            if (total <= CACHE_KEEP_BYTES) {
                break;
            }
            total -= file.length();
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static String sha1(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
