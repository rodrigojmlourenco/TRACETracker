package org.trace.tracker.location.modules;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.trace.tracker.ModuleInterface;
import org.trace.tracker.Profile;

import java.util.HashMap;


public class FusedLocationModule
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, ModuleInterface {

    Context context;
    LocationRequest locationRequest;
    FusedLocationProviderApi fusedLocationProviderApi;
    GoogleApiClient googleApiClient;
    private static boolean connected = false;
    private int interval = 20000;

    private static HashMap<String, Profile.SecurityLevel> registeredApps;

    public FusedLocationModule(Context ctx) {
        this();
        context = ctx;
    }

    public FusedLocationModule() {
        registeredApps = new HashMap<String, Profile.SecurityLevel>();
    }

    public void setNewContext(Context ctx) {
        context = ctx;
    }


    public void requestLocationUpdates() {

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(interval);
        locationRequest.setFastestInterval(interval);
        fusedLocationProviderApi = LocationServices.FusedLocationApi;

        googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        if (googleApiClient != null) {
            googleApiClient.connect();
        }

    }

    public void removeLocationUpdates() {
        fusedLocationProviderApi.removeLocationUpdates(googleApiClient, this);
    }

    @Override
    public void onConnected(Bundle arg0) {



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

        Log.d("FusedLocationModule", "Connected");

        fusedLocationProviderApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        connected = true;
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("FusedLocationModule","Connection SUSPENDED");
        connected = false;
    }

    @Override
    public void onLocationChanged(Location location) {

        Log.d("FusedLocationModule", "location from " + location.getProvider() + ":" + location.getLatitude() + " , " + location.getLongitude());

        //Toast.makeText(context, "location :" + location.getLatitude() + " , " + location.getLongitude(), Toast.LENGTH_SHORT).show();
        //((Activity) context).sendNewLocation(location, new ArrayList<String>(registeredApps.keySet())); TODO: make MainActivity indenpendent
        //((Activity) context).updateLocationTextView("Fused update!", location.getLatitude() + " , " + location.getLongitude()); TODO: make MainActivity independent
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d("FusedLocationModule","Connection FAILED");
        connected = false;
    }

    //ModuleInterface

    @Override
    public void registerApp(Profile profile) {
        String cls = profile.getCls();
        Profile.SecurityLevel level = profile.getSecurityLevel();

        //If already exists, it may be a security level update, anyway its always a put
        registeredApps.put(cls, level);
    }

    @Override
    public boolean isSecuritySensitive() { return false; }

    @Override
    public void unregisterApp(String cls) {
        registeredApps.remove(cls);
    }

    @Override
    public boolean noAppsRegistered() {
        return registeredApps.isEmpty();
    }
}
