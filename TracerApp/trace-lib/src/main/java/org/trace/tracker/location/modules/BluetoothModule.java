package org.trace.tracker.location.modules;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;

/**
 * Created by rodri_000 on 01/02/2016.
 */
public class BluetoothModule {
    public final static int REQUEST_ENABLE_BT = 3;

    private BluetoothAdapter mBluetoothAdapter;
    private Context context;
    Hashtable<String, BluetoothDevice> devices = new Hashtable<String, BluetoothDevice>();
    StringBuilder scannedDevices = new StringBuilder();
    private Handler mHandler;
    public ConnectedThread commsThread;

    //For the client and server connection threads (the uuid is random... but the length is fixed!)
    private String uuid = "0f44e0aa96054a82b9e45fd24488274c";
    private final UUID MY_UUID = new UUID(
            new BigInteger(uuid.substring(0, 16), 16).longValue(),
            new BigInteger(uuid.substring(16), 16).longValue());

    //After starting discovery this receiver will pick up any devices, one call per device
    //We want to shut down the discovery after discovering a device to connect to, because
    //device discovery is a heavy procedure
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        // Create a BroadcastReceiver for ACTION_FOUND
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                scannedDevices.append(device.getName() + " | " + device.getAddress() + "\n");

                devices.put(device.getName(), device);
                //((BluetoothActivity)context).updateDetectedDevices(device.getName() + "&" + device.getAddress()); TODO: make BluetoothActivity independent
                // Stop discovery
                //mBluetoothAdapter.cancelDiscovery(); //XXX cancel being called twice in this code
            }
            //((BluetoothActivity) context).showDevices(getScannedDevices()); TODO: make BluetoothActivity independent
        }
    };

    public BluetoothModule(Context context, Handler handler) {
        this.context = context;
        this.mHandler = handler;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /*check if the device supports Bluetooth */
    public boolean supportsBluetooth(){
        if (mBluetoothAdapter == null)
            return false;// Device does not support Bluetooth

        return true;
    }

    /*enable Bluetooth if needed*/
	/* MAY NOT BE NEEDED: Enabling discoverability will automatically enable Bluetooth */
    public void enableBluetooth(){
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((Activity)context).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
	/* If enabling Bluetooth succeeds, your activity receives the RESULT_OK result code in
	 * the onActivityResult() callback. If Bluetooth was not enabled due to an error (or the
	 * user responded "No") then the result code is RESULT_CANCELED. */
    }

    public String getScannedDevices(){
        if(scannedDevices.toString().isEmpty())
            return "No bluetooth devices found!";
        return scannedDevices.toString();
    }

    /* Returns an empty string if no devices are paired */
    public String getPairedDevices(){

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        scannedDevices = new StringBuilder();

        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                scannedDevices.append(device.getName() + " : " + device.getAddress() +"\n");
            }
        }
        String result = scannedDevices.toString();
        scannedDevices = new StringBuilder(); //In case we start a scan we dont want the paired again
        return result;
    }

    //To receive notifications from discovered devices we need to register the broadcast receiver
    //we already declared
    //Isto aqui pode dar problemas... este bocado de codigo pode ter de ir para a activity
    //To be used also on onResume and whenever we want to start a scan...
    public void registerBroadcastReceiver(){
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        ((Activity)context).registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
    }

    //To be used on onPause and onDestroy
    public void unregisterBroadcastReceiver(){
        ((Activity)context).unregisterReceiver(mReceiver);
    }

    //Before discovering you need to register the broadcast receiver
    public void discover(){
        mBluetoothAdapter.startDiscovery();
    }

    /* The problem with this method is that it will ask the user EVERY TIME it is called... */
    public void triggerDiscoverable(){
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
        ((Activity)context).startActivity(discoverableIntent);
        Toast.makeText(context, "Bluetooth discoverable!",
                Toast.LENGTH_SHORT).show();
    }

    //Called by whomever sets itself discoverable
    public void startServer(){
        new AcceptThread().start();
    }

    //The device needed is obtained after the broadcast receiver gets a device resulting from a scan
    public void connectToServer(String name){
        BluetoothDevice device = devices.get(name);
        new ConnectThread(device).start();
    }


    //Ran by the one who is discoverable as the server
    //This thread will end as soon as it gets a connection
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("Blue", MY_UUID);
            } catch (IOException e) { }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    Log.d("ConTh", String.format("CONNECTED # # # # # #"));
                    commsThread = new ConnectedThread(socket);
                    commsThread.start();
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) { }
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) { }
        }
    }

    //Run by the one who is discovering as the client
    //This thread will end as soon as it gets a connection
    private class ConnectThread extends Thread {

        private final BluetoothSocket mmSocket;
        private BluetoothDevice device;

        public ConnectThread(BluetoothDevice dev) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            device = dev;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery(); //XXX called twice in this code

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            Log.d("ConTh", String.format("CONNECTED # # # # # #"));
            commsThread = new ConnectedThread(mmSocket);
            commsThread.start();
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    //Where the connection is managed by both server and client
    //When the socket is closed (ordered by the main activity) the run method will catch an exception
    //and the thread terminates
    public class ConnectedThread extends Thread{
        private static final int MESSAGE_READ = 4;
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    //XXX possibly pass the handler thru the constructor
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.d("ConedTh", String.format("Did not write"));
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}
