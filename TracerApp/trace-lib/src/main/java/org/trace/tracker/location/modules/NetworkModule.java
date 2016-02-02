package org.trace.tracker.location.modules;


//Created by Kurt on 13-02-2015.
//Location seems to work with around 30m accuracy in Taguspark library.
//It worked after I turned on the internet, but it wasn't returning a location before this.
//However, after turning the internet off again, I could obtain new location advertisements which
//leads me to believe some mapping was downloaded and locally cached.
//Definitely, some caching is being done, as when arriving at a new place (or the same place after
//some hours) there is no localization via network until there is an internet connection established.
//16 February: I found out that after a network request, the APs are scanned and are reported in
//a broadcast which is picked up by the registered receiver (see WifiModule).
//18 February: I can get the scanned AP list if there is no local cache (i.e. if there was no prior
//internet connection in a given spot). However the time it takes after the network request to receive
//these scans is not predictable. I tried requesting every second, but it doesn't seem to work that
//way. The times I got for the scans are: 2, 25, 66, 106, 141, 166, 207, 247, 287, 327, 350, 390,
//430, 470, 510. The results seem to indicate that it takes between 25 to 40 seconds for the next
//network scan update to be broadcast. We no longer trust in googles scheduler, we implemented our own.
//Duty cycle depends on energy consumption settings: HIGH 1 min, LOW 10 min
//9 March: Found out the minimum frequency of updates you can get with the Network provider is 20sec.

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.trace.tracker.ModuleInterface;
import org.trace.tracker.Profile;
import org.trace.tracker.location.DutyCycleInterface;
import org.trace.tracker.utils.MyUtil;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class NetworkModule extends Service implements LocationListener, DutyCycleInterface, ModuleInterface {

    private static Context context;
    private static Location lastLocation;

    protected static LocationManager locationManager;

    public static final String PROVIDER_NAME = "network";

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 0; // 0 meters
    // The minimum time between updates in milliseconds
    private static final int MIN_TIME_BW_UPDATES = 20000; // 20 seconds

    private static final int operationPeriod = MIN_TIME_BW_UPDATES; //in secs
    private static double dutyCycle = 0;
    private static boolean toggled = false; //false = off
    private static boolean instantRequest = false;
    private static int timeout = 10000; //10 secs

    private static HashMap<String, Profile.SecurityLevel> registeredApps;
    private static HashMap<String, Profile.EnergyConsumption> energyAppsMap;

    private static WifiReceiver wifiReceiver = null;
    private static WifiManager wifiManager;
    private static HashMap<String, Profile.Synchronism> registeredSyncs; //<cls, profile.sync>
    private static ArrayList<List<ScanResult>> storedScans; //List of jsons, where each is is an array of scanresults
    private static List<ScanResult> currentScan;
    private static long timeDifference;

    public NetworkModule(Context ctx) {
        context = ctx;
        locationManager = (LocationManager) context
                .getSystemService(LOCATION_SERVICE);
        registeredApps = new HashMap<String, Profile.SecurityLevel>();

        NetworkDeferIntentService.initializeWithNetworkModule(this); //give the intentservice a reference to this module
        NetworkTimeoutIntentService.initializeWithNetworkModule(this);
        lastLocation = new Location("starterNetwork"); //dummy first location for the timeout thread checks
        energyAppsMap = new HashMap<String, Profile.EnergyConsumption>();

        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();
        registeredSyncs = new HashMap<String, Profile.Synchronism>();
        if (storedScans == null)
            storedScans = new ArrayList<List<ScanResult>>();


        //WebServiceTask.setNetworkModule(this); TODO: revloc specific
        setTimeDifference();
    }

    //Will measure the current difference in UTC time and ellapsed time since boot.
    //Later this is used to correctly set the UTC time for any stored locations (based on their ellapsed time)
    private void setTimeDifference() {
        timeDifference = System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    private Profile.EnergyConsumption checkMaxEnergyStats() {
        Profile.EnergyConsumption max = Profile.EnergyConsumption.LOW;
        for (Profile.EnergyConsumption energy : energyAppsMap.values()) {
            //showToast(energy.toString());
            if ((energy == Profile.EnergyConsumption.MODERATE && max == Profile.EnergyConsumption.LOW)
                    || (energy == Profile.EnergyConsumption.HIGH
                    && (max == Profile.EnergyConsumption.MODERATE)
                    || max == Profile.EnergyConsumption.LOW))
                max = energy;
        }
        //showToast("Max: " + max.toString());
        return max;
    }

    private void updateEnergySettings() {
        switch (checkMaxEnergyStats()) {
            case LOW:
                //Update speed of 10 minutes
                dutyCycle = 0.03333;
                //showToast("Yes contact");
                break;
            case MODERATE:
                //Update speed of 5 minutes
                dutyCycle = 0.06666;
                //showToast("No contact");
                break;
            case HIGH:
                //Max update speed: 1 minute by default
                dutyCycle = 0.3333;
                //showToast("No contact");
                break;
        }
    }

    public void setNewContext(Context ctx) {
        context = ctx;
    }

    private void showToast(final String message) {
        /* TODO: make MainActivity independent
        ((MainActivity)context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
        */
    }

    //For timeout
    public static Location getLastLocation() {
        return lastLocation;
    }

    //Used for defers when the timeout expires, to use the duty cycle already input
    public void requestNetworkUpdates() {
        requestNetworkUpdates(Math.round(operationPeriod / dutyCycle));
    }

    public void requestNetworkUpdates(long cycle) { //periodOperation/dutyCycle = whole cycle duration

        //TODO: make MainActivity independent
        /*if(!((MainActivity) context).isStill()) {
            try {
                // if Network Enabled request location
                if (isNetworkEnabled()) {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            cycle,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    Log.d("requestNetworkUpdates", "Network Enabled");
                    listenToWifiScan();
                }
                //Launch an alarm with a timeout to make sure the gps will not stay on without providing a location
                scheduleTimeoutCheck();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            deferNextUpdate();
            Log.d("requestNetworkUpdates", "DEVICE IS STILL!");
        }*/
    }

    /* 	Remove all location updates for this LocationListener (NetworkModule) */
    public void removeNetworkUpdates() {
        if (locationManager != null) {

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.

                return;
            }
            locationManager.removeUpdates(NetworkModule.this);
            Log.d("removeNetworkUpdates", "Network Disabled");
        }
    }

    // To prevent different alarms to cancel each other out, we need to give an id to the pending intent
    // which identifies the sender of the intent. Only alarms set with the same id can automatically cancel
    // each other.
    // See: http://stackoverflow.com/questions/8469705/how-to-set-multiple-alarms-using-android-alarm-manager
    public static void scheduleTimeoutCheck(){
        NetworkTimeoutIntentService.currentLocation(lastLocation);
        AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NetworkTimeoutIntentService.class);
        intent.setAction("NetworkTimeout");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0); //could also change the first 0 as an id,1,2,3... instead of setting action
        mgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + timeout, pi); //waits 10 seconds for network
        Log.d("scheduleTimeoutCheck","Network "+ timeout);
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        if(instantRequest){
            Log.d("onLocationChanged", "New Network Instant Location");
            removeNetworkUpdates();
            instantRequest = false;
        }else {
            Log.d("onLocationChanged", "New Network Location");
            deferNextUpdate(); //To go back to before strict intervals, comment this line
        }

        //TODO: make MainActivity independent
        /*((MainActivity)context).sendNewLocation(lastLocation, new ArrayList<String>(registeredApps.keySet()));
        ((MainActivity) context).updateLocationTextView("Network update!", getNetworkInfo());*/
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public String getNetworkInfo(){
        double lattd;
        double longtd;
        String time;
        float accuracy;

        lattd = lastLocation.getLatitude();
        longtd = lastLocation.getLongitude();
        time = MyUtil.getTimeSince(lastLocation.getElapsedRealtimeNanos());
        accuracy = lastLocation.getAccuracy();
        return lattd + " " + longtd + " " + time + " " + accuracy + "m";
    }


    public static boolean isNetworkEnabled() {
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    /* Function to show settings alert dialog */
    public void showSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);

        // Setting Dialog Title
        alertDialog.setTitle("Network settings");

        // Setting Dialog Message
        alertDialog.setMessage("Network is not enabled. Do you want to go to settings menu?");

        // Setting Icon to Dialog
        //alertDialog.setIcon(R.drawable.delete);

        // On pressing Settings button
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                context.startActivity(intent);
            }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which){
                dialog.cancel();
            }
        });

        // Showing Alert Message
        alertDialog.show();
    }

    //Instant locations do not check if the device is still, as they work as a trigger
    public void requestInstantLocation(){
        instantRequest = true;
        requestNetworkUpdates(operationPeriod);
    }

    //DutyCycle interface
    @Override
    public void requestLocationUpdates(double dtCycle) {
        if (dutyCycle == 0)//If not initialized by any other way (namely through the profile, it will accept the suggestion)
            dutyCycle = dtCycle;
        toggled = true;
        //No need for a timer thread here... the android API already provides timed requests to
        //GPS and Network providers
        requestNetworkUpdates(Math.round(operationPeriod / dutyCycle)); //size of cycle in ms
    }


    @Override
    public void removeLocationUpdates() {
        toggled = false;
        removeNetworkUpdates();
        // When the timeout is triggered it will run deferNextUpdate, which will only reschedule if the localization
        // method is ON. So if we stop the localization before the timeout is triggered no rescheduling is done.
        // However, if this call occurs after rescheduling is done we need to cancel the defer alarm
        // It's safe to cancel a pending intent that does not exist.
        AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NetworkDeferIntentService.class);
        intent.setAction("NetworkDefer");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0);
        mgr.cancel(pi);
    }

    //Deferring once, and deferring while the first one was not activated work!!
    //For an instant request simply 'unregisters', for regular it 'unregisters' and schedules the next update
    @Override
    public void deferNextUpdate() {
        if(instantRequest){ //if it got here it means it timed out
            Log.d("deferNextUpdate", "Instant update timeout!");
            removeNetworkUpdates();
            instantRequest = false;
        }else if(toggled) {//failsafe against calling defer before requesting any updates...
            Log.d("deferNextUpdate", "Next update scheduled!");
            removeNetworkUpdates(); //stays toggled = true, so that doing a defer of a defer will work
            scheduleNextUpdate();
//            ((MainActivity) context).updateLocationTextView("Network", "--defering-- "
//                    + Math.round((operationPeriod/dutyCycle) / 1000) + "seconds");
        }
    }

    public static void scheduleNextUpdate(){
        AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NetworkDeferIntentService.class);
        intent.setAction("NetworkDefer");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0);
        mgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime()
                + Math.round(operationPeriod/dutyCycle), pi); //set is supposedly more precise than setRepeated
        Log.d("scheduleNextUpdate","Network " + operationPeriod + " " + dutyCycle + " " + Math.round(operationPeriod/dutyCycle));
    }

    /**
     *
     */
    public static class NetworkDeferIntentService extends IntentService {
        private static NetworkModule networkModule;

        public NetworkDeferIntentService() {
            super("NetworkDeferIntentService");
        }
        public static void initializeWithNetworkModule(NetworkModule nM){ networkModule = nM; }

        @Override
        protected void onHandleIntent(Intent intent) {
            Log.d(getClass().getSimpleName(), "NetworkDeferIntentService was called!");

            /* TODO: make MainActivity independent
            ((MainActivity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d("NetworkDeferIntentService", "Network Deferred");
                    networkModule.requestNetworkUpdates();
                }
            });
            */
        }
    }




    public boolean hasRealTimeApps(){
        for(Profile.Synchronism sync : registeredSyncs.values()){
            if(sync == Profile.Synchronism.REALTIME)
                return true;
        }
        return false;
    }

    public boolean hasNonRealTimeApps(){
        for(Profile.Synchronism sync : registeredSyncs.values()){
            if(sync == Profile.Synchronism.ASYNC)
                return true;
        }
        return false;
    }

    public List<String> getNonRealTimeApps(){
        List<String> result = new ArrayList<String>();
        for(Map.Entry<String, Profile.Synchronism> entry : registeredSyncs.entrySet()){
            Profile.Synchronism sync = entry.getValue();
            if(sync == Profile.Synchronism.ASYNC)
                result.add(entry.getKey());
        }
        return result;
    }

    public boolean hasPendingLocations(){
        return !storedScans.isEmpty();
    }

    //Removes a scan from storedScans and returns it. If it cannot complete the transfer this time,
    //the url will end up back in the list due to retries.
    public List<ScanResult> getNextPendingLocation(){
        List<ScanResult> storedScan = storedScans.get(0);
        storedScans.remove(0);
        return storedScan;
    }

    public static void listenToWifiScan() {
        registerWifiReceiver();
    }

    public String getScanResults() {
        //Stop after receiving first update
        unregisterWifiReceiver();

        List<ScanResult> results = wifiManager.getScanResults();
        Collections.sort(results, new Comparator<ScanResult>() {
            public int compare(ScanResult a, ScanResult b) {
                return a.level > b.level ? -1 : 1;
            }
        });

//        List<List<ScanResult>> storedScns = new ArrayList<List<ScanResult>>();
//        storedScns.add(results); //Only one scan stored
//        Gson gson = new Gson();
//        String json = gson.toJson(storedScans);
//        String URL = HTTP + SERVER_IP + CONTEXT_ROOT + "/Translation";
//        WebServiceTask wstPost = new WebServiceTask(WebServiceTask.POST_TASK, context, "Posting data...", this);
//        wstPost.setBody(json);
//        wstPost.execute(new String[]{URL});
        currentScan = results;

        return "Got scan results on Network provider!";
    }

    //Handles response from the Translation service
    public void handleResponse(String response) {
        try {
            if (response.length() > 0) {
                //POST RESPONSE ===============
                Log.d("REVLoc server response", response);
                if (response.startsWith("Post")) { //Response to the POST with the stored Network locations
                    String[] parts = response.split("«");
                    response = parts[1];
                    Gson gson = new Gson();
                    Type datasetListType = new TypeToken<Collection<Location>>() {
                    }.getType();
                    List<Location> locations = gson.fromJson(response, datasetListType);
                    //((MainActivity) context).updateLocationTextView("Post Response!", "Size:" + locations.size() + " " + response); TODO: make MainActivity independent
                    //Notify all non realtime apps registered
                    if(hasNonRealTimeApps()){
                        for(Location loc : locations) {
                            Log.d("LocationTime","####"+(loc.getElapsedRealtimeNanos()/1000000) + " " + SystemClock.elapsedRealtime());
                            loc.setTime(timeDifference + loc.getElapsedRealtimeNanos()/1000000); //SETTING TIME that is consumed by the applications
                            notifyNonRealTimeApps(loc);
                        }
                    }
                    //Needs validation? Network does not validate...
                }
            }
        } catch (Exception e) {
            Log.e("WifiModule handleResponse", e.getLocalizedMessage(), e);
        }
    }

    public void notifyNonRealTimeApps(Location location){
        location.setProvider(LocationManager.NETWORK_PROVIDER);
        List<String> nonRealTimeApps = new ArrayList<String>();
        for (Map.Entry<String, Profile.Synchronism> entry : registeredSyncs.entrySet()) {
            if(entry.getValue() == Profile.Synchronism.ASYNC)
                nonRealTimeApps.add(entry.getKey());
        }

        /*
        if(!nonRealTimeApps.isEmpty())
            //((MainActivity) context).sendNewLocation(location, nonRealTimeApps); TODO: make MainActivity independent
        */
    }

    public static void storeCurrentScan(){
        storedScans.add(currentScan);
    }


    /*
    public static void checkConnectionAndSendStoredScans(){
        storedScans.removeAll(Collections.singleton(null)); //removes null elements
        if(!storedScans.isEmpty()){
            Log.d("checkConnectionAndSendStoredScans", storedScans.size() + " scans stored");
            if((new WebServiceTask(WebServiceTask.GET_TASK, context, "","")).isConnected()){ //check connectivity
                Log.d("checkConnectionAndSendStoredScans", "Sending scans to server...");
                Gson gson = new Gson();
                String json = gson.toJson(storedScans);
                String URL = HTTP + SERVER_IP + CONTEXT_ROOT + "/Translation";
                WebServiceTask wstPost = new WebServiceTask(WebServiceTask.POST_TASK, context, "Posting data...", "network");
                wstPost.setBody(json);
                wstPost.execute(new String[]{URL});
                storedScans.clear(); //CLEAR THE STORED SCANS
            }
        }
    }
    */

    //Don't forget to add in the manifest... idiot..
    //If this is triggered, the timeout expired, the network module does not have the model downloaded
    public static class NetworkTimeoutIntentService extends IntentService {
        private static NetworkModule networkModule;
        private static Location lastLoc;

        public NetworkTimeoutIntentService() {
            super("NetworkTimeoutIntentService");
        }
        public static void initializeWithNetworkModule(NetworkModule nM){ networkModule = nM; }
        public static void currentLocation(Location location){ lastLoc = location; }

        @Override
        protected void onHandleIntent(Intent intent) {
            Log.d(getClass().getSimpleName(), "NetworkTimeoutIntentService was called!");

            /* TODO: make MainActivity independent
            ((MainActivity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d("NetworkTimeoutIntentService", "Inside NetworkTimeoutIntentService");
                    //if same location, defer next update
                    if(NetworkModule.getLastLocation().getTime() == lastLoc.getTime()) {
                        networkModule.deferNextUpdate();
                        ((MainActivity) context).updateLocationTextView("Network", "timed out");
                        Log.d("NetworkTimeoutIntentService", "Timed out!");
                        //Store the scan
                        storeCurrentScan();
                    }
                    ////quero enviar, senao conseguir vai fazer store
                    ////postScan();
                    //storeCurrentScan(); //DEBUG
                    //Verify if there are stored scans to be translated
                    checkConnectionAndSendStoredScans();
                }
            });
            */
        }
    }

    private static void registerWifiReceiver(){
        context.registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    public void unregisterWifiReceiver(){
        context.unregisterReceiver(wifiReceiver);
    }

    //Prints results as soon as the scan terminates -> results available are broadcast
    public class WifiReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            String scanResults = getScanResults();

            /* TODO: make MainActivity independent
            if (scanResults != null) {
                ((MainActivity) context).updateLocationTextView("Network Scan results!", scanResults);
            }
            */
        }
    }

    //If the module should behave differently depending on the security level required by an application
    public boolean isSecuritySensitive(){
        return false;
    }

    public void registerApp(Profile profile){
        String cls = profile.getCls();
        Profile.SecurityLevel level = profile.getSecurityLevel();
        Profile.Synchronism sync = profile.getSynchronism();
        Profile.EnergyConsumption energy = profile.getEnergy();

        //If already exists, it may be a security level update, anyway its always a put
        registeredApps.put(cls, level);
        //if not running start... no this decision belongs to the collector
        //Quando se regista trata de actualizar as configuracoes de energia
        energyAppsMap.put(cls, energy);
        registeredSyncs.put(cls, sync);
        //Check max energy stats and update frequency and contactServer
        updateEnergySettings();
    }

    public void unregisterApp(String cls){
        registeredApps.remove(cls);
        energyAppsMap.remove(cls);
        registeredSyncs.remove(cls);
        //if nothing left stop... no this decision belongs to the collector
    }

    @Override
    public boolean noAppsRegistered(){
        return registeredApps.isEmpty();
    }
}
