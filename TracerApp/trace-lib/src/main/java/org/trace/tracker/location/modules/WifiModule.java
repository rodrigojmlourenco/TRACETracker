package org.trace.tracker.location.modules;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import org.trace.tracker.location.DutyCycleInterface;
import org.trace.tracker.ModuleInterface;
import org.trace.tracker.Profile;
import org.trace.tracker.utils.GeodesicCalculator;
import org.trace.tracker.utils.Geofi;
import org.trace.tracker.utils.LDPL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Kurt
 *
 * Issues dealt with: getScanResults never returns an empty list (except perhaps right after the app was
 * started). Either there are APs in range and the returned list is up-to-date, or there are no APs
 * in range and the list is out-of-date (and contains the last seen APs).
 * scan() asks for a scan. When the scan is completed, the wifiReceiver that was registered will be instantiated
 * and its code run. Its code includes getScanResults() where the code is processed to output a location.
 * If a valid location was found it is reported to the collector either via notifyWeakLocation() or via
 * contacting the server and then when receiving information from the server, handleResponse() is run.
 * Found out that if there is internet connectivity, wifi scans happen automatically about once per minute.
 * we need to detect connectivity and base the duty cycle on this information
 * Also take into account that if network provider was also requested, we can piggy back scan
 * information from the network forced scan.
 * A side-effect of this approach is that the app will wake the device each time any of this receiver
 * is triggered—potentially much more frequently than required. So in our implementation the wifireceiver
 * is turned on only when a scan is about to be made and turned off as soon as it ends.
 */
public class WifiModule implements ModuleInterface, DutyCycleInterface{

    private static Context context;
    private static Location lastLocation;
    private static Location lastWeakLocation;
    private static WifiManager wifiManager;
    private static WifiReceiver wifiReceiver = null;

    private final static int ALARM_MANAGER_SCAN_PERIOD = 20000; //20 secs

    private static int operationPeriod = ALARM_MANAGER_SCAN_PERIOD;
    private static double dutyCycle = 0;


    private static HashMap<String, Profile.SecurityLevel> registeredApps;
    private static HashMap<String, String> weakApps; //<cls, cls> just to make sure there are no duplicates
    private static HashMap<String, String> strongApps; //just to make sure there are no duplicates
    public static final String PROVIDER_NAME = "wifigeofi";
    //public static final String TRILATERATION_DEBUG_PROVIDER_NAME = "wifitrilatdebug"; <-- just use geofi provider name geofi.GEOFI returned by default

    private static final boolean showTrilaterationAPs = false; //Reports the AP locations in dark blue
    private static final boolean showScanResults = false; //sends to the collector the list of geofi APs and strongest AP
    private static final boolean showAllAPs = false;

    //weakApps and strongApps exist for speedy lookup, same as energyAppsMap
    private static ArrayList<Location> aps;
    private static HashMap<Location, String> signalStrengths;
    private static HashMap<String, Profile.EnergyConsumption> energyAppsMap;
    private static HashMap<String, Profile.Synchronism> registeredSyncs; //<cls, profile>
    private static boolean contactServer = false; //ALTEREI ISTO
    private static ArrayList<String> storedScans;

    //private static boolean ongoingRequests = false; //If non-instant location updates were requested
    //This allows registering on requestLocationUpdates

    //private static float connectivityFactor = 1; //1 if no connectivity, 0.333 if connectivity

    static float ss = -55f;

    public WifiModule(Context ctx){
        context = ctx;
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();
        registeredApps = new HashMap<String, Profile.SecurityLevel>();
        weakApps = new HashMap<String, String>();
        strongApps = new HashMap<String, String>();
        energyAppsMap = new HashMap<String, Profile.EnergyConsumption>();
        registeredSyncs = new HashMap<String, Profile.Synchronism>();

        if(storedScans == null)
            storedScans = new ArrayList<String>();
    }

    private Profile.EnergyConsumption checkMaxEnergyStats(){
        Profile.EnergyConsumption max = Profile.EnergyConsumption.LOW;
        for(Profile.EnergyConsumption energy : energyAppsMap.values()){
            //showToast(energy.toString());
            if((energy == Profile.EnergyConsumption.MODERATE && max == Profile.EnergyConsumption.LOW)
                    || (energy == Profile.EnergyConsumption.HIGH && (max == Profile.EnergyConsumption.MODERATE) || max == Profile.EnergyConsumption.LOW))
                max = energy;
        }
        //showToast("Max: " + max.toString());
        return max;
    }

    //TODO tabelas com valores para wifi activity para deduzir que intervalos de Wifi fazem sentido http://android.stackexchange.com/questions/79079/does-turning-wifi-on-and-off-use-more-power-than-it-being-constantly-on-for-a-se
    //TODO tambem queria mesmo guardar non-geofi scans e traduzi-los no servidor usando a google geolocation API:
    // https://developers.google.com/maps/documentation/business/geolocation/#requests (mas nao vai dar tempo...)
    private void updateEnergySettings(){
        switch(checkMaxEnergyStats()) {
            case LOW: //try to contact server, and if fail after 3 times, store it
                //Update speed of 10 minutes
                dutyCycle = 0.03333;
                //contactServer = true;
                //showToast("Yes contact");
                break;
            case MODERATE:
                //Update speed of 5 minutes
                dutyCycle = 0.06666;
                //contactServer = false;
                //showToast("No contact");
                break;
            case HIGH: //try to contact server and if fail after 3 times compute locally
                //Max update speed: 1 minute by default
                dutyCycle = 0.3333;
                //contactServer = false;
                //showToast("No contact");
                break;
        }
    }

    public void setNewContext(Context ctx) {
        context = ctx;
    }

    public static void scan() {
        registerWifiReceiver();
        wifiManager.startScan();
        /*if(!((Activity) context).isStill()) { TODO: do something else
            registerWifiReceiver();
            wifiManager.startScan();
            // Only check the scan result after the startScan emits an asynchronous event that marks its completion
            //http://www.androidsnippets.com/scan-for-wireless-networks
        }else{ //No defer here, as the next schedule is done before starting the scan
            Log.d("scan", "DEVICE IS STILL!");
        }*/
    }

    private void showToast(final String message) {
        ((Activity)context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    //After a scanning operation, it will check the 3 highest non-colocated aps and send their info to the server
    public String getScanResults(){
        //Stop after receiving first update
        unregisterWifiReceiver();

        List<ScanResult> results = wifiManager.getScanResults();
        //Its a hashmap to filter out multiple network interfaces within the same AP
        HashMap<String, ScanResult> geofiResults = new HashMap<String, ScanResult>();

        int highest = -200;
        ScanResult highestResult = null;
        String wifiList = "";
        String geofiCode;

        //FOR WIFI GEOFI MAPPING PURPOSES:
        //sort by distance (closest to furthest)
//        Collections.sort(results , new Comparator<ScanResult>() {
//            public int compare(ScanResult a, ScanResult b) {
//                double dist1 = calculateDistance(a.level);
//                double dist2 = calculateDistance(b.level);
//                return dist1 < dist2 ? -1 : 1;
//            }
//        });
//
//        //print the 7 first
//        wifiList += "\n";
//        int i = 0;
//        for(ScanResult res : results){
//            geofiCode = Geofi.getCodeFromBSSID(res.BSSID.substring(0, res.BSSID.length()-1));
//            if(geofiCode == null)
//                geofiCode = "No geofi";
//            String ssid = res.SSID;
//            if(ssid.length()>7)
//                ssid = ssid.substring(0,8);
//            wifiList += ssid + " <> " + res.level + "<>" + res.BSSID
//                    + "<>" + geofiCode + "\n";
//            if(i++ == 7)
//                break;
//        }

        // Filters the strongest geofi APs (network interfaces) from the rest, and pickup highest
        // level (closest AP), regardless of having geofi
        for (ScanResult result : results) {
            geofiCode = Geofi.getCodeFromBSSID(result.BSSID.substring(0, result.BSSID.length() - 1));

            //pickup highest level (closest AP), regardless of having geofi
            if(result.level > highest){
                highest = result.level;
                highestResult = result;
            }
            //compile only the best geofi APs
            if(geofiCode != null){
                if(!geofiResults.containsKey(geofiCode))
                    geofiResults.put(geofiCode, result);
                else{ //if already has the geofi code only goes in if its closer
                    ScanResult rep = geofiResults.get(geofiCode);
                    if(calculateDistance(result.level) < calculateDistance(rep.level))
                        geofiResults.put(geofiCode, result);
                }
            }// result.capabilities  yields encryption capabilities.. WPA2, EAP, etc..
        }
        results = new ArrayList<ScanResult>(geofiResults.values());
        //sort geofi results by distance (closest to furthest)
        Collections.sort(results, new Comparator<ScanResult>() {
            public int compare(ScanResult a, ScanResult b) {
                double dist1 = calculateDistance(a.level);
                double dist2 = calculateDistance(b.level);
                return dist1 < dist2 ? -1 : 1;
            }
        });

        // If new location is within 1 second of the last obtained location, it is invalid (check this class description)
        double timestampDifference;
        if(lastLocation != null && (timestampDifference = Math.abs(highestResult.timestamp - lastLocation.getTime())) < 1000 ){
            return "No APs visible " + timestampDifference + " " + highestResult.timestamp + " " + lastLocation.getTime();
        }

        // Print the highest, regardless if it has geofi code, for control
        geofiCode = Geofi.getCodeFromBSSID(highestResult.BSSID.substring(0, highestResult.BSSID.length() - 1));
        if (geofiCode == null)
            geofiCode = "No geofi";
        wifiList += "\n" + highestResult.SSID + "|" + highestResult.BSSID
                + "|" + highestResult.level + "|" + geofiCode + "|" + highestResult.timestamp + "\n";


        //Here we check the security level, if weak then just one AP is fine to obtain location and
        //do the maths locally (depends on the energy profile) or simply send the first 3 APs gathered and the security info
        //to the server, and the server decides what to do.
        //String url = RevlocRestInterface.SERVICE_URL + "/Trilateration";
        int iterator = 0;
        boolean enoughForTrilateration = false;
        Location strongestGeofiAP = new Location("Error: Check WifiModule");
        aps = new ArrayList<Location>();
        signalStrengths = new HashMap<Location, String>();

//        //This is to be part of the Network provider
//        //sending multiple scanresults is done in a post, but first we parse one at a time
//        //DONE depois olhar para a maneira como estamos a enviar e a receber os scans do wifi... ta num GET e eh 1 por 1.. ja esta corrigido para ser enviado por pacote
//        List<List<ScanResult>> storedScans = new ArrayList<List<ScanResult>>();
//        storedScans.add(results); //Only one scan stored
//        Gson gson = new Gson();
//        String json = gson.toJson(storedScans);
//        String URL = HTTP + SERVER_IP + CONTEXT_ROOT + "/Translation";
//        WebServiceTask wstPost = new WebServiceTask(WebServiceTask.POST_TASK, context, "Posting data...", this);
//        wstPost.setBody(json);
//        wstPost.execute(new String[]{URL});

        //Fill APs location list (with local time) and obtain the strongest AP
        for(ScanResult result: results){
            //PRINT GEOFI APS in the collector
            geofiCode = Geofi.getCodeFromBSSID(result.BSSID.substring(0, result.BSSID.length()-1));
            float distance = (float)calculateDistance(result.level);
            wifiList += result.BSSID + " <> " + result.level + "<>" + String.format("%.2f",distance)
                    + "<>" + geofiCode + "\n";

            Location loc = Geofi.decode(geofiCode);
            aps.add(loc);
            signalStrengths.put(loc, "" + result.level);
            loc.setTime(result.timestamp); //setting the local time
            loc.setAccuracy(distance);
            if(iterator == 0) { //obtain the strongest AP
                strongestGeofiAP = loc;
            }

            iterator++;
            if(iterator == 3) {
                enoughForTrilateration = true;
            }
        }

        // The calculations for the non collinearity and security checks, can be offloaded to the server as well

        //If found at least one geofi AP
        if(strongestGeofiAP.getProvider().equals(Geofi.GEOFI)) {
            boolean weakSec = !weakApps.isEmpty();
            boolean strongSec = !strongApps.isEmpty();

            //CONTACTING SERVER TODO: never contacting server
            /*
            if(contactServer) {
                url += "/list/";
                for (Location ap : aps)
                    url += ap.getLatitude() + ":" + ap.getLongitude() + ":" + signalStrengths.get(ap) + "«";
                if (url.endsWith("«"))
                    url = url.substring(0, url.length() - 1);
                url += "/timestamp/" + System.currentTimeMillis();

                if (strongSec) {
                    url += "/security/strong";
                    WebServiceTask wst = new WebServiceTask(WebServiceTask.GET_TASK, context, "GETting data...", this);
                    wst.execute(new String[]{url});
                    Log.d("strongSec: ", "Server contact " + url);
                } else { //Porque usar weak? Strong security pode ser excluir demsiadas localizaçoes para uma aplicaçao sem requisitos de segurança
                    url += "/security/weak";
                    WebServiceTask wst = new WebServiceTask(WebServiceTask.GET_TASK, context, "GETting data...", this);
                    wst.execute(new String[]{url});
                    Log.d("weakSec: ", "Server contact " + url);
                }
                //RUNNING LOCALLY
            }*/
            /*else{*/
                // aps is filled with APs by order of proximity, now we do non colinear check on the list,8
                // obtaining a list with only 3 non collinear elements
                ArrayList<Location> nonCollinearAPs = GeodesicCalculator.getNonColinear(aps);
                if (nonCollinearAPs != null) {
                    if (showTrilaterationAPs) {
                        for (Location ap : nonCollinearAPs) {
                            notifyAPLocation(ap);
                        }
                    }
                }
                if(strongSec) {
                    if (nonCollinearAPs != null) {
                        //Verify that the non collinear APs are within range and report the trilaterated position
                        if(GeodesicCalculator.proximityChecks(nonCollinearAPs)) {
                            Location trilateratedLocation = GeodesicCalculator.getLocationByTrilateration(nonCollinearAPs);
                            if (trilateratedLocation.getAccuracy() >= 0) {
                                trilateratedLocation.setTime(System.currentTimeMillis());
                                notifyStrongLocation(trilateratedLocation);
                            }else showToast("Bad measurements");
                        }else{
                            showToast("Bad APs");
                        }
                        Log.d("strongSec", "No server contact");
                        //showToast("No server contact");
                    }else { //Cant find non collinear APs
                        //Verify that all APs are within range and report the closest AP to both weak + strong
                        if(GeodesicCalculator.proximityChecks(aps) && strongestGeofiAP.getAccuracy() <= 25) {
                            strongestGeofiAP.setTime(System.currentTimeMillis());
                            notifyStrongLocation(strongestGeofiAP);
                            Log.d("strongSec", "No server contact");
                            //showToast("No server contact");
                        }
                        //showToast(strongestGeofiAP.getAccuracy()+"");
                    }
                }else { //weakSec only
                    if (nonCollinearAPs != null) {
                        Location trilateratedLocation = GeodesicCalculator.getLocationByTrilateration(nonCollinearAPs);
                        if (trilateratedLocation.getAccuracy() >= 0){
                            trilateratedLocation.setTime(System.currentTimeMillis());
                            notifyWeakLocation(trilateratedLocation);
                        }else showToast("Bad measurements");

                        Log.d("weakSec", "No server contact");
                        //showToast("No server contact");
                    } else if(strongestGeofiAP.getAccuracy() <= 25){ //Cant find non collinear APs
                        //Simply report the closest AP to weak registered apps
                        strongestGeofiAP.setTime(System.currentTimeMillis());
                        notifyWeakLocation(strongestGeofiAP);
                        Log.d("weakSec", "No server contact");
                        //showToast("No server contact");
                    }
                    //showToast(strongestGeofiAP.getAccuracy()+"");
                }

            //}
        }


        //Debug fabricate a call with the aps I want with the signal strengths I want (cc2, cc4, cc9)
//        nonColinearAPs = new ArrayList<Location>();
//        url = RevlocRestInterface.SERVICE_URL + "/Trilateration";
//        url += "/38.737328/-9.303201/" + ss;
//        url += "/38.737575/-9.302987/" + ss;
//        url += "/38.737564/-9.303394/" + ss;
//        Location loc = new Location(Geofi.GEOFI);
//        loc.setLatitude(38.737328);
//        loc.setLongitude(-9.303201);
//        loc.setAccuracy((float)calculateDistance(ss));
//        notifyAPLocation(loc);
//        Location loc1 = new Location(Geofi.GEOFI);
//        loc1.setLatitude(38.737575);
//        loc1.setLongitude(-9.302987);
//        loc1.setAccuracy((float)calculateDistance(ss));
//        notifyAPLocation(loc1);
//        Location loc2 = new Location(Geofi.GEOFI);
//        loc2.setLatitude(38.737564);
//        loc2.setLongitude(-9.303394);
//        loc2.setAccuracy((float)calculateDistance(ss));
//        notifyAPLocation(loc2);
//        ss = ss - 1;

        return wifiList;
    }

    //Notifies all registered apps of the location of APs used in trilateration (or not)
    public void notifyAPLocation(Location loc){
        ArrayList<String> strongAndWeakApps = new ArrayList<String>();
        strongAndWeakApps.addAll(weakApps.keySet());
        strongAndWeakApps.addAll(strongApps.keySet());
        //((MainActivity) context).sendNewLocation(loc, strongAndWeakApps); TODO: do something else
    }

    public void notifyWeakLocation(Location location){
        location.setProvider(PROVIDER_NAME);
        lastWeakLocation = location;

        //location.setTime(System.currentTimeMillis()); //DIFFERENT FROM THE SERVER'S!!

        //((Activity) context).sendNewLocation(location, new ArrayList<String>(weakApps.keySet())); TODO: do something else
        //If it was trilaterated it has an iterations bundle, if its an AP location, it doesnt obviously
        Bundle b = location.getExtras();
        String iterations = "Null";
        if(b != null)
            iterations = "" + b.get("iterations");
        //((MainActivity) context).updateLocationTextView("Wifi weak update!", "Iterations: " + iterations + " " + location.getAccuracy()); TODO: do something else
        //Tests
//        double refLat = 38.737345;
//        double refLong = -9.303051;
//        ((MainActivity) context).updateLocationTextView("Wifi weak update!", "" + GeodesicCalculator.getDistance(location.getLatitude(), location.getLongitude(), refLat, refLong) + " " + location.getAccuracy());

    }

    //Notifies both strong and weak location registered apps
    //In java objects are passed by value
    public void notifyStrongLocation(Location location){
        location.setProvider(PROVIDER_NAME);
        lastLocation = location;

        //location.setTime(System.currentTimeMillis()); //Time since epoch DIFFERENT FROM THE SERVER'S!!

        ArrayList<String> strongAndWeakApps = new ArrayList<String>();
        strongAndWeakApps.addAll(weakApps.keySet());
        strongAndWeakApps.addAll(strongApps.keySet());
        //((MainActivity) context).sendNewLocation(location, strongAndWeakApps); TODO: do something else
        //If it was trilaterated it has an iterations bundle, if its an AP location, it doesnt obviously
        Bundle b = location.getExtras();
        String iterations = "Null";
        if(b != null)
            iterations = "" + b.get("iterations");
        //((MainActivity) context).updateLocationTextView("Wifi strong update!", "Iterations: " + iterations + " " + location.getAccuracy()); TODO: do something else

        //Tests
//        double refLat = 38.737345;
//        double refLong = -9.303051;
//        ((MainActivity) context).updateLocationTextView("Wifi strong update!", "" + GeodesicCalculator.getDistance(location.getLatitude(), location.getLongitude(), refLat, refLong) + " " + location.getAccuracy());

    }

    public double calculateDistance(double signalLevelInDbm){
        //return LDPL.getDistance(signalLevelInDbm, 2.0, 0, -33); //empirical
        return LDPL.controlledLDPL(signalLevelInDbm);
    }

    private static void registerWifiReceiver(){ context.registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)); }
    public void unregisterWifiReceiver(){ context.unregisterReceiver(wifiReceiver); }


    //Response from the WebServiceTask a.k.a. notifyStrongLocation, will send to both weak and strong registered apps
    /* TODO: remove, this is from RevLOCInterface
    public void handleResponse(String response) {

        String security="";
        try {
            if (response.length() > 0) {
                //GET RESPONSE ===================
                if (response.charAt(0) == '{') { //If has real content, then expand JSON string GET
                    //obtain security level of the location response
                    String[] parts = response.split("«");
                    response = parts[0];
                    security = parts[1];

                    JSONObject jso = new JSONObject(response);
                    Log.i("JSONstring", response.charAt(0) + response);

                    double lattd = jso.getDouble(LATITUDE);
                    double longtd = jso.getDouble(LONGITUDE);
                    float accuracy = (float) jso.getDouble(ACCURACY);
                    long timestamp = jso.getLong(TIMESTAMP); //client timestamp (was sent to the server on the request)
                    //if valid location => valid measurement
                    if (accuracy >= 0) {
                        long id = jso.getLong(USER_ID); //TODO what to do with the user id?

                        Location location = new Location(PROVIDER_NAME);
                        location.setLatitude(lattd);
                        location.setLongitude(longtd);
                        location.setAccuracy(accuracy);
                        location.setTime(timestamp);
                        if (security.equals("strong"))
                            notifyStrongLocation(location);
                        else
                            notifyWeakLocation(location);

                        //((Activity) context).updateLocationTextView("Wifi update!", "\nFrom the server: " + lattd + " " + longtd + " " + accuracy); TODO: do something else
                    } else if (accuracy == -1) {
                        showToast("Bad measurements");
                    } else
                        showToast("Bad APs");
                    //POST RESPONSE from the call from the networkModule
                } else if (response.startsWith("Post")) { //Response to the POST with the stored Network locations
                    String[] parts = response.split("«");
                    response = parts[1];
                    Gson gson = new Gson();
                    Type datasetListType = new TypeToken<Collection<Location>>() {}.getType();
                    List<Location> locations = gson.fromJson(response, datasetListType);
                    //((Activity) context).updateLocationTextView("Post Response!", "Size:" + locations.size() + " " + response); //TODO: do something else
                    //Notify all non realtime apps registered with the Network provider.
                    //tested and working!
                    //((Activity) context).notifyNetworkNonRealTimeApps(locations); TODO: do something else
                    //Needs validation? Network does not validate...
                }
            }
        } catch (Exception e) {
            Log.e("WifiModule handleResponse", e.getLocalizedMessage(), e);
        }
    }
    */

    public void processUnsentScan(String urlWithScan){
//        switch(checkMaxEnergyStats()) {
//            case LOW: //store it
//                storeScan(urlWithScan);
//                break;
//            case MODERATE:
//                storeScan(urlWithScan);
//                break;
//            case HIGH: //compute location locally
//                computeLocationLocally(urlWithScan);
//                break;
//        }
        //showToast("RT:"+hasRealTimeApps()+"NRT:"+hasNonRealTimeApps());
        if(hasRealTimeApps() || checkMaxEnergyStats() == Profile.EnergyConsumption.HIGH){
            computeLocationLocally(urlWithScan);
        }
        else if(hasNonRealTimeApps()) { //avoid doing unnecessary storing if there are only real time apps registered
            storeScan(urlWithScan);
        }
    }

    private boolean hasRealTimeApps(){
        for(Profile.Synchronism sync : registeredSyncs.values()){
            if(sync == Profile.Synchronism.REALTIME)
                return true;
        }
        return false;
    }

    private boolean hasNonRealTimeApps(){
        for(Profile.Synchronism sync : registeredSyncs.values()){
            if(sync == Profile.Synchronism.ASYNC)
                return true;
        }
        return false;
    }

    private List<String> getNonRealTimeApps(){
        List<String> result = new ArrayList<String>();
        for(Map.Entry<String, Profile.Synchronism> entry : registeredSyncs.entrySet()){
            Profile.Synchronism sync = entry.getValue();
            if(sync == Profile.Synchronism.ASYNC)
                result.add(entry.getKey());
        }
        return result;
    }

    //Stores the url already filled with the contents of the wifigeofi scan
    public void storeScan(String urlWithScan){
        storedScans.add(urlWithScan);
        showToast("Wifigeofi stored scan!");
    }

    public boolean hasPendingLocations(){
        return !storedScans.isEmpty();
    }

    //Removes an url from the pendingScans and returns it. If it cannot complete the transfer this time,
    //the url will end up back in the list due to retries.
    public String getNextPendingLocation(){
        String storedUrl = storedScans.get(0);
        storedScans.remove(0);
        return storedUrl;
    }


    //Can only print the APs used in trilateration if running computations locally
    private void computeLocationLocally(String urlWithScan){
        //http://193.136.167.89:8080/spotx_rest/Trilateration/list/38.7371736111111:-9.303090277777784:-50«38.73709027777778:-9.303013888888898:-60«38.7373263888889:-9.30320138888888:-64«38.73692361111111:-9.30290972222221:-84«38.7375763888889:-9.302986111111125:-89/timestamp/1432761185128/security/weak
        String[] components = urlWithScan.split("list/");
        components = components[1].split("/timestamp/");
        String apsList = components[0];
        String timestamp = components[1].split("/security/")[0];

        String[] aps = apsList.split("«");

        ArrayList<Location> apLocations = new ArrayList<Location>();
        for(String ap : aps){
            components = ap.split(":");
            Location loc = new Location(Geofi.GEOFI);
            loc.setLatitude(Double.parseDouble(components[0]));
            loc.setLongitude(Double.parseDouble(components[1]));
            loc.setAccuracy((float) LDPL.controlledLDPL(Float.parseFloat(components[2])));
            apLocations.add(loc);
        }

        //go through the registered apps, see how many different security levels are required,
        //and a send a request to the server (or locally) for each type of security required
        boolean weakSec = !weakApps.isEmpty();
        boolean strongSec = !strongApps.isEmpty();

        // aps is filled with APs by order of proximity, now we do non colinear check on the list,8
        // obtaining a list with only 3 non collinear elements
        ArrayList<Location> nonCollinearAPs = GeodesicCalculator.getNonColinear(apLocations);
        Location strongestGeofiAP = apLocations.get(0);
        if (nonCollinearAPs != null) {
            if (showTrilaterationAPs) {
                for (Location ap : nonCollinearAPs) {
                    notifyAPLocation(ap);
                }
            }
        }
        if(strongSec) {
            if (nonCollinearAPs != null) {
                //Verify that the non collinear APs are within range and report the trilaterated position
                if(GeodesicCalculator.proximityChecks(nonCollinearAPs)) {
                    Location trilateratedLocation = GeodesicCalculator.getLocationByTrilateration(nonCollinearAPs);
                    if (trilateratedLocation.getAccuracy() >= 0) {
                        trilateratedLocation.setTime(Long.parseLong(timestamp));
                        notifyStrongLocation(trilateratedLocation);
                    }else showToast("Bad measurements");
                }else{
                    showToast("Bad APs");
                }
                Log.d("strongSec", "No server contact");
                //showToast("No server contact");
            }else { //Cant find non collinear APs
                //Verify that all APs are within range and report the closest AP to both weak + strong
                if(GeodesicCalculator.proximityChecks(apLocations) && strongestGeofiAP.getAccuracy() <= 25) {
                    strongestGeofiAP.setTime(Long.parseLong(timestamp));
                    notifyStrongLocation(strongestGeofiAP);
                    Log.d("strongSec", "No server contact");
                    //showToast("No server contact");
                }
                //showToast(strongestGeofiAP.getAccuracy()+"");
            }
        }else { //weakSec only
            if (nonCollinearAPs != null) {
                Location trilateratedLocation = GeodesicCalculator.getLocationByTrilateration(nonCollinearAPs);
                if (trilateratedLocation.getAccuracy() >= 0){
                    trilateratedLocation.setTime(Long.parseLong(timestamp));
                    notifyWeakLocation(trilateratedLocation);
                }else showToast("Bad measurements");

                Log.d("weakSec", "No server contact");
                //showToast("No server contact");
            } else if(strongestGeofiAP.getAccuracy() <= 25){ //Cant find non collinear APs
                //Simply report the closest AP to weak registered apps
                strongestGeofiAP.setTime(Long.parseLong(timestamp));
                notifyWeakLocation(strongestGeofiAP);
                Log.d("weakSec", "No server contact");
                //showToast("No server contact");
            }
            //showToast(strongestGeofiAP.getAccuracy()+"");
        }
        //showToast("Wifigeofi computed locally!");
    }


    //Very simple in the WifiModule case...
    public void requestInstantLocation(){ scan(); }

    @Override
    public void requestLocationUpdates(double dtCycle) {
        //ongoingRequests = true;
        if(dutyCycle == 0)//If not initialized by any other way (namely through the profile, it will accept the suggestion)
            dutyCycle = dtCycle;
        scheduleNextUpdate();
        scan();
    }

    @Override
    public void removeLocationUpdates() {
        //ongoingRequests = false;
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WifiScanIntentService.class);
        intent.setAction("WifiScan");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0); //schedule next update is cancelled
        mgr.cancel(pi);
        pi.cancel();
    }

    //Equivalent to the onLocationChanged from the GPS and Network modules
    public static class WifiScanIntentService extends IntentService {
        public WifiScanIntentService() {
            super("WifiScanIntentService");
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            Log.d(getClass().getSimpleName(), "WifiScanIntentService intent!");
            //Set next alarm
            scheduleNextUpdate();
            scan();
        }
    }

    public static void scheduleNextUpdate(){
        AlarmManager mgr= (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WifiScanIntentService.class);
        intent.setAction("WifiScan");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0);
        mgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() //since boot
                + Math.round(operationPeriod / (dutyCycle)), pi); //set is supposedly more precise than setRepeated
    }

    //Energy testing
    public static class TurnOnWifiIntentService extends IntentService {
        public TurnOnWifiIntentService() {
            super("TurnOnWifiIntentService");
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            Log.d(getClass().getSimpleName(), "TurnOnWifiIntentService intent!");

            WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
            wifiManager.setWifiEnabled(true);
        }
    }

    //Energy testing
    public static void scheduleTurnOnWifi(long intervalMillis){
        AlarmManager mgr= (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, TurnOnWifiIntentService.class);
        intent.setAction("TurnOnWifi");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0);
        mgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() //since boot
                + intervalMillis, pi); //set is supposedly more precise than setRepeated
    }

    //This one is only used for the defer button (debug), unlike the gps and network modules
    @Override
    public void deferNextUpdate() {
        boolean alarmUp = (PendingIntent.getService(context, 0,
                new Intent(context, WifiScanIntentService.class),
                PendingIntent.FLAG_NO_CREATE) != null);
        if(alarmUp) {
            removeLocationUpdates();
            scheduleNextUpdate();
            //((Activity) context).updateLocationTextView("Defer!", "--defering-- " + Math.round(operationPeriod / 1000) + "seconds"); TODO: do something else
        }
        //Else it does nothing... its a safe method
    }

    //Prints results as soon as the scan terminates -> results available are broadcast
    public class WifiReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            String scanResults = getScanResults();
            if (scanResults != null && showScanResults) {
                //((Activity) context).updateLocationTextView("Scan results!", scanResults); TODO: do something else
            }
        }
    }

    //If the module should behave differently depending on the security level required by an application
    public boolean isSecuritySensitive(){
        return true;
    }

    //Any repeated registers (for example for instant locations) will override the previous entry. This is ok.
    //Instant locations will have pretty much the same profile as the regular location request...
    //if not running start... no this decision belongs to the collector
    public void registerApp(Profile profile){
        String cls = profile.getCls();
        Profile.SecurityLevel level = profile.getSecurityLevel();
        Profile.Synchronism sync = profile.getSynchronism();
        Profile.EnergyConsumption energy = profile.getEnergy();

        //If already exists, it may be a security level update, anyway its always a put
        registeredApps.put(cls, level);
        //If its a security level update, remove the old, and after insert the new
        if(weakApps.containsKey(cls))
            weakApps.remove(cls);
        else if(strongApps.containsKey(cls))
            strongApps.remove(cls);

        switch(level){
            case WEAK:
                weakApps.put(cls, cls);
                break;
            case STRONG:
                strongApps.put(cls,cls);
                break;
        }

        registeredSyncs.put(cls, sync);

        //Quando se regista trata de actualizar as configuracoes de energia
        energyAppsMap.put(cls, energy);
        //Check max energy stats and update frequency and contactServer
        updateEnergySettings();
    }

    public void unregisterApp(String cls){
        registeredApps.remove(cls);
        if(weakApps.containsKey(cls))
            weakApps.remove(cls);
        else if(strongApps.containsKey(cls))
            strongApps.remove(cls);


        registeredSyncs.remove(cls);
        energyAppsMap.remove(cls);
        //REcheck max energy stats and update frequency and contactServer
        updateEnergySettings();
    }

    public boolean noAppsRegistered(){
        return registeredApps.isEmpty();
    }
}
