package com.cozygrams.tv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

final class CozyMusic {
    private AudioTrack track; private Thread thread; private volatile boolean playing;
    void start() {
        if(playing)return; playing=true;
        int rate=22050, min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
        track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(Math.max(min,4096)).setTransferMode(AudioTrack.MODE_STREAM).build();
        track.play(); thread=new Thread(()->loop(rate),"cozy-music");thread.start();
    }
    private void loop(int rate) {
        // Original, gently changing C-major music synthesized at runtime; no bundled copyrighted audio.
        double[] notes={261.63,329.63,392.00,523.25,440.00,349.23,392.00,329.63}; int beat=0;
        short[] data=new short[rate/2];
        while(playing) {
            double f=notes[beat++%notes.length];
            for(int i=0;i<data.length;i++) { double fade=Math.sin(Math.PI*i/data.length); double t=(double)i/rate; data[i]=(short)(1500*fade*(Math.sin(2*Math.PI*f*t)+.3*Math.sin(2*Math.PI*f/2*t))); }
            if(track!=null)track.write(data,0,data.length);
        }
    }
    void stop() { playing=false; if(thread!=null)try{thread.join(250);}catch(InterruptedException ignored){Thread.currentThread().interrupt();} if(track!=null){track.pause();track.flush();track.release();track=null;} }
}
