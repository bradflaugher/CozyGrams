package com.cozygrams.tv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** A tiny original music box score synthesized at runtime to keep the APK lean. */
final class CozyMusic {
    private static final int SAMPLE_RATE = 22050;
    private static final double[] MELODY = {
            329.63, 392.00, 523.25, 493.88, 440.00, 392.00, 329.63, 293.66,
            261.63, 329.63, 392.00, 440.00, 392.00, 329.63, 293.66, 261.63,
            329.63, 440.00, 523.25, 659.25, 587.33, 523.25, 440.00, 392.00,
            293.66, 392.00, 493.88, 587.33, 523.25, 440.00, 392.00, 329.63
    };
    private static final double[] BASS = {
            130.81, 130.81, 110.00, 110.00, 87.31, 87.31, 98.00, 98.00,
            130.81, 130.81, 146.83, 146.83, 110.00, 110.00, 98.00, 98.00
    };

    private AudioTrack track;
    private Thread thread;
    private volatile boolean playing;
    private boolean enabled = true;

    synchronized void setEnabled(boolean value) {
        enabled = value;
        if (value) start(); else stop();
    }

    synchronized void start() {
        if (playing || !enabled) return;
        int minimum = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(minimum, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        playing = true;
        track.play();
        thread = new Thread(this::musicLoop, "cozy-music");
        thread.setDaemon(true);
        thread.start();
    }

    private void musicLoop() {
        int beat = 0;
        short[] buffer = new short[SAMPLE_RATE / 2];
        while (playing) {
            synthesizeBeat(buffer, beat++);
            AudioTrack current = track;
            if (current != null && current.write(buffer, 0, buffer.length) < 0) break;
        }
    }

    private void synthesizeBeat(short[] buffer, int beat) {
        double note = MELODY[beat % MELODY.length];
        double bass = BASS[(beat / 2) % BASS.length];
        double harmony = note * (beat % 8 < 4 ? 1.25 : 1.20);
        for (int i = 0; i < buffer.length; i++) {
            double position = i / (double) buffer.length;
            double time = (double) i / SAMPLE_RATE;
            double bellEnvelope = Math.sin(Math.PI * position) * Math.exp(-position * 1.4);
            double bell = Math.sin(2 * Math.PI * note * time)
                    + .20 * Math.sin(4 * Math.PI * note * time)
                    + .06 * Math.sin(6 * Math.PI * note * time);
            double softHarmony = Math.sin(2 * Math.PI * harmony * time) * .12;
            double padEnvelope = .72 + .28 * Math.sin(Math.PI * position);
            double pad = Math.sin(2 * Math.PI * bass * time)
                    + .26 * Math.sin(3 * Math.PI * bass * time);
            buffer[i] = (short) (920 * bellEnvelope * (bell + softHarmony)
                    + 490 * padEnvelope * pad);
        }
    }

    synchronized void stop() {
        playing = false;
        AudioTrack oldTrack = track;
        track = null;
        if (oldTrack != null) {
            oldTrack.pause();
            oldTrack.flush();
        }
        Thread oldThread = thread;
        thread = null;
        if (oldThread != null && oldThread != Thread.currentThread()) {
            try {
                oldThread.join(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (oldTrack != null) oldTrack.release();
    }
}
