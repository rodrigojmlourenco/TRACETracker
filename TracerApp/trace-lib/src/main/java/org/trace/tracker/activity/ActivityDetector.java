package org.trace.tracker.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.google.android.gms.location.DetectedActivity;

import org.trace.tracker.location.fraud.LocationValidator;

public class ActivityDetector {
    private Context context;
    private static boolean lastWasStill = false;
    private static DetectedActivity lastActivity;

    IntentFilter filter;
    ActivityRecognitionReceiver ARReceiver;
    GoogleClientManager googleCM;


    public ActivityDetector(Context context) {
        this.context = context;

        googleCM = new GoogleClientManager();

        //Registering the receiver for the activity updates from the IntentService
        filter = new IntentFilter(ActivityRecognitionReceiver.ACTION_RESP);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        ARReceiver = new ActivityRecognitionReceiver();
    }

    public void setNewContext(Context context) {
        this.context = context;
    }

    public void requestUpdates() {
        context.registerReceiver(ARReceiver, filter); //Will receive activity updates from the GoogleApi
        googleCM.requestUpdates(context);
    }

    public void removeUpdates() {
        googleCM.removeUpdates();
        context.unregisterReceiver(ARReceiver);
    }


    // Will listen for intents sent by the IntentService to which the google client sends the updates
    // (specified on the onConnect() method) and updates the main activity
    public class ActivityRecognitionReceiver extends BroadcastReceiver {
        public static final String ACTION_RESP =
                "pt.ulisboa.tecnico.locapp.intent.action.MESSAGE_PROCESSED";

        public void onReceive(Context c, Intent intent) {
            Bundle extras = intent.getExtras();
//            String results = extras.getString("Contents");
//            ((MainActivity)context).updateLocationTextView("", results);
            DetectedActivity da = extras.getParcelable("DetectedActivity");
            //TODO Em vez de mostrar no collector, vai fazer store e mostra no collector tudo o q tem stored
            /* TODO: make MainActivity independent
            ((MainActivity)context).updateLocationTextView("",
                    ActivityRecognitionIntentService.getReadableActivity(da.getType()) + " confidence:"
                            + da.getConfidence() + " " + MyUtil.getTimeAsString(System.currentTimeMillis()));
            */

            // Decide whether or not to update the collector
            // So that duty-cycled modules can avoid operation if the device is still
            if(ActivityRecognitionIntentService.isStill(da.getType()) && da.getConfidence() > 0.9) {

                if(!lastWasStill) { //if before it was moving, we need to change
                    //((MainActivity) context).setStillStatus(true);//reports the still event to the collector TODO: make MainActivity independent
                    lastWasStill = true;
                }
            }else { //Currently moving
                if(lastWasStill){
                    //((MainActivity) context).setStillStatus(false);//reports the still event to the collector TODO: make MainActivity independent
                    lastWasStill = false;
                }
            }

            //TODO Decide whether or not to update the location validator with activity information
            //Only update when current status is different than the previous
            if(lastActivity == null || da.getType() != lastActivity.getType())
                LocationValidator.addActivityWithRemoval(da);

            //Update lastActivity
            lastActivity = da;
        }
    }

    //ActivityRecognitionClient deprecated
    //https://blacode.wordpress.com/2014/12/26/user-activity-recognition-through-new-activityrecognitionapi-in-android-activityrecognitionclient-deprecated/
    //Very useful for intentservice things:
    //https://github.com/pebble-bike/PebbleBike-AndroidApp/blob/master/src/com/njackson/MainActivity.java
}
