package org.trace.tracker.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileWriter {
    // Needs external storage write permission
    // Writes to Files folder in the root directory
    private static FileOutputStream fos;


    public static void writePublicFileToExternalStorageOnce(String data, String filename) {
        try {
            String extr = Environment.getExternalStorageDirectory().toString();
            File mFolder = new File(extr + "/Files");
            if (!mFolder.exists()) {
                mFolder.mkdir();
            }

            File f = new File(mFolder.getAbsolutePath(), filename);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data.getBytes());
            fos.flush();
            fos.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    //Writes one line at a time into the file specified
    public static void writePublicFileToExternalStorage(String data, String filename) {
        try {
            String extr = Environment.getExternalStorageDirectory().toString();
            File mFolder = new File(extr + "/Files");
            if (!mFolder.exists()) {
                mFolder.mkdir();
            }
            if(fos == null) {
                File f = new File(mFolder.getAbsolutePath(), filename);
                fos = new FileOutputStream(f);
            }
            fos.write(data.getBytes());
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    //To be used with 'writePublicFileToExternalStorage'
    public static void close(){
        try {
            fos.flush();
            fos.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }
}
