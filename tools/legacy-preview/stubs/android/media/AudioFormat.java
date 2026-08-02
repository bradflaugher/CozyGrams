package android.media;

/** Desktop stand-in for {@code android.media.AudioFormat}: compile-time only. */
public class AudioFormat {

    public static final int ENCODING_PCM_16BIT = 2;
    public static final int CHANNEL_OUT_MONO = 0x4;

    private AudioFormat() {
    }

    public static class Builder {

        public Builder setSampleRate(int rate) {
            return this;
        }

        public Builder setEncoding(int encoding) {
            return this;
        }

        public Builder setChannelMask(int mask) {
            return this;
        }

        public AudioFormat build() {
            return new AudioFormat();
        }
    }
}
