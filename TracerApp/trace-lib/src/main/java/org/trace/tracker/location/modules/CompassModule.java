package org.trace.tracker.location.modules;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.util.Log;

/**
 * Created by rodri_000 on 01/02/2016.
 */
public class CompassModule implements SensorEventListener {

    SensorManager sensorManager;
    private Sensor sensorAccelerometer;
    private Sensor sensorMagneticField;
    private Sensor sensorRotationVector;
//    private Sensor sensorLinearAccel;
//    private Sensor sensorGravity;

    private float[] valuesAccelerometer;
    private float[] valuesMagneticField;
    private float[] valuesRotationVector;
//    private float[] valuesLinearAccel;
//    private float[] valuesGravity;

    private float[] matrixR;
    private float[] matrixI;
    private float[] matrixValues;

    //    private float MAX = 3; //Experimental value for the fwd/bckwd axis, force exerted when walking at a slow pace
//    private double azimuth;
    private double[] azimuths;
    private int counter;
    private int NUM_SAMPLES = 30; //per update, which in the Nexus 5 translates to around once per sec updates
    private int NUM_SAMPLES_ROTATION = 5;

    private float lastAzimuth;

    private Context context;
    private static boolean fromCompassActivity;
    private final boolean rotationVector = true; //rotation vector with phone upstright = best results

    private static float magneticDeclination = 0;

    private final double TWENTY_FIVE_DEGREE_IN_RADIAN = Math.toRadians(25);
    private final double ONE_FIFTY_FIVE_DEGREE_IN_RADIAN = Math.toRadians(155);

    public CompassModule(Context context){

        this.context = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if(rotationVector)
            sensorRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        else{
            sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorMagneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
//        sensorLinearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
//        sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        valuesAccelerometer = new float[3];
        valuesMagneticField = new float[3];
        valuesRotationVector = new float[3];
//        valuesLinearAccel = new float[3];
//        valuesGravity = new float[3];

        matrixR = new float[9];
        matrixI = new float[9];
        matrixValues = new float[3];

        azimuths = new double[NUM_SAMPLES];
        fromCompassActivity = false;
    }

    //Constructor called by the compass activity, so that this module also outputs the pitch and roll for debug
    //it doesn't matter what value is the boolean, just the fact that a constructor with a boolean was used.
    public CompassModule(Context context, boolean frCompassActivity){
        this(context);
        fromCompassActivity = true;
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {}

    @Override
    public void onSensorChanged(SensorEvent event) {

        switch(event.sensor.getType()){
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, valuesAccelerometer, 0, 3);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, valuesMagneticField, 0, 3);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                System.arraycopy(event.values, 0, valuesRotationVector, 0, 3);
                break;
//        case Sensor.TYPE_LINEAR_ACCELERATION:
//            if( event.values[2] > MAX ) { //forward/backward mvt axis
//                for (int i = 0; i < 3; i++) {
//                    valuesLinearAccel[i] = event.values[i];
//                }
//            }
//            break;
        }

        boolean success;
        if(rotationVector) {
            success = true;
            SensorManager.getRotationMatrixFromVector(matrixR, valuesRotationVector);
        }else{
            //Computes the inclination matrix I as well as the rotation matrix R from the new values
            success = SensorManager.getRotationMatrix(matrixR, matrixI, valuesAccelerometer,
                    valuesMagneticField);
        }

        if(success) {
            float inclination = (float) Math.acos(matrixR[8]);
            float[] newMatrixR = new float[9];

            double pitch = Math.toDegrees(matrixValues[1]);
            double roll = Math.toDegrees(matrixValues[2]);

            //Detect if the device is vertical
            if (!(inclination < TWENTY_FIVE_DEGREE_IN_RADIAN
                    || inclination > ONE_FIFTY_FIVE_DEGREE_IN_RADIAN))
            {
                // The azimuth returned by getOrientation is obtained by orthogonally projecting the
                // device's Y axis into the world East-North plane and then calculate the angle
                // between the resulting projection vector and the North axis. If device is vertical,
                // the device's Y axis has a very small component, maybe negative due to sensor's
                // noise. We need to remap the rotation matrix coordinate system
                // See: http://stackoverflow.com/questions/16317599/android-compass-that-can-compensate-for-tilt-and-pitch
                SensorManager.remapCoordinateSystem(matrixR, SensorManager.AXIS_X,SensorManager.AXIS_Z, newMatrixR);
                matrixR = newMatrixR;
            }

            SensorManager.getOrientation(matrixR, matrixValues); //fills in matrixValues

            azimuths[counter++] = Math.toDegrees(matrixValues[0]);

            int num_samples;
            if(rotationVector){ num_samples = NUM_SAMPLES_ROTATION; }
            else{ num_samples = NUM_SAMPLES; }

            if (counter == num_samples){
                double avg = 0;
                for (double value : azimuths)
                    avg += value;
                avg = avg / num_samples;

                lastAzimuth = (float)avg + magneticDeclination;
                //Debug activity compassActivity
                if(fromCompassActivity) {

                    /* TODO: make CompassActivity independent
                    ((CompassActivity) context).readingAzimuth.setText("Azimuth: " + lastAzimuth);
                    ((CompassActivity) context).readingPitch.setText("Pitch: " + String.valueOf(pitch));
                    ((CompassActivity) context).readingRoll.setText("Roll: " + String.valueOf(roll));

                    ((CompassActivity) context).myCompass.update((float)Math.toRadians(lastAzimuth));
                    */
                }

                counter = 0;
            }
        }
    }


    // Initializes the magnetic declination variable that's added to azimuth returned from the compass.
    // A magnetic compass, including the compasses on most smart phones, does not point to the North Pole (true north),
    // it points to the North Magnetic Pole. This angle known as the Magnetic Declination, varies considerably, depending
    // on where you are on the earths surface and it also varies slowly over time.
    // The GeomagneticField class doesn't interact with the sensors,
    // it provides data based on models of the shape of the magnetic field for a given lat,lon
    public float getMagneticDeclination(Location location) {
        GeomagneticField gf = new GeomagneticField((float)location.getLatitude(),
                (float)location.getLongitude(), (float)location.getAltitude(), System.currentTimeMillis());
        magneticDeclination = gf.getDeclination();
        Log.d("CompassModule", "Declination: " + magneticDeclination);
        return magneticDeclination;
    }

    public void setFromCompassActivity(boolean value){
        fromCompassActivity = value;
    }

    public void registerListeners(){
        if(rotationVector)
            sensorManager.registerListener(this, sensorRotationVector, SensorManager.SENSOR_DELAY_NORMAL);
        else {
            sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, sensorMagneticField, SensorManager.SENSOR_DELAY_NORMAL);// around 30ms in the Nexus 5 (not constant)
        }
//        sensorManager.registerListener(this, sensorLinearAccel, SensorManager.SENSOR_DELAY_UI); // around 20ms in the Nexus 5
//        sensorManager.registerListener(this, sensorGravity, SensorManager.SENSOR_DELAY_GAME); // around 8.5ms
    }

    public void unregisterListeners(){
        if(rotationVector)
            sensorManager.unregisterListener(this, sensorRotationVector);
        else {
            sensorManager.unregisterListener(this, sensorAccelerometer);
            sensorManager.unregisterListener(this, sensorMagneticField);
        }
//        sensorManager.unregisterListener(this, sensorLinearAccel);
//        sensorManager.unregisterListener(this, sensorGravity);
    }

    public float getLastAzimuth(){
        return lastAzimuth;
    }
}
