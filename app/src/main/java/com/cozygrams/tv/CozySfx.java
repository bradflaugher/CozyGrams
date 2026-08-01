package com.cozygrams.tv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class CozySfx {
    enum Sound { MOVE,FILL,CROSS,HINT,ERROR,WIN,SELECT }
    private volatile boolean enabled=true;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"cozy-sfx");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardOldestPolicy());
    void setEnabled(boolean value){enabled=value;}
    void play(Sound sound){if(enabled)worker.execute(()->synthesize(sound));}
    private void synthesize(Sound sound){int rate=22050,ms;double[] notes;switch(sound){case MOVE:ms=32;notes=new double[]{330};break;case FILL:ms=65;notes=new double[]{523,659};break;case CROSS:ms=55;notes=new double[]{294,247};break;case HINT:ms=180;notes=new double[]{523,659,784};break;case ERROR:ms=140;notes=new double[]{196,165};break;case WIN:ms=480;notes=new double[]{523,659,784,1047};break;default:ms=90;notes=new double[]{440,554};}
        int count=rate*ms/1000;short[] data=new short[count];for(int i=0;i<count;i++){double t=(double)i/rate,fade=Math.sin(Math.PI*i/count);int segment=Math.min(notes.length-1,i*notes.length/count);double f=notes[segment];data[i]=(short)(2600*fade*(Math.sin(2*Math.PI*f*t)+.18*Math.sin(4*Math.PI*f*t)));}
        AudioTrack track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(data.length*2).setTransferMode(AudioTrack.MODE_STATIC).build();track.write(data,0,data.length);track.play();try{Thread.sleep(ms+25L);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}track.release();}
    void release(){worker.shutdownNow();}
}
