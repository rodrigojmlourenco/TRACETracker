package org.trace.tracker.location.modules;

import android.content.Context;

import org.trace.tracker.ModuleInterface;
import org.trace.tracker.Profile;
import org.trace.tracker.utils.MyUtil;

import android.location.Location;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by rodri_000 on 02/02/2016.
 */
public class QRCodeModule implements ModuleInterface {

    private static final String TAG = QRCodeModule.class.getSimpleName();

    private static Context context;
    private static HashMap<String, Profile.SecurityLevel> registeredApps;


    public QRCodeModule(){}

    public QRCodeModule(Context ctx){
        context = ctx;
        registeredApps = new HashMap<String, Profile.SecurityLevel>();
    }

    public void setContext(Context ctx) {
        context = ctx;
    }

    public void processContent(String qrContent){
        Location location = stringToLocation(qrContent);

        /*
        TODO: make MainActivity independent
        if(location != null)
            ((MainActivity) context).sendNewLocation(location, new ArrayList<String>(registeredApps.keySet()));
        */
    }

    public Location stringToLocation(String qrContent){

        String coords = qrContent.replaceAll("\\s+","");

        if(MyUtil.isLatLong(coords)) {
            String[] parts = coords.split(",");
            Location loc = new Location("qr");
            loc.setLatitude(Double.parseDouble(parts[0]));
            loc.setLongitude(Double.parseDouble(parts[1]));
            loc.setAccuracy(0);
            return loc;
        }
        return null;
    }



    //ModuleInterface methods
    @Override
    public void registerApp(Profile profile) {
        String cls = profile.getCls();
        Profile.SecurityLevel level = profile.getSecurityLevel();

        //If already exists, it may be a security level update, anyway its always a put
        registeredApps.put(cls, level);
    }

    @Override
    public void unregisterApp(String cls) {
        registeredApps.remove(cls);
    }

    @Override
    public boolean isSecuritySensitive() {
        return false;
    }

    @Override
    public boolean noAppsRegistered() {
        return registeredApps.isEmpty();
    }

    //TODO: make this part of a TriggerInterface
    public static void sendLocationToCollector(Location location){
        /* TODO: make MainActivity independent
        ((MainActivity) context).sendNewLocation(location, new ArrayList<String>(registeredApps.keySet()));
        ((MainActivity) context).updateLocationTextView("NFC update!", location.toString());
        */
    }
}
