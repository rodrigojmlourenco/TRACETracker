package org.trace.tracker.location.modules;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.Toast;

/**
 * Created by rodri_000 on 01/02/2016.
 */
public class PedometerModule implements SensorEventListener {

    private Context context;
    private SensorManager sensorManager;

    private boolean successfullyRequested;
    private float stepCount;
    private float stepOffset;
    private boolean firstValue;

    private DRModule drModule;

    private long lastTimestamp;
    public final double STEP_SIZE = 0.8; //meters
    private boolean showSteps = false;

    public PedometerModule(Context context, DRModule drModule){
        this.context = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.drModule = drModule;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final int NUM_STEPS = 2;
        if(firstValue) {
            stepOffset = event.values[0];
            firstValue = false;
//            stepCount = 0;
        }
        stepCount = event.values[0] - stepOffset;

        /* TODO: make MainActivity independent
        if(showSteps)
            ((MainActivity)context).updateLocationTextView("", "Number of steps so far: "+ stepCount);
        */
        if(stepCount%NUM_STEPS == 0)//every NUM_STEPS steps notify drModule which will obtain the bearing and store info
            drModule.newSegment(NUM_STEPS);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // TODO step counter stopping when on sleep
    // However, be aware that the sensor will deliver your app the batched events based on your
    // report latency only while the CPU is awake. Although a hardware sensor that supports batching
    // will continue to collect sensor events while the CPU is asleep, it will not wake the CPU to
    // deliver your app the batched events.
    // See: https://developer.android.com/about/versions/android-4.4.html

    public void requestStepCounter() {
        Sensor countSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (countSensor != null) {
            sensorManager.registerListener(this, countSensor, SensorManager.SENSOR_DELAY_UI);
            successfullyRequested = true;
            firstValue = true;
        } else {
            Toast.makeText(context, "Count sensor not available!", Toast.LENGTH_LONG).show();
        }
    }

    public void removeStepCounter() {
        if(successfullyRequested){
            sensorManager.unregisterListener(this);
            successfullyRequested = false;
        }
    }
}
