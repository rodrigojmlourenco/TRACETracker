package org.trace.tracker.utils;

import android.location.Location;
import android.os.Bundle;

import org.trace.tracker.location.modules.WifiModule;

import java.util.ArrayList;

/**
 * @version v1.1
 * This class encapsulates:
 * - getLocationByTrilateration(lat1, long1, dist1, lat2, ...)
 * - getDistance(lat1, long1, lat2 ,long2)
 * - getCoordsFromDistanceAndBearing(dist, bearing, lat, longt)
 *
 * v1.3
 * - double getBearingFromTo(startLat, startLng, endLat, endLng)
 * - double getAngle(Location l1, Location l2, Location l3)
 * - ArrayList<Location> getNonColinear(ArrayList<Location> locations)
 *
 * v1.4
 * - boolean proximityChecks(ArrayList<Location> aps)
 *
 * V1.5
 * Locations now report the iterations of trilateration in a bundle
 *
 * v1.6
 * Added getDistance overloaded method for Location objects
*/
public class GeodesicCalculator {
    private static final int earthRadius = 6378137; //at equator

    private static final int SUM = 0;
    private static final int SUBTRACT = 1;
    private static final int MULTIPLY = 2;
    private static final int DIVIDE = 3;
    private static final int DOT_PRODUCT = 4;

    public static WifiModule wifiModule;

    //decision step
    private static final double step = 0.5;


    public static Location getLocationByTrilateration(ArrayList<Location> aps) {

        Location[] locArr = new Location[aps.size()];
        locArr = aps.toArray(locArr);
        double latitude1 = locArr[0].getLatitude();
        double longitude1 = locArr[0].getLongitude();
        double dist1 = locArr[0].getAccuracy();
        double latitude2 = locArr[1].getLatitude();
        double longitude2 = locArr[1].getLongitude();
        double dist2 = locArr[1].getAccuracy();
        double latitude3 = locArr[2].getLatitude();
        double longitude3 = locArr[2].getLongitude();
        double dist3 = locArr[2].getAccuracy();
        return getLocationByTrilateration(latitude1, longitude1, dist1,
                latitude2, longitude2, dist2,
                latitude3, longitude3, dist3, 0);
    }

    public static Location getLocationByTrilateration(double latitude1, double longitude1, double dist1,
                                                      double latitude2, double longitude2, double dist2,
                                                      double latitude3, double longitude3, double dist3, int iteration) {

//        //Security: Verify that the 3 points are within 200m of each other
        double ab = getDistance(latitude1, longitude1,
                latitude2, longitude2);
        double bc = getDistance(latitude2, longitude2,
                latitude3, longitude3);
        double ca = getDistance(latitude3, longitude3,
                latitude1, longitude1);
//        if(ab>200 || bc>200 || ca>200) {
//            Location result = new Location("GeodesicCalculator");
//            result.setAccuracy(-4);
//            result.setLatitude(100);
//            result.setLongitude(200);
//            return result; //Bad APs
//        }

        //If the closest distance is within 10, the return the location of
        //that AP + the distance to it as an error
        double latitude = 0;
        double longitude = 0;
        double decision1 = 0, decision2 = 0, decision3 = 0;
        float closest;
        if(dist1<=dist2 && dist1<=dist3){
            closest = (float)dist1;
            latitude = latitude1;
            longitude = longitude1;
        }else if(dist2<=dist3 && dist2<=dist1){
            closest = (float)dist2;
            latitude = latitude2;
            longitude = longitude2;
        }else{
            closest = (float)dist3;
            latitude = latitude3;
            longitude = longitude3;
        }
        //If I'm less than 10m from an AP, I'm at its position
        if(closest <= 10){
            Location result = new Location("GeodesicCalculator");
            result.setAccuracy(closest);
            result.setLatitude(latitude);
            result.setLongitude(longitude);
            return result;
        }

        //Future work?
        //A possible improvement on the algorithm: shortest distance dictates
        //whether the point is within the triangle or outside it
        //then maybe use simple average instead of trilateration

        //Convert to radians
        double lat1 = toRadians(latitude1);
        double long1 = toRadians(longitude1);
        double lat2 = toRadians(latitude2);
        double long2 = toRadians(longitude2);
        double lat3 = toRadians(latitude3);
        double long3 = toRadians(longitude3);

        //System.out.println("Positions: \n" +lat1+" "+long1+"\n"+lat2+" "+long2+"\n"+lat3+" "+long3+"\n");

        //Convert to cartesian coordinates ECEF (earth fixed, earth centered)
        //the x-axis goes through long,lat (0,0), so longitude 0 meets the equator;
        //the y-axis goes through (0,90);
        //and the z-axis goes through the poles.
        double x1 = earthRadius * cos(lat1) * cos(long1);
        double y1 = earthRadius * cos(lat1) * sin(long1);
        double z1 = earthRadius * sin(lat1);

        double x2 = earthRadius * cos(lat2) * cos(long2);
        double y2 = earthRadius * cos(lat2) * sin(long2);
        double z2 = earthRadius * sin(lat2);

        double x3 = earthRadius * cos(lat3) * cos(long3);
        double y3 = earthRadius * cos(lat3) * sin(long3);
        double z3 = earthRadius * sin(lat3);

        double[] P1 = {x1, y1, z1};
        double[] P2 = {x2, y2, z2};
        double[] P3 = {x3, y3, z3};
        //Taken from: http://gis.stackexchange.com/questions/66/trilateration-using-3-latitude-and-longitude-points-and-3-distances
        //http://en.wikipedia.org/wiki/Trilateration
//		#from wikipedia
//		#transform to get circle 1 at origin
//		#transform to get circle 2 on x axis
//		ex = (P2 - P1)/(numpy.linalg.norm(P2 - P1))
        double[] ex = arrayNormalized(3, arrayOperation(3, SUBTRACT, P2 , P1));
        if(ex.length <= 0){
            //Never happens as array should always have 3 elements
        }
//		i = dot(ex, P3 - P1)
        double i = arrayToScalarOperation(3, DOT_PRODUCT, ex, arrayOperation(3, SUBTRACT, P3 , P1));
        if(i <= 0){
            //Never happens as array should always have 3 elements
        }
        //		ey = (P3 - P1 - i*ex)/(numpy.linalg.norm(P3 - P1 - i*ex))
        double[] ey = arrayOperation(3, SUBTRACT, P3 , P1);
        ey = arrayNormalized(3, arrayOperation(3, SUBTRACT, ey, scalarOnArrayOperation(3, MULTIPLY, i, ex)));
        if(ey.length <= 0){
            //Never happens as array should always have 3 elements
        }
//		ez = numpy.cross(ex,ey)
        double[] ez = crossProduct(ex, ey);
        if(ez.length <= 0){
            //Never happens as array should always have 3 elements
        }
//		d = numpy.linalg.norm(P2 - P1)
        double d = getNorm(3, arrayOperation(3, SUBTRACT, P2 , P1));
        //double d = P2[0]; //d Is the x coordinate of point P2.
//		j = dot(ey, P3 - P1)
        double j = arrayToScalarOperation(3, DOT_PRODUCT, ey, arrayOperation(3, SUBTRACT, P3 , P1));

//		#from wikipedia
//		#plug and chug using above values
//		x = (pow(DistA,2) - pow(DistB,2) + pow(d,2))/(2*d)
        double x = (pow(dist1,2) - pow(dist2,2) + pow(d,2))/(2*d);

//		y = ((pow(DistA,2) - pow(DistC,2) + pow(i,2) + pow(j,2))/(2*j)) - ((i/j)*x)
        double y = ((pow(dist1,2) - pow(dist3,2) + pow(i,2) + pow(j,2))/(2*j)) - ((i/j)*x);

//		# only one case shown here
//		z = sqrt(pow(DistA,2) - pow(x,2) - pow(y,2))
        double z = pow(dist1,2) - pow(x,2) - pow(y,2);

        // Check if z is negative, it means the circles dont intercept.
        // We increase slightly until intersection occurs
        if(z<0){
            double dif = 0;

            //Tested and documented!

            dif = ab - dist2;
            if(dist1 > ab + dist2){
                decision1 -= step;
                decision2 += step;
            } //lower d1, elevate d2
            else if(dif < 0 && dist1 < abs(dif)){
                decision1 += step;
                decision2 -= step;
            }//elevate d1, lower d2
            else if(dif > 0 && dist1 < abs(dif)){
                decision1 += step;
                decision2 += step;
            } //elevate d1 and d2

            dif = bc - dist3;
            if(dist2 > bc + dist3){
                decision2 -= step;
                decision3 += step;
            } //lower d2, elevate d3
            else if(dif < 0 && dist2 < abs(dif)){
                decision2 += step;
                decision3 -= step;
            }//elevate d2, lower d3
            else if(dif > 0 && dist2 < abs(dif)){
                decision2 += step;
                decision3 += step;
            } //elevate d2 and d3

            dif = ca - dist3;
            if(dist3 > ca + dist1){
                decision3 -= step;
                decision1 += step;
            } //lower d3, elevate d1
            else if(dif < 0 && dist3 < abs(dif)){
                decision3 += step;
                decision1 -= step;
            }//elevate d3, lower d1
            else if(dif > 0 && dist3 < abs(dif)){
                decision3 += step;
                decision1 += step;
            } //elevate d3 and d1

            // If this happens, it means the circles intersect in points too distant
            // from each other, no iterative correction method can be applied. Bad measurements
            // Solution: return impossible coordinates, which flags for a repeat with new/other measurements
            // max values for lat is 90, long is 180, and accuracy -1
            if(decision1 == 0 && decision2 == 0 && decision3 == 0){
                if(closest < 25 + step*iteration){//Its 25m + a margin, because it may have been incremented
                    Location result = new Location("GeodesicCalculator");
                    result.setAccuracy(closest);
                    result.setLatitude(latitude);
                    result.setLongitude(longitude);
                    Bundle b = new Bundle();
                    b.putShort("iterations", (short)iteration);
                    result.setExtras(b);
                    return result;
                }
                //Bad measurement
                Location result = new Location("GeodesicCalculator");
                result.setAccuracy(-1);
                result.setLatitude(100);
                result.setLongitude(200);
                return result;
            }

            //May end up returning a location in the end where its 'closest' value
            //is bigger than the original simply because of these imcrementing iterations
            return getLocationByTrilateration(latitude1, longitude1, dist1 + decision1,
                    latitude2, longitude2, dist2 + decision2,
                    latitude3, longitude3, dist3 + decision3, ++iteration);

//            Location result = new Location("GeodesicCalculator");
//            result.setAccuracy(-1);
//            result.setLatitude(100);
//            result.setLongitude(200);
//            return result;
        }

        z = sqrt(z);


//		#triPt is an array with ECEF x,y,z of trilateration point
//		triPt = P1 + x*ex + y*ey + z*ez
        double[] xEx = scalarOnArrayOperation(3, MULTIPLY, x , ex);
        double[] yEy = scalarOnArrayOperation(3, MULTIPLY, y , ey);
        double[] zEz = scalarOnArrayOperation(3, MULTIPLY, z , ez);
        double[] triPt = arrayOperation(3, SUM, P1 , xEx);
        triPt = arrayOperation(3, SUM, triPt , yEy);
        triPt = arrayOperation(3, SUM, triPt , zEz);

//		#convert back to lat/long from ECEF
//		#convert to degrees
//		lat = math.degrees(math.asin(triPt[2] / earthR))
        latitude = toDegrees(asin(triPt[2]/earthRadius));
//		lon = math.degrees(math.atan2(triPt[1],triPt[0]))
        longitude = toDegrees(atan2(triPt[1], triPt[0]));

        //reverse conversion
//		lat = asin(z / R)
//		lon = atan2(y, x)
        //System.out.println("Lat: " + latitude + " longitude: " + longitude);
//		System.out.println("Iterations: "+iterations);

        if(wifiModule != null){ //print the APs new distances
            Location AP1 = new Location(Geofi.GEOFI);
            AP1.setAccuracy((float)dist1);
            AP1.setLatitude(latitude1);
            AP1.setLongitude(longitude1);
            Location AP2 = new Location(Geofi.GEOFI);
            AP2.setAccuracy((float)dist2);
            AP2.setLatitude(latitude2);
            AP2.setLongitude(longitude2);
            Location AP3 = new Location(Geofi.GEOFI);
            AP3.setAccuracy((float)dist3);
            AP3.setLatitude(latitude3);
            AP3.setLongitude(longitude3);

            wifiModule.notifyAPLocation(AP1);
            wifiModule.notifyAPLocation(AP2);
            wifiModule.notifyAPLocation(AP3);
        }
        Location result = new Location("GeodesicCalculator");
        result.setAccuracy(closest); //Error is distance to closest
        result.setLatitude(latitude);
        result.setLongitude(longitude);
        Bundle b = new Bundle();
        b.putShort("iterations", (short)iteration);
        result.setExtras(b);
        return result;
    }

    //Uses haversine formula to calculate the distance between 2 pairs of coordinates in degrees
    public static double getDistance(double latitude1, double longitude1,
                                     double latitude2, double longitude2){

        double dLat = toRadians(latitude2-latitude1);
        double dLon = toRadians(longitude2-longitude1);
        double lat1 = toRadians(latitude1);
        double lat2 = toRadians(latitude2);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                sin(dLon/2) * sin(dLon/2) * cos(lat1) * cos(lat2);
        double c = 2 * atan2(sqrt(a), sqrt(1-a));
        return earthRadius * c;
    }

    //Uses haversine formula to calculate the distance between 2 pairs of coordinates in degrees
    public static double getDistance(Location l1, Location l2){

        return getDistance(l1.getLatitude(), l1.getLongitude(), l2.getLatitude(), l2.getLongitude());
    }

    //One of the Great Circle navigation formulas source (aviation manual): http://williams.best.vwh.net/avform.htm
    //dist1 in meters, bearing in degrees
    public static Location getCoordsFromDistanceAndBearing(double distance, double bearing, double lat, double longt){

        if(bearing < 0) bearing += 360;

        double dR = distance/earthRadius;
        double brng = Math.toRadians(bearing);
        double lat1 = Math.toRadians(lat);
        double lon1 = Math.toRadians(longt);

        double lat2 = Math.asin( Math.sin(lat1)*Math.cos(dR) + Math.cos(lat1)*Math.sin(dR)*Math.cos(brng) );
        double a = Math.atan2(Math.sin(brng)*Math.sin(dR)*Math.cos(lat1), Math.cos(dR)-Math.sin(lat1)*Math.sin(lat2));
        //System.out.println("a = " +  a);
        double lon2 = lon1 + a;

        //convert impossible longitudes to possible (-189.1.. -> 170.9..)
        lon2 = (lon2+ 3*Math.PI) % (2*Math.PI) - Math.PI;

        //System.out.println("Latitude = "+Math.toDegrees(lat2)+"\nLongitude = "+Math.toDegrees(lon2));
        Location result = new Location("GeodesicCalculator");
        result.setAccuracy(0f);
        result.setLatitude(Math.toDegrees(lat2));
        result.setLongitude(Math.toDegrees(lon2));
        return result;
    }

    //South -180/180 West -90 North 0 East 90
    //Returns bearing in degrees
    public static double getBearingFromTo(double startLat, double startLng, double endLat, double endLng){
        double longitude1 = startLng;
        double longitude2 = endLng;
        double latitude1 = Math.toRadians(startLat);
        double latitude2 = Math.toRadians(endLat);
        double longDiff = Math.toRadians(longitude2-longitude1);
        double y = Math.sin(longDiff)*Math.cos(latitude2);
        double x = Math.cos(latitude1)*Math.sin(latitude2)-Math.sin(latitude1)*Math.cos(latitude2)*Math.cos(longDiff);

        return (Math.toDegrees(Math.atan2(y, x)));
    }

    // Outputs the smallest angle between the 3 locations input
    // Location 1 is the pivot for the calculations
    public static double getAngle(Location l1, Location l2, Location l3){

        double result;
        double bearing1 = getBearingFromTo(l1.getLatitude(), l1.getLongitude(), l2.getLatitude(), l2.getLongitude());
        double bearing2 = getBearingFromTo(l1.getLatitude(), l1.getLongitude(), l3.getLatitude(), l3.getLongitude());

        //System.out.println(""+bearing1+" "+bearing2);

        //If same signal, just abs, subtract and abs
        if((bearing1 > 0 && bearing2 > 0) || (bearing1 < 0 && bearing2 < 0)){
            bearing1 = Math.abs(bearing1);
            bearing2 = Math.abs(bearing2);
            result = Math.abs(bearing1 - bearing2);
        }else{
            bearing1 = Math.abs(bearing1);
            bearing2 = Math.abs(bearing2);
            result = bearing1 + bearing2;
            if(result > 180)
                result = 360 - result;

            //System.out.println("" + result + " " + bearing1 + " " + bearing2);
        }
        return result;
    }

    // Outputs an array of 3 non colinear locations
    // We define as 'collinear' if the angle measured between 3 points is <15 degrees
    // or > (180 - 15). Two different points must pass this test. Returns null if no possibility of collinear
    // Algorithm: fix the 2 strongest APs and vary the 3rd util non-collinear
    public static ArrayList<Location> getNonColinear(ArrayList<Location> locations){
        if(locations.size() < 3)
            return null;

        Location l1 = null;
        Location l2 = null;
        Location l3 = null;
        double angle;
        ArrayList<Location> result = new ArrayList<Location>();

        //Fills the 2 strongest locations, and iterated through the 3rd until all 3 are non colinear
        for(Location loc : locations){
            if(l1 == null)
                l1 = loc;
            else{
                if(l2 == null)
                    l2 = loc;
                else{
                    l3 = loc;
                    angle = getAngle(l1, l2, l3);
                    if(angle > 15 && angle < (180 - 15)){
                        //passed test 1, now onto second point of origin
                        angle = getAngle(l2, l1, l3);
                        if(angle > 15 && angle < (180 - 15)){
                            //passed test 2, now onto last point of origin
                            angle = getAngle(l3, l2, l1);
                            if(angle > 15 && angle < (180 - 15)){
                                //passed second test, good to go
                                result.add(l1);
                                result.add(l2);
                                result.add(loc);
                                return result;
                            }
                        }
                    }
                }
            }
        }
        //If it reaches this spot, no possibility of non collinear
        return null;
    }



    // Receives a list of AP locations and verifies if those locations are within
    // wifi range of each other
    public static boolean proximityChecks(ArrayList<Location> aps){
        double limit = 200; //wifi = 100m, worst case is APs directly across from each other
        int size = aps.size();
        Location[] apsArray = aps.toArray(new Location[size]);
        for(int i=0; i<size; i++){
            for(int j=i+1; j<size; j++){
                double distance = getDistance(
                        apsArray[i].getLatitude(),
                        apsArray[i].getLongitude(),
                        apsArray[j].getLatitude(),
                        apsArray[j].getLongitude());
                if(distance > limit)
                    return false;
            }
        }
        return true;
    }

//	public static Location travel(double distance, double bearing, double latitude, double longitude) {
//		double dR = distance / earthRadius;
//		double brng = Math.toRadians(bearing);
//		double lat1 = Math.toRadians(latitude);
//		double lon1 = Math.toRadians(longitude);
//
//		double a = Math.sin(dR) * Math.cos(lat1);
//		double lat2 = Math.asin(Math.sin(lat1) * Math.cos(dR) + a * Math.cos(brng));
//		double lon2 = lon1
//				+ Math.atan2(Math.sin(brng) * a, Math.cos(dR) - Math.sin(lat1) * Math.sin(lat2));
//		return new Location(0, Math.toDegrees(lat2), Math.toDegrees(lon2), 0f);
//	}




    // ==== HELPER METHODS ====
    private static double[] arrayNormalized(int sizeOfArray, double[] array){
        if(array.length < sizeOfArray){
            return new double[0];
        }
        double[] result = new double[sizeOfArray];
        double norm = getNorm(sizeOfArray, array);
        for(int i=0; i<sizeOfArray; i++){
            result[i] = array[i]/norm;
        }
        return result;
    }

    private static double getNorm(int sizeOfArray, double[] array){
        if(array.length < sizeOfArray){
            return 0;
        }
        double norm = 0;
        for(int i=0; i<sizeOfArray; i++){
            norm += Math.pow(array[i],2);
        }
        return Math.sqrt(norm);
    }

    private static double[] arrayOperation(int sizeOfArray, int operation, double[] array1 , double[] array2){
        if(array1.length < sizeOfArray || array2.length < sizeOfArray){
            return new double[0];
        }
        double[] result = new double[sizeOfArray];
        switch (operation){
            case SUM:
                for(int i=0; i<sizeOfArray; i++){
                    result[i] = array1[i] + array2[i];
                }
                return result;
            case SUBTRACT:
                for(int i=0; i<sizeOfArray; i++){
                    result[i] = array1[i] - array2[i];
                }
                return result;
            case MULTIPLY:
                for(int i=0; i<sizeOfArray; i++){
                    result[i] = array1[i] * array2[i];
                }
                return result;
            case DIVIDE:
                for(int i=0; i<sizeOfArray; i++){
                    result[i] = array1[i] / array2[i];
                }
                return result;
        }
        return new double[0];
    }

    private static double arrayToScalarOperation(int sizeOfArray, int operation, double[] array1 , double[] array2){
        if(array1.length < sizeOfArray || array2.length < sizeOfArray){
            return 0;
        }
        double result = 0;
        switch (operation){
            case DOT_PRODUCT:
                for(int i=0; i<sizeOfArray; i++){
                    result += array1[i] * array2[i];
                }
                return result;
        }
        return 0;
    }

    private static double[] scalarOnArrayOperation(int sizeOfArray, int operation, double scalar , double[] array){
        if(array.length < sizeOfArray){
            return new double[0];
        }
        double[] result = new double[sizeOfArray];
        switch (operation){
            case MULTIPLY:
                for(int i=0; i<sizeOfArray; i++){
                    result[i] = scalar * array[i];
                }
                return result;
        }
        return new double[0];
    }

    //Also known as external product, assumes 3 dimension
    private static double[] crossProduct(double[] array1 , double[] array2){
        if(array1.length < 3 || array2.length < 3){
            return new double[0];
        }
        double[] result = new double[3];

        result[0] = (array1[1]*array2[2]) - (array1[2]*array2[1]);
        result[1] = (array1[2]*array2[0]) - (array1[0]*array2[2]);
        result[2] = (array1[0]*array2[1]) - (array1[1]*array2[0]);

        return result;

    }

    private static double pow(double base, double exponent){
        return Math.pow(base, exponent);
    }
    private static double sqrt(double x){
        return Math.sqrt(x);
    }

    //receive degrees, convert to radians first
    private static double cos(double x){
        return Math.cos(x);
    }
    private static double sin(double x){
        return Math.sin(x);
    }
    private static double toRadians(double x){
        return Math.toRadians(x);
    }

    //receive in radians, convert to degrees before outputting
    private static double asin(double x){
        return Math.asin(x);
    }
    private static double atan2(double y, double x){
        return Math.atan2(y, x);
    }
    private static double toDegrees(double x){
        return Math.toDegrees(x);
    }


    private static double abs(double x){
        return Math.abs(x);
    }
}
