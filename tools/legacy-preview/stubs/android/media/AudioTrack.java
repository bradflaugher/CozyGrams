package android.media;

/**
 * Desktop stand-in for {@code android.media.AudioTrack}.
 *
 * <p>v1.2.0's {@code CozyGameView} owns a {@code CozyMusic} and a {@code CozySfx}, so
 * both have to compile — but neither is ever exercised by the harness: the music only
 * starts from {@code resume()} and effects only from key handlers, and the driver calls
 * neither. Building a track therefore throws rather than pretending to make sound, so a
 * future change that does reach the audio path fails loudly instead of silently.
 */
public class AudioTrack {

    public static final int MODE_STATIC = 0;
    public static final int MODE_STREAM = 1;

    private AudioTrack() {
    }

    public static int getMinBufferSize(int sampleRate, int channelConfig,
                                       int audioFormat) {
        return 4096;
    }

    public void play() {
    }

    public void pause() {
    }

    public void flush() {
    }

    public void release() {
    }

    public int write(short[] data, int offset, int size) {
        return 0;
    }

    public static class Builder {

        public Builder setAudioAttributes(AudioAttributes attributes) {
            return this;
        }

        public Builder setAudioFormat(AudioFormat format) {
            return this;
        }

        public Builder setBufferSizeInBytes(int size) {
            return this;
        }

        public Builder setTransferMode(int mode) {
            return this;
        }

        public AudioTrack build() {
            throw new UnsupportedOperationException(
                    "the legacy screenshot harness has no audio device");
        }
    }
}
