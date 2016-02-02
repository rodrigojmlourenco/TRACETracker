package org.trace.tracker.utils;

import android.location.Location;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rodri_000 on 01/02/2016.
 */
public class MyUtil {
    public static String getTimeSince(double eventNanos){ //in secs
        double conversionFactor = 1000000000; //nanos to secs
        double secs  = eventNanos/conversionFactor;
        double systemSecs = SystemClock.elapsedRealtimeNanos()/conversionFactor;

        double mins;
        double hours = 0;

        secs = systemSecs - secs;
        if(secs < 60){
            mins = 0;
            hours = 0;
        }
        else if(secs <= 3600){
            mins = secs/60;
            secs = secs%60;
        }
        else{
            hours = secs/3600;
            mins = secs%3600/60;
            secs = secs%60;
        }

        return String.format("%.0f", hours) +"h " +
                String.format("%.0f", mins) + "m " + String.format("%.0f", secs) +"s";
    }

    public static String getTimeAsString(long milliseconds) { //time since epoch UTC
        return new SimpleDateFormat("dd/MM/yy HH:mm:ss").format(new Date(milliseconds));
    }

//    public static byte[] CoordstoByteArray(double latd, double longt) {
//        byte[] latArray = new byte[8];
//        byte[] longArray = new byte[8];
//        ByteBuffer.wrap(latArray).putDouble(latd);
//        ByteBuffer.wrap(longArray).putDouble(longt);
//        byte[] mServiceData = new byte[latArray.length + longArray.length];
//        System.arraycopy(latArray,0,mServiceData,0         ,latArray.length);
//        System.arraycopy(longArray,0,mServiceData,latArray.length,longArray.length);
//        return mServiceData;
//    }
//
//    public static Location ByteArraytoLocation(byte[] bytes) {
//        byte[] resultLat = new byte[8];
//        byte[] resultLong = new byte[8];
//        System.arraycopy(bytes,0, resultLat, 0, 8);
//        System.arraycopy(bytes,8, resultLong, 0, 8);
//        Location loc = new Location("MyUtil");
//        loc.setLatitude(ByteBuffer.wrap(resultLat).getDouble());
//        loc.setLongitude(ByteBuffer.wrap(resultLong).getDouble());
//        return loc;
//    }


    public static byte[] DoubletoByteArray(double value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(value);
        return bytes;
    }

    public static double ByteArraytoDouble(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getDouble();
    }




    public static byte[] CoordstoByteArray(double latd, double longt, double accuracy) {
        byte[] latArray = new byte[8];
        byte[] longArray = new byte[8];
        byte[] accuracyArray = new byte[8];
        ByteBuffer.wrap(latArray).putDouble(latd);
        ByteBuffer.wrap(longArray).putDouble(longt);
        ByteBuffer.wrap(accuracyArray).putDouble(accuracy);

        byte[] checkSum = computeCheckSum(latArray, longArray);

        byte[] mServiceData = new byte[latArray.length + longArray.length + 4];
        System.arraycopy(latArray,0,mServiceData,0         								,latArray.length);
        System.arraycopy(longArray,0,mServiceData,latArray.length						,longArray.length);
        System.arraycopy(checkSum,0,mServiceData,latArray.length +longArray.length	, 1);
        System.arraycopy(accuracyArray,0,mServiceData,latArray.length +longArray.length	+1, 3);
        //System.arraycopy(tail,0,mServiceData,head.length+latArray.length+longArray.length+4, tail.length);
        return mServiceData;
    }

    public static Location ByteArraytoLocation(byte[] bytes) {
        try{
            byte[] checkSum = new byte[1];
            byte[] resultLat = new byte[8];
            byte[] resultLong = new byte[8];

            byte[] resultAccuracy = new byte[3];
            byte[] resultAccuracy2 = new byte[8];

            System.arraycopy(bytes,0, resultLat, 0, 8);
            System.arraycopy(bytes,8, resultLong, 0, 8);
            System.arraycopy(bytes,16, checkSum, 0, 1);
            System.arraycopy(bytes,17, resultAccuracy, 0, 3);


            System.arraycopy(resultAccuracy,0, resultAccuracy2, 0, 3);
            System.arraycopy(new byte[]{(byte)0,(byte)0,(byte)0,(byte)0,(byte)0},0, resultAccuracy2, 3, 5);


            byte[] expectedCheckSum = computeCheckSum(resultLat, resultLong);

            if(Arrays.equals(checkSum, expectedCheckSum)) {
                //Log.d("ByteArraytoLocation", "Byte array sent is an encoded location!");

                Location loc = new Location("MyUtil");
                loc.setLatitude(ByteBuffer.wrap(resultLat).getDouble());
                loc.setLongitude(ByteBuffer.wrap(resultLong).getDouble());
                loc.setAccuracy((float)ByteBuffer.wrap(resultAccuracy2).getDouble());
                return loc;
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }

    public static byte[] computeCheckSum(byte[] latArray, byte[] longArray){
        //get an int hashcode the 4byte array that is the int for latitude and longitude
        ByteBuffer b1 = ByteBuffer.allocate(4);
        b1.putInt(Arrays.hashCode(latArray));
        byte[] b1Result = b1.array();

        ByteBuffer b2 = ByteBuffer.allocate(4);
        b2.putInt(Arrays.hashCode(longArray));
        byte[] b2Result = b2.array();

        //get the first two bytes of those arrays
        byte[] head = new byte[2];
        byte[] tail = new byte[2];
        System.arraycopy(b1Result,0, head, 0, 2);
        System.arraycopy(b2Result,0, tail, 0, 2);

        //perform checkSum
        byte[] result = new byte[1];
        result[0] = (byte)(head[0]+head[1]+tail[0]+tail[1]);

//        System.out.println(Arrays.toString(latArray) + " " + Arrays.toString(longArray));
//        System.out.println(			Arrays.toString(head)
//                + " " + Arrays.toString(tail)
//                + " " + Arrays.toString(result));

        return result;
    }

    public static boolean isLatLong(String latlong){
        //String latlong = "38.123456, -9.123456";
        String regex_coords = "^[-+]?([1-8]?\\d(\\.\\d+)?|90(\\.0+)?),\\s*[-+]?(180(\\.0+)?|((1[0-7]\\d)|([1-9]?\\d))(\\.\\d+)?)$";
        Pattern compiledPattern2 = Pattern.compile(regex_coords, Pattern.CASE_INSENSITIVE);
        Matcher matcher2 = compiledPattern2.matcher(latlong);
        while (matcher2.find()) {
            //System.out.println("Is Valid Map Coordinate: " + matcher2.group());
            return true;
        }
        return false;
    }
}
