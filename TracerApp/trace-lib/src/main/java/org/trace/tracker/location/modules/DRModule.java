package org.trace.tracker.location.modules;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

import org.trace.tracker.ModuleInterface;
import org.trace.tracker.Profile;
import org.trace.tracker.utils.GeodesicCalculator;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Kurt
 *
 * When PedometerModule step counter sensor changes it fetches the azimuth from the CompassModule and updates
 * the this class with a new segment and the main activity
 */
public class DRModule implements ModuleInterface {

    private Context context;
    private SensorManager sensorManager;

    private static PedometerModule pedometerModule;
    private static CompassModule compassModule;

    private static List<Segment> course;

    private static Location lastLocation;
    private static HashMap<String, Profile.SecurityLevel> registeredApps;

    public static final String PROVIDER_NAME = "deadreckoning";
    private boolean showAzimuthAndSteps = false;

    public DRModule(Context context){
        this.context = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        pedometerModule = new PedometerModule(context, this);
        compassModule = new CompassModule(context);
        registeredApps = new HashMap<String, Profile.SecurityLevel>();
    }

    public DRModule(boolean dummy){
        //This constructor is called to instantiate dummy DRModules, whose only purpose is to allow
        //for the instantiation of the inner class Segment in a static context.
        //This is because, DeadReckoningIntentService has to be an inner class so I can access the segments
        //and the pedometerModule and the compassModule. And in order for an IntentService to be usable as
        //an inner class, it has to be static... hence the whole issue.
    }

    public static Location getLastLocation(){
        return lastLocation;
    }

    public void setNewContext(Context context) {
        this.context = context;
    }

    public String getListSensors(){
        List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

        StringBuilder sensorList = new StringBuilder();
        // Loop through the sensors
        for (Sensor sensor : deviceSensors) {
            // Add the name and address to an array adapter to show in a ListView
            sensorList.append(sensor.getName() + " : " + sensor.toString() +"\n");
        }
        return sensorList.toString();
    }


    // Start the compass activity
    public void launchCompassActivity() {
        /* TODO: make CompassActivity independent
        Intent intent = new Intent(context, CompassActivity.class);
        context.startActivity(intent);
        */
    }

    public void requestStepCounter() {
        pedometerModule.requestStepCounter();
    }

    public void removeStepCounter() {
        pedometerModule.removeStepCounter();
    }

    public void requestDirection(){ compassModule.registerListeners(); }

    public void removeDirection() { compassModule.unregisterListeners(); }

    //period in ms
    public void doDR() {
        //((MainActivity)context).notifyDRModule(); //requests to be notified of a new location TODO: make MainActivity independent
//        requestStepCounter();
//        requestDirection();

        course = new ArrayList<Segment>();
    }

    public void resetDR(Location location){
        lastLocation = location;
    }

    public void stopDR(){
        removeStepCounter();
        removeDirection();

        if(showAzimuthAndSteps) {
            String info = "";
            for (Segment s : course) {
                float azi = Float.valueOf(new DecimalFormat("#.##").format(
                        s.getAzimuth()));
                info += "timestamp " + s.getTimestamp() + " stp " + s.getNumOfSteps() + " azi " + azi + "\n";
            }

            //((MainActivity) context).updateLocationTextView("", info); TODO: make MainActivity independent
        }
    }

    //Called by the main activity when it received a new location after DR requested one
    public void startingPoint(Location location){
        Log.d("DRModule", "startingPoint " + location.getProvider());
        lastLocation = location;
        compassModule.getMagneticDeclination(location); //initialize magnetic declination for this region
        requestStepCounter();
        requestDirection();
    }

    //Called by the PedometerModule every 2 (or NUM_STEPS) steps
    public void newSegment(int numSteps){
        //Call the compassModule to obtain a bearing
        //store the bearing together with the num_steps in a list
        //every 1 min, this list is what...? TODO
        float azimuth = compassModule.getLastAzimuth();
        long timestamp = SystemClock.elapsedRealtime(); //clock since last boot
        Segment seg = new DRModule(false).new Segment(timestamp, numSteps, azimuth);
        course.add(seg);

        //Calculate and send new location
        lastLocation = GeodesicCalculator.getCoordsFromDistanceAndBearing(numSteps * pedometerModule.STEP_SIZE, azimuth,
                lastLocation.getLatitude(), lastLocation.getLongitude());
        lastLocation.setProvider(PROVIDER_NAME);
        //((MainActivity) context).sendNewLocation(lastLocation, new ArrayList<String>(registeredApps.keySet())); TODO: make MainActivity independet


    }

    //If the module should behave differently depending on the security level required by an application
    public boolean isSecuritySensitive(){
        return false;
    }


    public void registerApp(Profile profile){
        String cls = profile.getCls();
        Profile.SecurityLevel level = profile.getSecurityLevel();

        //If already exists, it may be a security level update, anyway its always a put
        registeredApps.put(cls, level);
    }

    public void unregisterApp(String cls){
        registeredApps.remove(cls);
    }

    public boolean noAppsRegistered(){
        return registeredApps.isEmpty();
    }

    public class Segment {
        private long timestamp;
        private int numOfSteps;
        private float azimuth;

        public Segment(long timestamp, int numOfSteps, float azimuth){
            this.timestamp = timestamp;
            this.numOfSteps = numOfSteps;
            this.azimuth = azimuth;
        }

        public long getTimestamp() {
            return timestamp;
        }
        public int getNumOfSteps() {
            return numOfSteps;
        }
        public double getAzimuth() {
            return azimuth;
        }
    }
}
