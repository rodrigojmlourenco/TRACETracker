package org.trace.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by rodri_000 on 02/02/2016.
 */
public class ProfileBroadcastReceiver extends BroadcastReceiver {
    private static final String COLLECTOR_PACKAGE = "org.trace.tracker";
    private static final String PROFILE_RECEIVED = COLLECTOR_PACKAGE + ".Profile";
    private static Context ctx;


    public ProfileBroadcastReceiver(){
        super();
    }
    public ProfileBroadcastReceiver(Context context){
        ctx = context;
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("ProfileReceiver", "Received something!");
        if (intent != null && PROFILE_RECEIVED.equals(intent.getAction())) {

            String marshalledProfile = (String)intent.getExtras().get("profile");
            Profile profile = Profile.unmarshall(marshalledProfile);

            //((MainActivity)ctx).updateRegisteredApps(profile); TODO: make MainActivity independent
            Log.d("ProfileReceiver", profile.toString());
            Toast.makeText(context, "Collector: Received profile", Toast.LENGTH_SHORT).show();

        }
    }
}
