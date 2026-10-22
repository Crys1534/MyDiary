package com.kiminonawa.mydiary.shared;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Does a job in a background thread and gives the answer back in the main thread.
 * <p>
 * It is used like the old Android "AsyncTask" (which Android has deprecated) so the tasks of the app keep their
 * shape: {@link #doInBackground} runs in the background, {@link #onPostExecute} in the main thread.
 * The jobs run one after the other (like AsyncTask did), so two saves never write the database at the same time.
 */
public abstract class BackgroundTask<Params, Result> {

    private static final String TAG = "BackgroundTask";

    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "mydiary-background");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setDaemon(true);
            return thread;
        }
    });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private volatile boolean cancelled = false;
    private Future<?> future;

    /**
     * Main thread, just before the job starts.
     */
    protected void onPreExecute() {
    }

    /**
     * Background thread: the job itself.
     */
    @SuppressWarnings("unchecked")
    protected abstract Result doInBackground(Params... params);

    /**
     * Main thread, when the job is finished (not called when it was cancelled).
     */
    protected void onPostExecute(Result result) {
    }

    /**
     * Main thread, when the job was cancelled.
     */
    protected void onCancelled() {
    }

    public final boolean isCancelled() {
        return cancelled;
    }

    public final boolean cancel(boolean mayInterruptIfRunning) {
        cancelled = true;
        if (future != null) {
            future.cancel(mayInterruptIfRunning);
        }
        return true;
    }

    @SafeVarargs
    public final BackgroundTask<Params, Result> execute(final Params... params) {
        onPreExecute();
        future = SERIAL.submit(new Runnable() {
            @Override
            public void run() {
                Result result = null;
                try {
                    if (!cancelled) {
                        result = doInBackground(params);
                    }
                } catch (final Throwable error) {
                    Log.e(TAG, "The background job failed", error);
                    //Same as AsyncTask: a failed job is not hidden
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            throw new RuntimeException("The background job failed", error);
                        }
                    });
                    return;
                }
                final Result finalResult = result;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cancelled) {
                            onCancelled();
                        } else {
                            onPostExecute(finalResult);
                        }
                    }
                });
            }
        });
        return this;
    }

    /**
     * Runs something in the main thread from the background job.
     */
    protected final void runOnMain(Runnable runnable) {
        MAIN.post(runnable);
    }
}
