package org.trace.tracker.location.modules;


import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import org.trace.tracker.ModuleInterface;
import org.trace.tracker.Profile;
import org.trace.tracker.location.DutyCycleInterface;
import org.trace.tracker.utils.MyUtil;

import java.security.Provider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.app.Service;

public class GPSModule extends Service implements LocationListener, DutyCycleInterface, ModuleInterface {


    private static Context context;
    private static Location lastLocation;

    protected static LocationManager locationManager;

    public static final String PROVIDER_NAME = "gps";

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 0; // 0 meters
    // The minimum time between updates in milliseconds
    private static final int MIN_TIME_BW_UPDATES = 60000; // 1 minute

    private static final int operationPeriod = MIN_TIME_BW_UPDATES; //in ms
    private static double dutyCycle = 0;
    private static boolean toggled = false; //false = off
    private static boolean instantRequest = false;
    private static int timeout = 10000; //10 secs

    private static HashMap<String, Profile.SecurityLevel> registeredApps;
    private static HashMap<String, Profile.EnergyConsumption> energyAppsMap;


    public GPSModule(Context ctx) {
        context = ctx;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        registeredApps = new HashMap<String, Profile.SecurityLevel>();

        GPSDeferIntentService.initializeWithGPSModule(this); //give the intentservice a reference to this module
        GPSTimeoutIntentService.initializeWithGPSModule(this);
        lastLocation = new Location("starterGPS"); //dummy first location for the timeout thread checks
        energyAppsMap = new HashMap<String, Profile.EnergyConsumption>();
    }



    private Profile.EnergyConsumption checkMaxEnergyStats() {
        Profile.EnergyConsumption max = Profile.EnergyConsumption.LOW;
        for (Profile.EnergyConsumption energy : energyAppsMap.values()) {
            //showToast(energy.toString());
            if ((energy == Profile.EnergyConsumption.MODERATE && max == Profile.EnergyConsumption.LOW)
                    || (energy == Profile.EnergyConsumption.HIGH && (max == Profile.EnergyConsumption.MODERATE) || max == Profile.EnergyConsumption.LOW))
                max = energy;
        }
        //showToast("Max: " + max.toString());
        return max;
    }

    private void updateEnergySettings() {
        switch (checkMaxEnergyStats()) {
            case LOW:
                //Update speed of 10 minutes
                dutyCycle = 0.1;
                break;
            case MODERATE:
                //Update speed of 5 minutes
                dutyCycle = 0.2;
                break;
            case HIGH:
                //Max update speed: 1 minute by default
                dutyCycle = 1;
                break;
        }
    }


    public void setNewContext(Context ctx) {
        context = ctx;
    }

    public static Location getLastLocation() {
        return lastLocation;
    }

    //TODO: look into GpsStatus.Listener and getTimeToFirstFix()
    //SUPER DEPRECATED! DO NOT USE
//    public Location requestGPSUpdates() {
//        try {
//            // if GPS Enabled request location
//            if (isGPSEnabled()){
//                locationManager.requestLocationUpdates(
//                        LocationManager.GPS_PROVIDER,
//                        MIN_TIME_BW_UPDATES,
//                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
//                Log.d("requestGPSUpdates", "GPS Enabled");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return lastLocation;
//    }

    //Used for defers when the timeout expires, to use the duty cycle already input
    public void requestGPSUpdates() {
        requestGPSUpdates(Math.round(operationPeriod / dutyCycle));
    }

    public void requestGPSUpdates(long cycle) { //periodOperation/dutyCycle = whole cycle duration in ms

        /* TODO: make MainActivity independent
        if(!((MainActivity) context).isStill()) {
            try {
                // if GPS Enabled request location
                if (isGPSEnabled()){
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            cycle,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    Log.d("requestGPSUpdates", "GPS Enabled");
                }
                //Launch an alarm with a timeout to make sure the gps will not stay on without providing a location
                scheduleTimeoutCheck();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            deferNextUpdate();
            Log.d("requestGPSUpdates", "DEVICE IS STILL!");
        }
        */
    }

    /* 	Remove all location updates for this LocationListener (GPSModule) */
    public void removeGPSUpdates() {

        if (locationManager != null) {

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.

                return;
            }

            locationManager.removeUpdates(GPSModule.this);
            Log.d("removeGPSUpdates", "GPS Disabled");
        }
    }

    // To prevent different alarms to cancel each other out, we need to give an id to the pending intent
    // which identifies the sender of the intent. Only alarms set with the same id can automatically cancel
    // each other.
    // See: http://stackoverflow.com/questions/8469705/how-to-set-multiple-alarms-using-android-alarm-manager
    public static void scheduleTimeoutCheck(){

        GPSTimeoutIntentService.currentLocation(lastLocation);
        AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, GPSTimeoutIntentService.class);
        intent.setAction("GPSTimeout");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0); //could also change the first 0 as an id,1,2,3... isntead of setting action
        mgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + timeout, pi); //waits 10 seconds for gps
        Log.d("scheduleTimeoutCheck","GPS "+ timeout);
    }

    @Override
    public void onLocationChanged(Location location) {
        if(isNotSpoofed(location)) {
            lastLocation = location;
            if (instantRequest) {
                Log.d("onLocationChanged", "New GPS Instant Location");
                removeGPSUpdates();
                instantRequest = false;
            } else {
                Log.d("onLocationChanged", "New GPS Location");
                deferNextUpdate(); //To go back to before strict intervals, comment this line
            }

            //Instantaneous location is sent to every registered app
            //((MainActivity) context).sendNewLocation(lastLocation, new ArrayList<String>(registeredApps.keySet())); TODO: make MainActivity independent
            //((MainActivity) context).updateLocationTextView("GPS update!", getGPSInfo()); TODO: make MainActivity independent
        }
    }

    //Some spoofing applications do not issue a bundle with the number os satellites that is normally issued
    private boolean isNotSpoofed(Location location){
        double latd;
        double longtd;
        long time;
        Bundle extras;
        int satellites;
        float accuracy;

        latd = location.getLatitude();
        if(latd > 90 || latd < -90) return false;

        longtd = location.getLongitude();
        if(longtd > 180 || longtd < -180) return false;

        time = location.getElapsedRealtimeNanos();
        if(time < 0) return false;

        extras = location.getExtras();
        if(extras == null) return false;

        satellites = extras.getInt("satellites");
        if(satellites < 0) return false;

        accuracy = location.getAccuracy();
        if(accuracy <= 0) return false;

        return true;
    }


    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    //Tested
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        int satellites = extras.getInt("satellites");
        String stat;
        switch(status){
            case LocationProvider.OUT_OF_SERVICE:
                stat = "OUT OF SERVICE";
                break;
            case LocationProvider.AVAILABLE:
                stat = "AVAILABLE";
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                stat = "TEMPORARILY UNAVAILABLE";
                break;
            default:
                stat = "NO STAT";
                break;
        }

        //((MainActivity) context).updateLocationTextView("GPS Status!", stat + " num sats: " + satellites); TODO: make MainActivity independent
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    //Some GPS spoofers do not add the sattellites in their bundle, we can check for those
    public String getGPSInfo(){
        double latd;
        double longtd;
        String time;
        int satellites;
        float accuracy;

        latd = lastLocation.getLatitude();
        longtd = lastLocation.getLongitude();
        time = MyUtil.getTimeSince(lastLocation.getElapsedRealtimeNanos());
        satellites = lastLocation.getExtras().getInt("satellites");
        accuracy = lastLocation.getAccuracy();
        return latd + " " + longtd + " " + time + " sat: " + satellites + " " + accuracy + "m";
    }


    public static boolean isGPSEnabled() {
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        //ATTENTION beware of multiple consecutive false reports may be due to locationManager == null
    }

    // Show settings alert dialog
    public void showSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);

        // Setting Dialog Title
        alertDialog.setTitle("GPS settings");

        // Setting Dialog Message
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");

        // Setting Icon to Dialog
        //alertDialog.setIcon(R.drawable.delete);

        // On pressing Settings button
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                context.startActivity(intent);    }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which){
                dialog.cancel();    }
        });

        // Showing Alert Message
        alertDialog.show();
    }

    public void requestInstantLocation(){
        instantRequest = true;
        requestGPSUpdates(operationPeriod);
    }

    //Elapsed time between location updates will never be less than minTime, but it can be
    //greater, because it is influenced by the implementation of each given Provider
    @Override
    public void requestLocationUpdates(double dtCycle) {
        if(dutyCycle == 0)//If not initialized by any other way (namely through the profile, it will accept the suggestion)
            dutyCycle = dtCycle;
        toggled = true;
        //No need for a timer thread here... the android API already provides timed requests to
        //GPS and Network providers
        requestGPSUpdates(Math.round(operationPeriod / dutyCycle)); //size of cycle in ms
    }

    @Override
    public void removeLocationUpdates() {
        toggled = false;
        removeGPSUpdates();
        // When the timeout is triggered it will run deferNextUpdate, which will only reschedule if the localization
        // method is ON. So if we stop the localization before the timeout is triggered no rescheduling is done.
        // However, if this call occurs after rescheduling is done we need to cancel the defer alarm
        // It's safe to cancel a pending intent that does not exist.
        AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, GPSDeferIntentService.class);
        intent.setAction("GPSDefer");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0);
        mgr.cancel(pi);
    }

    // Deferring once, and deferring while the first one was not activated work!!
    // Why? If there is already an alarm scheduled for the same IntentSender,
    // that previous alarm will first be canceled.
    @Override
    public void deferNextUpdate() {
        if(instantRequest){ //if it got here it means it timed out
            removeGPSUpdates();
            instantRequest = false;
        }else if(toggled) {//failsafe will only defer if module is turned on or if it was an instant request
            removeGPSUpdates(); //stays toggled = true, so that doing a defer of a defer will work
            scheduleNextUpdate();
//            ((MainActivity) context).updateLocationTextView("GPS", "--defering-- "
//                    + Math.round((operationPeriod/dutyCycle) / 1000) + "seconds");
        }
    }

    public static void scheduleNextUpdate(){
        AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, GPSDeferIntentService.class);
        intent.setAction("GPSDefer");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0);
        mgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime()
                + Math.round(operationPeriod/dutyCycle), pi); //set is supposedly more precise than setRepeated
        Log.d("scheduleNextUpdate","GPS " + Math.round(operationPeriod/dutyCycle));
    }

    /** IntentService will receive the Intents, launch a worker thread, and stop the service as appropriate.
     * The IntentService cannot run tasks in parallel. Hence all the consecutive intents will go into
     * the message queue for the worker thread and will execute sequentially.
     */
    public static class GPSDeferIntentService extends IntentService {
        private static GPSModule gpsModule;

        public GPSDeferIntentService() {
            super("GPSDeferIntentService");
        }
        public static void initializeWithGPSModule(GPSModule gM){ gpsModule = gM; }

        @Override
        protected void onHandleIntent(Intent intent) {
            Log.d(getClass().getSimpleName(), "GPSDeferIntentService was called!");

            /* TODO: make MainActivity idependent
            ((MainActivity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d("GPSDeferIntentService", "GPS Deferred");
                    gpsModule.requestGPSUpdates();
                }
            });
            */
        }
    }

    /**
     * Don't forget to add in the manifest... idiot..
     * IntentService will receive the Intents, launch a worker thread, and stop the service as appropriate.
     */
    public static class GPSTimeoutIntentService extends IntentService {
        private static GPSModule gpsModule;
        private static Location lastLoc;

        public GPSTimeoutIntentService() {
            super("GPSTimeoutIntentService");
        }
        public static void initializeWithGPSModule(GPSModule gM){ gpsModule = gM; }
        public static void currentLocation(Location location){ lastLoc = location; }

        @Override
        protected void onHandleIntent(Intent intent) {
            Log.d(getClass().getSimpleName(), "GPSTimeoutIntentService was called!");

            /* TODO make MainActivity independent
            ((MainActivity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d("GPSTimeoutIntentService", "Inside GPSTimeoutIntentService");
                    //if timeout == same location, defer next update
                    if(GPSModule.getLastLocation().getTime() == lastLoc.getTime()) {
                        gpsModule.deferNextUpdate();
                        ((MainActivity) context).updateLocationTextView("GPS", "timed out" );
                    }
                }
            });
             */
        }
    }

    //If the module should behave differently depending on the security level required by an application
    @Override
    public boolean isSecuritySensitive(){
        return false;
    }

    @Override
    public void registerApp(Profile profile){
        String cls = profile.getCls();
        Profile.EnergyConsumption energy = profile.getEnergy();
        Profile.SecurityLevel level = profile.getSecurityLevel();

        //If already exists, it may be a security level update, anyway its always a put
        registeredApps.put(cls, level);
        energyAppsMap.put(cls, energy);
        updateEnergySettings();
        //if not running start... no this decision belongs to the collector
    }

    @Override
    public void unregisterApp(String cls){
        registeredApps.remove(cls);
        energyAppsMap.remove(cls);
        //if nothing left stop... no this decision belongs to the collector
    }

    @Override
    public boolean noAppsRegistered(){
        return registeredApps.isEmpty();
    }
}
