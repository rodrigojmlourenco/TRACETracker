package org.trace.tracker;


import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.location.Location;

import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.Log;

import org.trace.tracker.modules.activity.ActivityDetector;
import org.trace.tracker.modules.fraud.LocationValidator;
import org.trace.tracker.modules.location.BLEModule;
import org.trace.tracker.modules.location.DRModule;
import org.trace.tracker.modules.location.FusedLocationModule;
import org.trace.tracker.modules.location.GPSModule;
import org.trace.tracker.modules.location.NFCModule;
import org.trace.tracker.modules.location.NetworkModule;
import org.trace.tracker.modules.location.QRCodeModule;
import org.trace.tracker.modules.location.WifiModule;
import org.trace.tracker.modules.power.PowerGauge;
import org.trace.tracker.utils.FileWriter;
import org.trace.tracker.utils.GeodesicCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CollectorManager extends Service{

    public final static String LOG_TAG = "CollectorManager";

    private static HashMap<Module, Boolean> activeModules;

    //Location Modules
    private static GPSModule gpsModule                  = null;
    private static WifiModule wifiModule                = null;
    private static NetworkModule networkModule          = null;
    private static BLEModule bleModule                  = null;
    private static LocationValidator locationValidator  = null;
    private static DRModule drModule                    = null;
    private static NFCModule nfcModule                  = null;
    private static QRCodeModule qrModule                = null;
    private static FusedLocationModule fusedModule      = null;

    //Activity Recognition
    private static ActivityDetector activityDetector    = null;

    private static long lastUIUpdate    = 0;
    private static long requestTime     = 0; //When it receives the profile, or when the buttons were pressed

    //Is moving by default
    private static boolean still = false;

    private static boolean onlineLBSs = false;

    //static: when we rotate the phone the activity is destroyed and recreated
    private static String qrOutput;

    private static boolean notifyDR = false;

    private static HashMap<String, Profile> registeredApps; //<cls, profile>
    private static ProfileBroadcastReceiver profileReceiver;
    private static List<Profile.Module> runningModules;

    public CollectorManager(){
        //initialize resgisteredApps table, and the profile receiver
        if(activeModules == null)
            activeModules = new HashMap<>
                    ();

        if(registeredApps == null)
            registeredApps = new HashMap<String, Profile>();
        if(runningModules == null)
            runningModules = new ArrayList();
        profileReceiver = new ProfileBroadcastReceiver(this); //just a dummy, to give the class a context variable

        initTrackingModules();
    }

    private void initTrackingModules(){


        for(int i=0; i < Module.values().length; i ++){
            activeModules.put(Module.values()[i], false);
        }

        if(gpsModule == null)
            gpsModule = new GPSModule(this);
        else
            gpsModule.setNewContext(this);
        if(wifiModule == null)
            wifiModule = new WifiModule(this);
        else
            wifiModule.setNewContext(this);
        if(networkModule == null)
            networkModule = new NetworkModule(this);
        else
            networkModule.setNewContext(this);
        if(bleModule == null)
            bleModule = new BLEModule(this);
        else
            bleModule.setNewContext(this);
        if(activityDetector == null)
            activityDetector = new ActivityDetector(this);
        else
            activityDetector.setNewContext(this);
        //LocationValidator is a static object... accessed as if it were a lib
        //It needs to be isntantiated to create the treemap, but otherwise it is used as a static object
        if(locationValidator == null)
            locationValidator = new LocationValidator();

        if(drModule == null)
            drModule = new DRModule(this);
        else
            drModule.setNewContext(this);

        if(nfcModule == null)
            nfcModule = new NFCModule(this);
        else
            nfcModule.setContext(this);

        if(qrModule == null)
            qrModule = new QRCodeModule(this);
        else
            qrModule.setContext(this);

        if(fusedModule == null)
            fusedModule = new FusedLocationModule(this);
        else
            fusedModule.setNewContext(this);

        PowerGauge.setContext(this);
    }

    //Used by the AcitivityDectector to update the moving status
    public void setStillStatus(boolean still){
        synchronized(this){
            this.still = still;
        }
    }

    //Used by all duty cycled modules, before trying to obtain a location
    public boolean isStill(){
        if(isActive(Module.ActivityRecognition)) {
            boolean st;
            synchronized (this) {
                st = this.still;
            }
            Log.d("isStill", "" + st);
            return st;
        }
        else return false; //if AD is not on, assume always moving, so that locations are always reported
    }

    public DRModule getDR(){
        return drModule;
    }

    private void activateModule(Module module){
        switch (module){
            case GPS:
                if(gpsModule.isGPSEnabled()) // gps enabled
                    gpsModule.requestLocationUpdates(1); //dutyCycle = 100% = 120 secs
                else
                    gpsModule.showSettingsAlert();
                break;
            case Network:
                if(networkModule.isNetworkEnabled())
                    networkModule.requestLocationUpdates(1); //dutyCycle = 100% = 20 secs
                else
                    networkModule.showSettingsAlert();
                break;
            case WiFi:
                wifiModule.requestLocationUpdates(1);
                break;
            case ActivityRecognition:
                activityDetector.requestUpdates();
                break;
            case LocationValidator:
                //TODO: did not understand this one
                break;
            case Bluetooth:
                //TODO: did not understand this one, it starts a new activity
                break;
            case BLE:
                //TODO: did not understand this one, it starts a new activity
                break;
            case DR:
                drModule.doDR();
                break;
            case NFC:
                //TODO: did not understand this one, it starts a new activity
                break;
            case QR:
                //TODO: did not understand this one, it starts a new activity
                break;
            case FusedLocation:
                fusedModule.requestLocationUpdates();
                break;
            case Power:
                PowerGauge.measureAndScheduleNext();
                break;
            case Compass:
                //TODO: did not understand this one, it starts a new activity
                break;
            default:
                //TODO: throw new Exception




        }

        activeModules.remove(module);
        activeModules.put(module, false);
    }

    private void deactivateModule(Module module){
        switch (module){
            case ActivityRecognition:
                activityDetector.removeUpdates();
                break;
            case LocationValidator:
                //TODO: did not understand this one
                break;
            case Bluetooth:
                //TODO: did not understand this one, it starts a new activity
                break;
            case BLE:
                //TODO: did not understand this one, it starts a new activity
                break;
            case DR:
                drModule.stopDR();
                break;
            case NFC:
                //TODO: did not understand this one, it starts a new activity
                break;
            case QR:
                //TODO: did not understand this one, it starts a new activity
                break;
            case FusedLocation:
                fusedModule.removeLocationUpdates();
                break;
            case Power:
                PowerGauge.stopMeasurements();
                break;
            case Compass:
                //TODO: did not understand this one, it starts a new activity
                break;
            default:
                //TODO: throw new Exception
        }

        activeModules.remove(module);
        activeModules.put(module, false);
    }

    public void toggleModule(Module module){

        requestTime = SystemClock.elapsedRealtime();

        if(! activeModules.containsKey(module)) return; //TODO: throw new Exception;

        //The module is active -> Deactivate it
        if(activeModules.get(module))
            deactivateModule(module);
        else
            activateModule(module);
    }

    public void deferModuleNextUpdate(Module module){
        switch (module){
            case GPS:
                gpsModule.deferNextUpdate();
                break;
            case Network:
                networkModule.deferNextUpdate();
                break;
            case WiFi:
                networkModule.deferNextUpdate();
                break;
            default:
                //TODO: throw new Exception
        }
    }

    private boolean isActive(Module module){
        return activeModules.get(module);
    }

    //Helper method to create a new intent
    private Intent getLocationIntentForComponent(Location location, String pkg, String cls){
        Intent intent = new Intent();
        intent.setAction("pt.ulisboa.tecnico.locapp.NewLocation");
        intent.setComponent(new ComponentName(pkg, cls));
        intent.putExtra("location", location);
        intent.putExtra("sender", PendingIntent.getActivity(this, 0, intent, 0));
        return intent;
    }

    //Callback for all modules to send location information to all registered apps, regardless of what modules they required
    //DEPRECATED, all modules now send the list of apps (cls) that are supposed to receive it
    public void sendNewLocation(Location location){
        for(String app : registeredApps.keySet()) {
            Profile profile = registeredApps.get(app);
            Intent intent = getLocationIntentForComponent(location, profile.getPkg(), profile.getReceiver());
//        Intent intent = getLocationIntentForComponent(location, "pt.ulisboa.tecnico.sharemaps", "pt.ulisboa.tecnico.sharemaps.LocationReceiver");
            Log.d("Sending new location", location.getProvider() + " " + intent.toString() + " " + profile.getReceiver());
            sendBroadcast(intent);
        }
    }

    public void updateLocationTextView(String desc, String values) {

        long millisSinceBoot = SystemClock.elapsedRealtime(); //current time
        double interval = 0;
        if(lastUIUpdate == 0) {
            lastUIUpdate = millisSinceBoot;
            interval = (millisSinceBoot - requestTime)/1000; //if first time -> its the time since pressing button
        }else{
            interval = (millisSinceBoot - lastUIUpdate)/1000; //in secs
            lastUIUpdate = millisSinceBoot;
        }
        //userOutput.setText(userOutput.getText() + "\n" + desc + " " + values + " " + interval + "s"); TODO: specific to revloc
        //TODO toasts are off for now
//        if(!desc.isEmpty())
//            Toast.makeText(this, desc, Toast.LENGTH_SHORT).show();
        //LOGGING TESTS - GPS CDF
        //TODO Use this for time measurements (in milliseconds)
        //FileWriter.writePublicFileToExternalStorage(values + " " + (millisSinceBoot - requestTime) + "\n", "times_trace.txt");
        FileWriter.writePublicFileToExternalStorage(values + " " + (millisSinceBoot - requestTime) + "\n", "text_output.txt");
    }

    //Callback for all modules to send location information only to the registered apps that required this particular
    //type of location, e.g. Weak security Wifi geofi
    public void sendNewLocation(Location location, List<String> apps){
        //TESTES VALIDACAO DR: Se o DR estiver ligado, calcula a distancia entre a location actual e a ultima DR location
        //TODO imprimir o current time para ser mais facil interpretar..
        if(isActive(Module.DR) && !location.getProvider().equals(DRModule.PROVIDER_NAME) && DRModule.getLastLocation() != null){
            double distance = GeodesicCalculator.getDistance(location, DRModule.getLastLocation());
            updateLocationTextView("Distance DR", "Distance: " + distance);

            //If the location comes from a trusted source
            if(location.getProvider().equals(NetworkModule.PROVIDER_NAME) || location.getProvider().equals(GPSModule.PROVIDER_NAME)){
                drModule.resetDR(location);
            }
        }


        //VALIDATION
        if(!isActive(Module.LocationValidator) || (isActive(Module.LocationValidator) && LocationValidator.isValid(location))) {

            //Either send an intent to each registered app
            if(onlineLBSs)
                ;//sendLocationToServer(location); TODO: this is RevLoc specific
            else
            {
                for (String app : apps) {
                    Profile profile = registeredApps.get(app);
                    Intent intent = getLocationIntentForComponent(location, profile.getPkg(), profile.getReceiver());
                    Log.d(LOG_TAG, location.getProvider() + " " + intent.toString() + " " + profile.getReceiver());
                    sendBroadcast(intent);
                }
            }
            if (notifyDR) {
                drModule.startingPoint(location);
                notifyDR = false;
            }
            //If its a GPS location and there are apps registered for BLE, we send a BLE advertisement
            if(location.getProvider().equals("gps") && !bleModule.noAppsRegistered()){
                bleModule.advertiseBLE(location.getLatitude(), location.getLongitude(), location.getAccuracy());
            }
            //If its a BLE location and there are apps registered for GPS, we delay the next GPS activation
            if(location.getProvider().equals("ble") && !gpsModule.noAppsRegistered()){
                gpsModule.deferNextUpdate();
            }
        }

        //For debug
        if(activeModules.get(Module.LocationValidator)){
            double boundary = LocationValidator.getBoundary();
            double actualDistance = LocationValidator.getActualDistance();
            updateLocationTextView("Location Validator", "Boundary:" + boundary + " Dist:" + actualDistance);
        }
    }

    //Callback used by the ProfileReceiver
    //Works as a dif, if an app sends gps+network, and then the same app sends just network,
    //the gps module will be notified to unregister this app
    public void updateRegisteredApps(Profile profile){
        //Time TESTS
        requestTime = SystemClock.elapsedRealtime();

        //Insta requests are treated on their own
        if(profile.isInstantLocation()){
            //If this app isnt registered yet, it will be registered with an empty modules list
//            profile.clearModules();
            if (!registeredApps.keySet().contains(profile.getCls()))
                registeredApps.put(profile.getCls(), profile);
            requestInstantLocation(profile);
        }
        else {
            if (registeredApps.keySet().contains(profile.getCls())) {
                Profile oldProfile = registeredApps.get(profile.getCls());
                //Check if any module needs to retire operation (if this was the last app requesting it)
                List<Profile.Module> toBeRemoved;
                if ((toBeRemoved = profile.containsAllModules(oldProfile)).size() != 0) { //if we need to remove modules
                    oldProfile.setModules(toBeRemoved);
                    removeFromModules(oldProfile);
                    String result = "";
                    for (Profile.Module mod : toBeRemoved)
                        result += mod.toString() + " ";
                    Log.d("toBeRemoved", result);
                }

                List<Profile.Module> toBeAdded;
                if ((toBeAdded = oldProfile.containsAllModules(profile)).size() != 0) { //if we need to start modules
                    oldProfile.setModules(toBeAdded);
                    addToModules(oldProfile);
                    String result = "";
                    for (Profile.Module mod : toBeAdded)
                        result += mod.toString() + " ";
                    Log.d("toBeAdded", result);
                }
            } else {
                String result = "";
                for (Profile.Module mod : profile.getModules())
                    result += mod.toString() + " ";
                Log.d("2BeAddedForTheFirstTime", result);
                addToModules(profile); //updates the modules with the pair (cls, security) and starts if necessary
                //1st time could be an insta module that gets removed automatically
            }
            //check if new profile is empty, unregister the app
            if(profile.getModules().isEmpty()) {
                registeredApps.remove(profile.getCls()); //its safe to remove non existing members (insta modules are erased right after call)
                Log.d("unregisteringApp", profile.getCls());
            }else //update the modules wanted
                registeredApps.put(profile.getCls(), profile);
        }


    }

    public void requestInstantLocation(Profile profile){
        switch (profile.getInstantModule()) {
            case GPS:
                gpsModule.registerApp(profile);
                gpsModule.requestInstantLocation();
                break;
            case NETWORK:
                networkModule.registerApp(profile);
                networkModule.requestInstantLocation();
                break;
            case WIFIGEOFI:
                wifiModule.registerApp(profile);
                wifiModule.requestInstantLocation();
                break;
        }
        profile.clearModules();
    }

    //Updates the modules specified in the profile with the app that registered and the security level
    //May start modules if not running already
    public void addToModules(Profile profile){
        List<Profile.Module> modules = profile.getModules();
        for(Profile.Module module : modules){
            switch(module) {
                case GPS:
                    gpsModule.registerApp(profile);
                    if (!runningModules.contains(module)) {
                        runningModules.add(Profile.Module.GPS);
                    }
                    break;
                case NETWORK:
                    networkModule.registerApp(profile);
                    if (!runningModules.contains(module)){
                        runningModules.add(Profile.Module.NETWORK);
                    }
                    break;
                case WIFIGEOFI: //energy translates to frequency of updates and to location of computations
                    wifiModule.registerApp(profile);
                    if (!runningModules.contains(module)) {
                        runningModules.add(Profile.Module.WIFIGEOFI);
                    }
                    break;
                case DEAD_RECKONING:
                    drModule.registerApp(profile);
                    if (!runningModules.contains(module)) {
                        runningModules.add(Profile.Module.DEAD_RECKONING);
                    }
                    break;
                case BLE:
                    bleModule.registerApp(profile);
                    if (!runningModules.contains(module)) {
                        bleModule.requestLocationUpdates(1);
                        runningModules.add(Profile.Module.BLE);
                    }
                    break;
                case NFC:
                    nfcModule.registerApp(profile);
                    if (!runningModules.contains(module)) {
                        runningModules.add(Profile.Module.NFC);
                    }
                    break;
                case QR:
                    qrModule.registerApp(profile);
                    if (!runningModules.contains(module)) {
                        runningModules.add(Profile.Module.QR);
                    }
                    break;
                case FUSED:
                    fusedModule.registerApp(profile);
                    if (!runningModules.contains(module)) {
                        fusedModule.requestLocationUpdates();
                        runningModules.add(Profile.Module.FUSED);
                    }
                    break;
            }
        }
    }

    //Updates the modules specified in the profile with the app that registered and the security level
    //May remove modules if no more apps registered
    public void removeFromModules(Profile profile){
        List<Profile.Module> modules = profile.getModules();
        for(Profile.Module module : modules){
            switch(module) {
                case GPS:
                    gpsModule.unregisterApp(profile.getCls());
                    if (gpsModule.noAppsRegistered()) {
                        runningModules.remove(Profile.Module.GPS);
                    }
                    break;
                case NETWORK:
                    networkModule.unregisterApp(profile.getCls());
                    if (networkModule.noAppsRegistered()) {
                        runningModules.remove(Profile.Module.NETWORK);
                    }
                    break;
                case WIFIGEOFI:
                    wifiModule.unregisterApp(profile.getCls());
                    if (wifiModule.noAppsRegistered()) {
                        runningModules.remove(Profile.Module.WIFIGEOFI);
                    }
                    break;
                case DEAD_RECKONING:
                    drModule.unregisterApp(profile.getCls());
                    if (drModule.noAppsRegistered()) {
                        runningModules.remove(Profile.Module.DEAD_RECKONING);
                    }
                    break;
                case BLE:
                    bleModule.unregisterApp(profile.getCls());
                    if (bleModule.noAppsRegistered()) {
                        runningModules.remove(Profile.Module.BLE);
                        bleModule.removeLocationUpdates();
                    }
                    break;
                case NFC:
                    nfcModule.unregisterApp(profile.getCls());
                    if (nfcModule.noAppsRegistered()) {
                        runningModules.remove(Profile.Module.NFC);
                    }
                    break;
                case QR:
                    qrModule.unregisterApp(profile.getCls());
                    if (qrModule.noAppsRegistered()) {
                        runningModules.remove(Profile.Module.QR);
                    }
                    break;
                case FUSED:
                    fusedModule.unregisterApp(profile.getCls());
                    if (fusedModule.noAppsRegistered()) {
                        runningModules.remove(Profile.Module.FUSED);
                        fusedModule.removeLocationUpdates();
                    }
                    break;
            }
        }
    }

    //Called by the DR module upon turning on, so that it is notified of the next location to start
    //performing DR
    public void notifyDRModule(){
        notifyDR = true;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
