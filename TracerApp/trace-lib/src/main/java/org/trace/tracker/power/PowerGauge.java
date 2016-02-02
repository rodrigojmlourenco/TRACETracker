package org.trace.tracker.power;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.Log;

import org.trace.tracker.utils.FileWriter;
import org.trace.tracker.utils.SoundPlayer;

// Created by Kurt on 15-07-2015.
// Ver https://source.android.com/devices/tech/power/index.html para obter mais informacao
public class PowerGauge {
    private static Context context;
    private static int interval = 1800000; //milliseconds (30 min)

    public static Long getBestEnergyMeasureAvailable(Context context) {
        BatteryManager mBatteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

        Long energy;
        energy = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER); //nanoWatt-hour
        if(energy != Long.MIN_VALUE){
            Log.i("PowerGauge", "Remaining energy = " + energy + "nWh");
            return energy;
        }
        energy = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER); //microamp-hour
        if(energy != Long.MIN_VALUE){
            Log.i("PowerGauge", "Remaining capacity = " + energy + "uAh");
            return energy;
        }
        energy = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); //percentage capacity left (no decimal values)
        if(energy != Long.MIN_VALUE) {
            Log.i("PowerGauge", "Remaining capacity = " + energy + "%");
            return energy;
        }
        return -1L;
    }

    public static String getEnergyInfo() {
        BatteryManager mBatteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

        Long chargeCounter = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER); //microamp-hour
        Long capacityPercentage = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); //percentage capacity left (no decimal values)
        return "ChargeCounter:" + chargeCounter + " Capacity:" + capacityPercentage + "% Timestamp:" + SystemClock.elapsedRealtime();
    }

    public static String getSimpleEnergyInfo() {
        BatteryManager mBatteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

        Long chargeCounter = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER); //microamp-hour
        return "ChargeCounter:" + chargeCounter;
    }

    public static void setContext(Context c){
        context = c;
    }


    //Equivalent to the onLocationChanged from the GPS and Network modules
    public static class PowerGaugeIntentService extends IntentService {
        public PowerGaugeIntentService() {
            super("PowerGaugeIntentService");
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            Log.d(getClass().getSimpleName(), "PowerGaugeIntentService intent!");
            //Set next alarm
            measureAndScheduleNext();
        }
    }

    public static void measureAndScheduleNext(){
        String info = getSimpleEnergyInfo();
        FileWriter.writePublicFileToExternalStorage(info + "\n", "power_trace.txt");

        AlarmManager mgr= (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PowerGaugeIntentService.class);
        intent.setAction("PowerGauge");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0);
        mgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() //since boot
                + interval, pi); //Every 10 secs

        SoundPlayer.playR2d2Sound(context); //sound cue
    }

    public static void stopMeasurements() {
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PowerGaugeIntentService.class);
        intent.setAction("PowerGauge");
        PendingIntent pi = PendingIntent.getService(context, 0, intent, 0); //schedule next measurement is cancelled
        mgr.cancel(pi);
        pi.cancel();
    }
}
