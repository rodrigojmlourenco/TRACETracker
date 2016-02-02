package org.trace.tracker.activity;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import org.trace.tracker.utils.MyUtil;

public class ActivityRecognitionIntentService extends IntentService{
    public ActivityRecognitionIntentService() {
        super("ActivityRecognitionIntent");
        Log.v("ActivityRecognitionIntentService", "IntentService Created");
    }

    /** Called when a new Activity Detection update is available. */
    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("onHandleIntent", "Activity Detected");

        // If the intent contains an update
        if (ActivityRecognitionResult.hasResult(intent)) {

            // Get the update
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            DetectedActivity mostProbableActivity = result.getMostProbableActivity();
            int confidence = mostProbableActivity.getConfidence();
            int activityType = mostProbableActivity.getType();
            //mostProbableActivity.getVersionCode();

//            results = result.toString();
            String results = getReadableActivity(activityType) + " confidence:" + confidence + " " + MyUtil.getTimeAsString(result.getTime());
//            notifyMain(results);
            notifyMain(mostProbableActivity);
        }
    }

    //Using a simple intent to pass information to the ActivityDetector, which passes it to the main activity
    private void notifyMain(String content){
        Intent responseIntent = new Intent();

        responseIntent.setAction(ActivityDetector.ActivityRecognitionReceiver.ACTION_RESP);
        responseIntent.addCategory(Intent.CATEGORY_DEFAULT);
        responseIntent.putExtra("Contents", content);
        sendBroadcast(responseIntent);
    }

    //Using a simple intent to pass information to the ActivityDetector, which passes it to the main activity
    private void notifyMain(DetectedActivity da){
        Intent responseIntent = new Intent();

        responseIntent.setAction(ActivityDetector.ActivityRecognitionReceiver.ACTION_RESP);
        responseIntent.addCategory(Intent.CATEGORY_DEFAULT);
        responseIntent.putExtra("DetectedActivity", da);
        sendBroadcast(responseIntent);
    }

    /**
     * @param activityType The detected activity type
     * @return A user-readable name for the type
     */
    public static String getReadableActivity(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "In vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "On bicycle";
            case DetectedActivity.RUNNING:
                return "Running";
            case DetectedActivity.WALKING:
                return "Walking";
            case DetectedActivity.ON_FOOT:
                return "On foot";
            case DetectedActivity.STILL:
                return "Still";
            case DetectedActivity.TILTING:
                return "Tilting";
            default:
                return "Unknown";
        }
    }

    public static boolean isStill(int activityType){
        return (activityType == DetectedActivity.STILL) ? true : false;
    }
}
