package org.trace.tracker.utils;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Handler;


import org.trace.trace.R;

import java.io.IOException;

public class SoundPlayer {

    private static Context context;

    private static int duration = 1; // seconds
    private static int sampleRate = 8000;
    private static int numSamples = duration * sampleRate;
    private static double sample[] = new double[numSamples];
    private static double freqOfTone = 440; // hz

    private static byte generatedSnd[] = new byte[2 * numSamples];

    private static Handler handler = new Handler();


    public static void playTone() {
        // Use a new tread as this can take a while
        final Thread thread = new Thread(new Runnable() {
            public void run() {
                genTone();
                handler.post(new Runnable() {

                    public void run() {
                        playSound();
                    }
                });
            }
        });
    }

    private static void genTone(){
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
        }

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }

    private static void playSound(){
        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);
        audioTrack.write(generatedSnd, 0, generatedSnd.length);
        audioTrack.play();
    }

    public static void playR2d2Sound(Context ctx) {
        /* TODO: very cute but serves no purpose
        context = ctx;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                MediaPlayer mp = MediaPlayer.create(context, R.raw.r2d2);
                mp.start();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mp.stop();
                mp.release();
                mp = null;
            }
        });
        thread.start();
        */
    }
}
