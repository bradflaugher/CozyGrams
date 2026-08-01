package com.cozygrams.tv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

final class CozyMusic {
    private AudioTrack track; private Thread thread; private volatile boolean playing; private boolean enabled=true;
    void setEnabled(boolean value) { enabled=value; if(value)start();else stop(); }
    void start() {
        if(playing||!enabled)return; playing=true;
        int rate=22050, min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
        track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(Math.max(min,4096)).setTransferMode(AudioTrack.MODE_STREAM).build();
        track.play(); thread=new Thread(()->loop(rate),"cozy-music");thread.start();
    }
    private void loop(int rate) {
        // Original, gently changing C-major music synthesized at runtime; no bundled copyrighted audio.
        double[] notes={329.63,392,523.25,493.88,440,392,329.63,293.66,261.63,329.63,392,440,392,329.63,293.66,261.63};
        double[] bass={130.81,130.81,110,110,87.31,87.31,98,98}; int beat=0;
        short[] data=new short[rate/2];
        while(playing) {
            double f=notes[beat%notes.length],low=bass[(beat++/2)%bass.length];
            for(int i=0;i<data.length;i++) { double fade=Math.sin(Math.PI*i/data.length),t=(double)i/rate;double bell=Math.sin(2*Math.PI*f*t)+.22*Math.sin(4*Math.PI*f*t);double pad=Math.sin(2*Math.PI*low*t)+.35*Math.sin(3*Math.PI*low*t);data[i]=(short)(1000*fade*bell+600*pad); }
            if(track!=null)track.write(data,0,data.length);
        }
    }
    void stop() { playing=false; if(thread!=null)try{thread.join(250);}catch(InterruptedException ignored){Thread.currentThread().interrupt();} if(track!=null){track.pause();track.flush();track.release();track=null;} }
}
