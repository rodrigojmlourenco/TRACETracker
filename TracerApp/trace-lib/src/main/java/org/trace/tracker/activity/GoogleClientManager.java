package org.trace.tracker.activity;

import com.google.android.gms.common.api.GoogleApiClient;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.ActivityRecognition;

/**
 * Created by rodri_000 on 02/02/2016.
 */
public class GoogleClientManager implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    protected GoogleApiClient mGoogleApiClient;
    private PendingIntent mActivityRecognitionPendingIntent;

    long DETECTION_INTERVAL_MILLISECONDS = 10000; //every 10 secs

    public void requestUpdates(Context context) {
        createPendingIntent(context);
        buildGoogleApiClient(context); //used for activity recognition
        mGoogleApiClient.connect(); //on connection it will request AR updates
    }

    public void removeUpdates() {
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient,
                mActivityRecognitionPendingIntent);

        if (mGoogleApiClient.isConnected())
            mGoogleApiClient.disconnect();
    }

    protected synchronized void buildGoogleApiClient(Context context) {
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
//                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
                .build();
    }

    // Create the PendingIntent that Google Location Services uses
    // to send activity recognition updates back to this app.
    private void createPendingIntent(Context context){
        Intent intent = new Intent(context, ActivityRecognitionIntentService.class);
        mActivityRecognitionPendingIntent = PendingIntent.getService(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Runs when a GoogleApiClient object successfully connects. It will connect even without
     * an active internet connection */
    @Override
    public void onConnected(Bundle connectionHint) {

        Log.i("ADConnected", "AD Connected");
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient,
                DETECTION_INTERVAL_MILLISECONDS,
                mActivityRecognitionPendingIntent);
    }
    //Weird google play messages bug displayed in the log, is apparently normal:
    //http://stackoverflow.com/questions/18068627/logcat-message-the-google-play-services-resources-were-not-found-check-your-pr

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i("onConnectionFailed", "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }


    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i("onConnectionSuspended", "Connection suspended");
        mGoogleApiClient.connect();
    }
}
