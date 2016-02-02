package org.trace.tracker.location.modules;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;

import org.trace.tracker.ModuleInterface;
import org.trace.tracker.Profile;
import org.trace.tracker.location.DutyCycleInterface;
import org.trace.tracker.utils.MyUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.trace.tracker.Profile.*;

/**
 * @author Kurt
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BLEModule implements DutyCycleInterface, ModuleInterface {

        public final static int REQUEST_ENABLE_BT = 3;

        private static BluetoothAdapter mBluetoothAdapter;
        private static Context context;
        private static Location lastLocation;

        private final BluetoothManager bluetoothManager;

        private static HashMap<String, Profile.SecurityLevel> registeredApps;
        private static HashMap<String, EnergyConsumption> energyAppsMap;

        private static boolean debug = false;

        //BLE
        private static float BLE_RANGE = 10.0f;
        private static final int ADVERTISE_TIME_MS = 10000;

        private final static int ALARM_MANAGER_SCAN_PERIOD = 20000; //20 secs
        private static int operationPeriod = ALARM_MANAGER_SCAN_PERIOD;
        private static double dutyCycle = 1;

        private static final int SCAN_TIME_MS = 10000;
        private static Handler scanHandler = new Handler();
        private static List<ScanFilter> scanFilters = new ArrayList<ScanFilter>();
        private static ScanSettings scanSettings;
        private static boolean isScanning = false;
        private boolean gotLocation = false;
        private static BluetoothLeScanner scanner;
        private static Runnable scanRunnable = new Runnable() {


            @Override
            public void run() {
                scanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
                if (!isScanning) {
                    Log.d("scanRunnable", "Started BLE scan");
                    scanner.startScan(scanFilters, scanSettings, scanCallback);
                    isScanning = true;
                    scanHandler.postDelayed(this, SCAN_TIME_MS); //In 10s the scan will stop regardless
                } else {
                    Log.d("scanRunnable","Finished BLE scan");
                    scanner.stopScan(scanCallback); //if log d comes out as 'could not find callback wrapper, it means it has already been stopped
                    isScanning = false;
                    //gotLocation = false;
                }
            }
        };
        private Runnable stopAdvertiseRunnable = new Runnable() {
            @Override
            public void run() {
                stopAdvertisements();
            }
        };

        //BLE advertisements
        BluetoothLeAdvertiser mLeAdvertiser;
        private final AdvertiseCallback mLeAdvCallback = new AdvertiseCallback() {
            public void onStartSuccess (AdvertiseSettings settingsInEffect) {
                Log.d("AdvertiseCallback", "onStartSuccess:" + settingsInEffect);
            }

            public void onStartFailure(int errorCode) {
                String description = "";
                if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED) description = "ADVERTISE_FAILED_FEATURE_UNSUPPORTED";
                else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) description = "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS";
                else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) description = "ADVERTISE_FAILED_ALREADY_STARTED";
                else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE) description = "ADVERTISE_FAILED_DATA_TOO_LARGE";
                else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR) description = "ADVERTISE_FAILED_INTERNAL_ERROR";
                else description = "unknown";
                Log.e("AdvertiseCB", "onFailure error:" + errorCode + " " + description);
            }
        };

        //After starting the scan this receiver will pick up any devices, one call per device
        //We want to shut down the discovery after discovering a device to connect to, because
        //device discovery is a heavy procedure
        private static ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                //Log.d("scanCallBack", result.getScanRecord().toString());
//            Map<ParcelUuid, byte[]> map = result.getScanRecord().getServiceData();
//            Collection<ParcelUuid> colParcels = map.keySet();
//            Log.d("scanCallback", "**** " + colParcels.iterator().next().toString());
//
//            Collection<byte[]> colCoords = map.values();
//            byte[] coords = colCoords.iterator().next(); //first

                //Get the serviceData byte array only from devices broadcasting our service
                byte[] coords = result.getScanRecord().getServiceData(ParcelUuid.fromString("0000feff-0000-1000-8000-00805f9b34fb"));

                if(coords != null){
                    //Log.d("scanCallBack", "$$$$ " + Arrays.toString(coords));
                    Location loc = (MyUtil.ByteArraytoLocation(coords));
                    if(loc != null){
                        scanner.stopScan(scanCallback);
                        loc.setProvider("ble");
                        loc.setAccuracy(loc.getAccuracy() + BLE_RANGE); //adds its own innacuracy to the final accuracy
                        Log.d("scanCallBack", "#### " + loc.toString());
                        lastLocation = loc;

                        //Send the location to the Collector
                        /* TODO: make BLEActivity independent
                        if(debug)
                            ((BLEActivity) context).updateUI(loc.toString() + "\n");
                        else {
                            ((MainActivity) context).sendNewLocation(lastLocation, new ArrayList<String>(registeredApps.keySet()));
                            ((MainActivity) context).updateLocationTextView("BLE update!", loc.getLatitude() + " " + loc.getLongitude() + " " + loc.getAccuracy());
                        }
                        */
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                // a scan error occurred
            }
        };

        public BLEModule(Context context) {
            this.context = context;
            //this.mHandler = handler;
//        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();

            registeredApps = new HashMap<String, Profile.SecurityLevel>();
            energyAppsMap = new HashMap<String, Profile.EnergyConsumption>();
        }

        public void setDebugMode(){debug = true;}

        public void setNewContext(Context ctx) {
            context = ctx;
        }

        // Check whether BLE is supported on the device
        public boolean supportsBLE(){
            return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        }

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
	/* MAY NOT BE NEEDED: Enabling discoverability will automatically enable Bluetooth */
        public void enableBluetooth(){
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                ((Activity)context).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
	/* If enabling Bluetooth succeeds, your activity receives the RESULT_OK result code in
	 * the onActivityResult() callback. If Bluetooth was not enabled due to an error (or the
	 * user responded "No") then the result code is RESULT_CANCELED. */
        }


        public static void scanBLE() {
            if(mBluetoothAdapter.isEnabled()) {
                ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
                scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_BALANCED); //LOW - 1 scan a cada 10s, BALANCED 1 scan por segundo
                scanSettings = scanSettingsBuilder.build();

                scanHandler.post(scanRunnable);
//        mBluetoothAdapter.startLeScan(mLeScanCallback); //deprecated from kitkat 4.4
            }
            else
                Log.e("scanBLE", "Did not scan, Bluetooth was turned off.");
        }


        //Nexus 4,5,7 nao podem fazer advertisements (adv so sao permitidos no lollipop, aos telefones com chip apropriado)...
        //http://stackoverflow.com/questions/26441785/does-bluetoothleadvertiser-work-on-a-nexus-5-with-android-5-0
        public boolean allowsBLE() {
            if (mBluetoothAdapter.isEnabled()) {
                if (mBluetoothAdapter.isMultipleAdvertisementSupported())
                    Log.d("allowsBLE","isMultiple.. supported");
                if(mBluetoothAdapter.getBluetoothLeAdvertiser() != null) {
                    Log.d("allowsBLE","advertiser non null!");
                    return true;
                }
                Log.d("allowsBLE","No BLE advertisements.. :(");
                return false;
            }
            Log.d("allowsBLE","Adapter not enabled..");
            return false;
        }

        public void advertiseBLE(double latd, double longt, double accuracy){
            if(mBluetoothAdapter.isEnabled()) {
                //        BluetoothLeAdvertiser myBluetoothLeAdvertiser =  mBluetoothAdapter.getBluetoothLeAdvertiser();
                //        myBluetoothLeAdvertiser.startAdvertising(new AdvertiseSettings(), AdvertiseData advertiseData, AdvertiseCallback callback);

                ParcelUuid mAdvParcelUUID = ParcelUuid.fromString("0000FEFF-0000-1000-8000-00805F9B34FB");

                mLeAdvertiser = (BluetoothLeAdvertiser)((BluetoothAdapter)((BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter()).getBluetoothLeAdvertiser();
                if (mLeAdvertiser == null)
                {
                    Log.e("startAdvertising", "didn't get a bluetooth le advertiser");
                    return;
                }

                AdvertiseSettings.Builder mLeAdvSettingsBuilder =
                        new AdvertiseSettings.Builder().setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);//Tested: HIGH 15m, MEDIUM 10m, LOW is about 1m
                mLeAdvSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
                mLeAdvSettingsBuilder.setConnectable(false);

                AdvertiseData.Builder mLeAdvDataBuilder = new AdvertiseData.Builder();


                //        List<ParcelUuid> myUUIDs = new ArrayList<ParcelUuid>();
                //        myUUIDs.add(ParcelUuid.fromString("0000FE00-0000-1000-8004-00805F9B34FB"));

                //        byte mServiceData[] = { (byte)0xff, (byte)0xfe, (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04,
                //                (byte)5, (byte)6, (byte)7, (byte)8, (byte)9, (byte)10, (byte)11, (byte)12, (byte)13, (byte)14, (byte)15, (byte)16};
                byte[] mServiceData = MyUtil.CoordstoByteArray(latd, longt, accuracy);
                mLeAdvDataBuilder.addServiceData(mAdvParcelUUID, mServiceData);


                //DONE tentar ver se consigo po-lo a scannar apenas o uuid que quero
                //DONE timeout nos advertisements
                //DONE incorporar com o duty cycle interface para por os scans em duty cycle
                //DONE enviar a location para o Collector
                //TODO enviar tambem a accuracy

                //        AdvertiseSettings.Builder advSetBuilder = new AdvertiseSettings.Builder();
                //        advSetBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
                //        advSetBuilder.setConnectable(false);
                //        advSetBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
                //        advSetBuilder.setTimeout(10000);
                //        Log.d("advBuild", "settings:" + advSetBuilder.build());

                //        AdvertiseData.Builder advDataBuilder = new AdvertiseData.Builder();
                //        advDataBuilder.setIncludeDeviceName(false);
                //        advDataBuilder.setIncludeTxPowerLevel(true);
                //        advDataBuilder.addServiceData(mAdvParcelUUID, mServiceData);

                mLeAdvertiser.startAdvertising(mLeAdvSettingsBuilder.build(), mLeAdvDataBuilder.build(), mLeAdvCallback);

                scanHandler.postDelayed(stopAdvertiseRunnable, ADVERTISE_TIME_MS); //In 10s stops the advertisement
            }
            else
                Log.e("advertiseBLE", "Did not scan, Bluetooth was turned off.");
        }

        /**
         * Stop Advertisements
         */
        public void stopAdvertisements() {
            if (mLeAdvertiser != null) {
                Log.d("stopAdvertisements", "Stopped BLE advertisement");
                mLeAdvertiser.stopAdvertising(mLeAdvCallback);
            }
        }

        @Override
        public void requestLocationUpdates(double dtCycle) {
            Log.d("BLE", "requestLocationUpdates");
            if(dutyCycle == 0)//If not initialized by any other way (namely through the profile, it will accept the suggestion)
                dutyCycle = dtCycle;
            scheduleNextUpdate();
            scanBLE();
        }


        @Override
        public void removeLocationUpdates() {
            Log.d("BLE", "removeLocationUpdates");
            AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, BLEScanIntentService.class);
            intent.setAction("BLEScan");
            PendingIntent pi = PendingIntent.getService(context, 0, intent, 0); //schedule next update is cancelled
            mgr.cancel(pi);
            pi.cancel();
        }

        @Override
        public void deferNextUpdate() {
            //Doesn't make sense for BLE
        }

        @Override
        public void requestInstantLocation() {
            //Doesn't make sense for BLE
        }

        public static void scheduleNextUpdate(){
            AlarmManager mgr= (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, BLEScanIntentService.class);
            intent.setAction("BLEScan");
            PendingIntent pi = PendingIntent.getService(context, 0, intent, 0);
            mgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() //since boot
                    + Math.round(operationPeriod / (dutyCycle)), pi); //set is supposedly more precise than setRepeated
        }


        //Equivalent to the onLocationChanged from the GPS and Network modules
        public static class BLEScanIntentService extends IntentService {
            public BLEScanIntentService() {
                super("BLEScanIntentService");
            }

            @Override
            protected void onHandleIntent(Intent intent) {
                Log.d(getClass().getSimpleName(), "BLEScanIntentService intent!");
                //Set next alarm
                scheduleNextUpdate();
                scanBLE();
            }
        }


        @Override
        public void registerApp(Profile profile) {
            String cls = profile.getCls();
            EnergyConsumption energy = profile.getEnergy();
            SecurityLevel level = profile.getSecurityLevel();

            //If already exists, it may be a security level update, anyway its always a put
            registeredApps.put(cls, level);
            energyAppsMap.put(cls, energy);
            //updateEnergySettings(); No need as here the dutyCycle is always 1
            //if not running start... no this decision belongs to the collector
        }

        @Override
        public void unregisterApp(String cls) {
            registeredApps.remove(cls);
            energyAppsMap.remove(cls);
            //if nothing left stop... no this decision belongs to the collector
        }

        @Override
        public boolean isSecuritySensitive() {
            return false;
        }

        @Override
        public boolean noAppsRegistered() {
            return registeredApps.isEmpty();
        }
    }

