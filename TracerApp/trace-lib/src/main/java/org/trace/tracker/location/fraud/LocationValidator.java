package org.trace.tracker.location.fraud;

import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.gms.location.DetectedActivity;

import org.trace.tracker.utils.GeodesicCalculator;

import java.util.ArrayList;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;


/** Created by Kurt on 11-04-2015.
 * All the duty-cycled localization methods, are validated by the Location Validator in one of 2 ways:
 * either checking the time and place of the last known position and seeing if it is humanly possible
 * given the transportation modes detected in between; or using DR to set a region within which the
 * user must be contained.
 * The Activity Detector detects and reports user activity state changes (static
 * or in-motion) to the Collector Manager and delivers the mode of transportation
 *         (on foot, cycling, in-vehicle, still) to the Location Validator, upon request.
 * To validate by AD, it uses the timestamp from the last valid location and obtains the detected activity
 * right before that timestamp and all subsequent activities. Then it obtains the intervals between activities
 * and calculates the total distance based on a speed estimate for each activity type.
 * The activities are maintained in a TreeMap for faster range lookup. To keep the map from growing indefinitely
 * we trim the timestamps older than 48h every time there is a new activity added.
 */
public class LocationValidator {


        private static Location lastLocation;
        private static long lastTimestamp;

        private static double boundary, actualDistance;

        private static TreeMap<Long, DetectedActivity> activities = new TreeMap<Long, DetectedActivity>();
        private final static long OLD_ENTRY_LIMIT = 1000*3600*48; //48h is the limit

        public LocationValidator(){
//        if(activities == null) {
//            activities = new TreeMap<Long, DetectedActivity>();
//        }
        }

        // Used by the Collector to set valid Locations (the ones that do not need to be validated such as
        // GPS? TODO think about this)
        public void setValidLocation(Location location){
            lastLocation = location;
        }



        //timestamp is millisSinceLastBoot
        //Rules for valid locations... Network is always valid?.. for now...
        //First location (the one where there is no lastLocation) is always valid? Depends on the provider?
        public static boolean isValid(Location location){
            long timestamp = SystemClock.elapsedRealtime();

            if(location.getProvider().equals(LocationManager.NETWORK_PROVIDER) || location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                lastTimestamp = timestamp;
                lastLocation = location;
                return true;
            }

            //first check if new timestamp is larger than old
            if(timestamp > lastTimestamp) {
                SortedMap<Long, DetectedActivity> subMap = getRelevantActivities(lastTimestamp);
                if(subMap == null) {
                    Log.d("Submap is null", "Submap is null");
                    return false;
                }
                for(Long l : subMap.keySet())
                    System.out.println(""+l);
                //then check if given the modes of transportation in between and the time, that distance is
                //coverable. the modes of transportation are obtained by consulting with the DR module.
                boundary = getDistanceFromActivities(subMap);
                //calculate the distance between the last valid location and the current location
                actualDistance = GeodesicCalculator.getDistance(lastLocation, location);
                Log.d("IsValid","B:"+boundary+"D:"+actualDistance);

                if(actualDistance <= boundary) {
                    lastLocation = location;
                    lastTimestamp = timestamp;
                    return true;
                }else return false;
            }else{ // If the phone was booted there is nothing in memory, so this should never happen...
//            return DRModule.isWithinRange(lastLocation, lastTimestamp, location, timestamp);
                return false;
            }
        }

        //Returns the last calculated boundary
        public static double getBoundary(){
            return boundary;
        }

        //Returns the last actualDistance
        public static double getActualDistance(){
            return actualDistance;
        }

        // Remove activities that are older than OLD_ENTRY_LIMIT (48hl)
        public static void removeOlderEntries(Long currentTime){
            //Long currentTime = System.currentTimeMillis();
            //System.out.println(""+currentTime);
            //If there is more than one element
            ArrayList<Long> toBeRemoved = new ArrayList<Long>();
            for(Map.Entry<Long,DetectedActivity> entry : activities.entrySet()) {
                Long key = entry.getKey();
                if(currentTime > key + OLD_ENTRY_LIMIT) { //only keep 24 hours
                    toBeRemoved.add(key);
                }else break;
            }
            if(!toBeRemoved.isEmpty())
                for(Long key : toBeRemoved){
                    //System.out.println("" + key);
                    activities.remove(key);
                }
            toBeRemoved = null; //facilitates GC
        }


        // Every time we add an activity we check for older ones and remove them
        // Used by the ActivityDetector to report detected activities
        public static void addActivityWithRemoval(DetectedActivity da){
            long currentTime = SystemClock.elapsedRealtime();
            removeOlderEntries(currentTime);
            activities.put(currentTime, da);
        }


        //Returns the submap of activities from timestamp up to current time.
        //TODO IA AQUI... activities esta a null.. usar instancia no main em vez de estatico... ele pode estar a fazer GC
        //Era o ceilingKey() que pode estar a retornar null
        //TODO talvez passar a lista para o AD afterall...
        public static SortedMap<Long, DetectedActivity> getRelevantActivities(long timestamp){
            Long floorKey = activities.floorKey(timestamp); //the greater key less than or equal to
            if(floorKey == null){
                String timestamps = "";
                for(long t : activities.keySet())
                    timestamps += t + " ";
                Log.d("getRelevantActivities", "Activity timestamps: "+timestamps);
                Log.d("getRelevantActivities", "No ceilingKey for timestamp: "+timestamp);
                return null;
            }
            long currentTime = SystemClock.elapsedRealtime();
            //System.out.println(" ceiling: " + ceilingKey + " lastKey: " + lastKey);
            return activities.subMap(floorKey, true, currentTime, true);
        }


        public static double getDistanceFromActivities(SortedMap<Long, DetectedActivity> subMap) {
            double boundary = 0;
            DetectedActivity priorActivity = null;
            long priorActivityTime = 0;
            for(Map.Entry<Long,DetectedActivity> entry : subMap.entrySet()){
                double speed = getSpeedFromActivity(priorActivity);
                double time = getTimeBetweenActivites(priorActivityTime, entry.getKey());
                boundary += speed * time;
                priorActivityTime = entry.getKey();
                priorActivity = entry.getValue();
            }
            return boundary;
        }

        //We can use average speeds, as we assume city traffic
        //Speeds in m/s
        public static double getSpeedFromActivity(DetectedActivity da){
            if(da != null)
                switch (da.getType()) {
                    case DetectedActivity.IN_VEHICLE:
                        return (0.0 + 50*1000)/3600; //50km/h in m/s (on the most congested cities the average value can be as low as 20km/h)
                    case DetectedActivity.ON_BICYCLE:
                        return (15.5*1000)/3600; //avg cycling speed in copenhagen
                    case DetectedActivity.ON_FOOT:
                        return (0.0 + 10*1000)/3600; //Jogging speed (fastest sustained speed)
                    case DetectedActivity.STILL:
                        return 0;
                    default:
                        return 0;
                }
            return 0;
        }

        //Time in secs
        public static double getTimeBetweenActivites(long priorActivityTime, long currentActivityTime){
            return (priorActivityTime == 0) ? 0 : (0.0 + currentActivityTime - priorActivityTime)/1000;
        }
}
