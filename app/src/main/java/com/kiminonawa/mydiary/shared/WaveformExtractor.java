package com.kiminonawa.mydiary.shared;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads the sound of a downloaded audio and makes its "waves": {@link #BARS} numbers from 0 to 1, one for each
 * little piece of the audio (how loud that piece is). They are saved next to the audio, so the second time
 * they appear at once. The work is done in the background, one audio at a time.
 */
public class WaveformExtractor {

    public static final int BARS = 100;

    private static final long MAX_WORK_MS = 150000;   // a very long audio is not worth waiting for
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        /**
         * @param peaks {@link #BARS} numbers from 0 to 1
         */
        void onWave(float[] peaks);
    }

    private WaveformExtractor() {
    }

    private static File waveFileFor(File audio) {
        return new File(audio.getPath() + ".wave");
    }

    /**
     * Gives the waves of the audio when it is on the phone (the callback is called on the main thread).
     * If the audio is not downloaded yet, nothing happens.
     */
    public static void loadAsync(final File audio, final Callback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                File waveFile = waveFileFor(audio);
                float[] peaks = read(waveFile);
                if (peaks == null && audio.exists() && audio.length() > 0) {
                    peaks = extract(audio);
                    if (peaks != null) {
                        write(waveFile, peaks);
                    }
                }
                if (peaks != null) {
                    final float[] result = peaks;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onWave(result);
                        }
                    });
                }
            }
        });
    }

    private static float[] read(File file) {
        if (!file.exists() || file.length() != BARS) {
            return null;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] bytes = new byte[BARS];
            int done = 0;
            while (done < BARS) {
                int n = in.read(bytes, done, BARS - done);
                if (n < 0) {
                    return null;
                }
                done += n;
            }
            float[] peaks = new float[BARS];
            for (int i = 0; i < BARS; i++) {
                peaks[i] = (bytes[i] & 0xff) / 255f;
            }
            return peaks;
        } catch (IOException e) {
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static void write(File file, float[] peaks) {
        FileOutputStream out = null;
        try {
            byte[] bytes = new byte[BARS];
            for (int i = 0; i < BARS; i++) {
                bytes[i] = (byte) Math.round(Math.max(0f, Math.min(1f, peaks[i])) * 255f);
            }
            out = new FileOutputStream(file);
            out.write(bytes);
        } catch (IOException ignored) {
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Opens the audio with the decoder of the phone (the same one that plays it) and measures how loud it is.
     * Returns null when the phone can't read it (then the waves are simply not shown).
     */
    private static float[] extract(File file) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(file.getAbsolutePath());
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    format = candidate;
                    break;
                }
            }
            if (format == null) {
                return null;
            }
            int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : 0;
            long estimatedFrames = durationUs > 0 ? durationUs * sampleRate / 1000000L : sampleRate * 300L;
            //About 1000 little pieces for the whole audio (then they are joined in 100 bars)
            int blockFrames = (int) Math.max(256, Math.min(4096, estimatedFrames / 1000));

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            Measurer measurer = new Measurer(format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2, blockFrames);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            long start = SystemClock.uptimeMillis();
            int idle = 0;
            while (!outputDone) {
                if (SystemClock.uptimeMillis() - start > MAX_WORK_MS) {
                    return null;
                }
                boolean worked = false;
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(5000);
                    if (in >= 0) {
                        worked = true;
                        ByteBuffer buffer = codec.getInputBuffer(in);
                        int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, 5000);
                if (out >= 0) {
                    worked = true;
                    if (info.size > 0) {
                        ByteBuffer buffer = codec.getOutputBuffer(out);
                        if (buffer != null) {
                            buffer.position(info.offset);
                            buffer.limit(info.offset + info.size);
                            measurer.add(buffer);
                        }
                    }
                    codec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                } else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    worked = true;
                    measurer.setFormat(codec.getOutputFormat());
                }
                idle = worked ? 0 : idle + 1;
                if (idle > 600) {
                    return null;
                }
            }
            return measurer.finish();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Exception ignored) {
            }
            extractor.release();
        }
    }

    /**
     * Receives the decoded sound (numbers) and keeps the loudest value of every little piece.
     */
    private static class Measurer {
        private int channels;
        private boolean isFloat = false;
        private final int blockFrames;
        private float[] blocks = new float[2048];
        private int blockCount = 0;
        private float blockMax = 0;
        private int inBlock = 0;
        private short[] shortTmp = new short[0];
        private float[] floatTmp = new float[0];

        Measurer(int channels, int blockFrames) {
            this.channels = Math.max(1, channels);
            this.blockFrames = blockFrames;
        }

        void setFormat(MediaFormat format) {
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channels = Math.max(1, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            }
            //"pcm-encoding" (Android 7+): 4 = decimal numbers, 2 = whole numbers of 16 bits (the usual one)
            isFloat = format.containsKey("pcm-encoding") && format.getInteger("pcm-encoding") == 4;
        }

        void add(ByteBuffer buffer) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            if (isFloat) {
                FloatBuffer fb = buffer.asFloatBuffer();
                int total = fb.remaining();
                if (floatTmp.length < total) {
                    floatTmp = new float[total];
                }
                fb.get(floatTmp, 0, total);
                for (int i = 0; i + channels <= total; i += channels) {
                    float m = 0;
                    for (int c = 0; c < channels; c++) {
                        float v = Math.abs(floatTmp[i + c]);
                        if (v > m) {
                            m = v;
                        }
                    }
                    push(m);
                }
            } else {
                ShortBuffer sb = buffer.asShortBuffer();
                int total = sb.remaining();
                if (shortTmp.length < total) {
                    shortTmp = new short[total];
                }
                sb.get(shortTmp, 0, total);
                for (int i = 0; i + channels <= total; i += channels) {
                    int m = 0;
                    for (int c = 0; c < channels; c++) {
                        int v = shortTmp[i + c];
                        if (v < 0) {
                            v = -v;
                        }
                        if (v > m) {
                            m = v;
                        }
                    }
                    push(m / 32768f);
                }
            }
        }

        private void push(float value) {
            if (value > blockMax) {
                blockMax = value;
            }
            if (++inBlock >= blockFrames) {
                closeBlock();
            }
        }

        private void closeBlock() {
            if (blockCount == blocks.length) {
                blocks = Arrays.copyOf(blocks, blocks.length * 2);
            }
            blocks[blockCount++] = blockMax;
            blockMax = 0;
            inBlock = 0;
        }

        /**
         * Joins the little pieces in {@link #BARS} bars and scales them (the loud part reaches the top).
         */
        float[] finish() {
            if (inBlock > 0) {
                closeBlock();
            }
            if (blockCount == 0) {
                return null;
            }
            float[] bars = new float[BARS];
            for (int i = 0; i < BARS; i++) {
                int from = (int) ((long) i * blockCount / BARS);
                int to = Math.max(from + 1, (int) ((long) (i + 1) * blockCount / BARS));
                to = Math.min(to, blockCount);
                from = Math.min(from, blockCount - 1);
                float max = 0;
                for (int j = from; j < to; j++) {
                    if (blocks[j] > max) {
                        max = blocks[j];
                    }
                }
                bars[i] = max;
            }
            //The reference is a loud (not the loudest) bar, so one single click does not flatten everything
            float[] sorted = bars.clone();
            Arrays.sort(sorted);
            float reference = sorted[(int) (BARS * 0.95f)];
            if (reference <= 0.0001f) {
                reference = sorted[BARS - 1];
            }
            for (int i = 0; i < BARS; i++) {
                float v = reference <= 0.0001f ? 0 : Math.min(1f, bars[i] / reference);
                //A soft curve: quiet parts can still be seen
                bars[i] = (float) Math.pow(v, 0.7);
            }
            return bars;
        }
    }
}
