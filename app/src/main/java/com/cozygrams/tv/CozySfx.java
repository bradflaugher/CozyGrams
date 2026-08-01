package com.cozygrams.tv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class CozySfx {
    enum Sound { MOVE, FILL, CROSS, HINT, ERROR, WIN, SELECT, LINE, JOIN }

    private static final int SAMPLE_RATE = 22050;
    private volatile boolean enabled = true;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(6), runnable -> {
                Thread thread = new Thread(runnable, "cozy-sfx");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());

    void setEnabled(boolean value) {
        enabled = value;
    }

    void play(Sound sound) {
        if (enabled) worker.execute(() -> synthesize(sound));
    }

    private void synthesize(Sound sound) {
        Voice voice = voiceFor(sound);
        int count = SAMPLE_RATE * voice.durationMs / 1000;
        short[] data = new short[count];
        for (int i = 0; i < count; i++) {
            double time = (double) i / SAMPLE_RATE;
            double position = i / (double) count;
            double envelope = Math.sin(Math.PI * position);
            envelope *= Math.min(1, position * 18);
            int segment = Math.min(voice.notes.length - 1,
                    i * voice.notes.length / count);
            double frequency = voice.notes[segment];
            double tone = Math.sin(2 * Math.PI * frequency * time)
                    + .16 * Math.sin(4 * Math.PI * frequency * time)
                    + .05 * Math.sin(6 * Math.PI * frequency * time);
            data[i] = (short) (voice.volume * envelope * tone);
        }
        playBuffer(data, voice.durationMs);
    }

    private Voice voiceFor(Sound sound) {
        switch (sound) {
            case MOVE:
                return new Voice(28, 1500, 392);
            case FILL:
                return new Voice(78, 2400, 523, 659);
            case CROSS:
                return new Voice(62, 1900, 330, 262);
            case HINT:
                return new Voice(235, 2350, 659, 784, 1047, 1319);
            case ERROR:
                return new Voice(150, 1800, 220, 196, 175);
            case WIN:
                return new Voice(720, 2350, 523, 659, 784, 1047, 1319, 1568);
            case LINE:
                return new Voice(135, 2050, 659, 784, 988);
            case JOIN:
                return new Voice(210, 2150, 392, 523, 659);
            case SELECT:
            default:
                return new Voice(95, 2100, 440, 554);
        }
    }

    private void playBuffer(short[] data, int durationMs) {
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(data.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        track.write(data, 0, data.length);
        track.play();
        try {
            Thread.sleep(durationMs + 20L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            track.release();
        }
    }

    void release() {
        worker.shutdownNow();
    }

    private static final class Voice {
        final int durationMs;
        final int volume;
        final double[] notes;

        Voice(int durationMs, int volume, double... notes) {
            this.durationMs = durationMs;
            this.volume = volume;
            this.notes = notes;
        }
    }
}
