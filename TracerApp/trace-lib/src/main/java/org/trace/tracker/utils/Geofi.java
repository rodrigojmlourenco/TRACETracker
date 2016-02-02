package org.trace.tracker.utils;

import android.location.Location;

import java.util.HashMap;

/**
 * @author Kurt
 * This class encapsulates:
 * - the map for AP BSSID -> Geofi code
 * - the encode/decode Geofi functionality
*/
public class Geofi {
    public static final String GEOFI = "geofi";

    private static final String cc1 = "BpcwQrlqZ";
    private static final String cc2 = "BpcxArlqJ";
    private static final String cc3 = "Bpcw2rlqk";
    private static final String cc4 = "Bpd0Mrlqo";
    private static final String cc5 = "Bpd2Hrlqc";
    private static final String cc6 = "Bpd2Irlpg";
    private static final String cc7 = "Bpd1Jrlph";
    private static final String cc8 = "Bpd0Irlpp";
    private static final String cc9 = "Bpd0Prlqn";

    private static final String cc10 ="Bpctqrls8"; //hard to map
    private static final String cc11 ="Bpcv2rlsf"; //hard to map
    private static final String cc12 ="BpcvErlr1";

    private static final String cc21 ="Bpd2Prlqc";
    private static final String cc22 ="Bpd1Qrlqd";
    private static final String cc23 ="Bpd0Wrlql";
    private static final String cc24 ="BpcxIrlr8";
    private static final String cc25 ="BpcwLrlrY";
    private static final String cc26 ="Bpcvbrls4";
    private static final String cc27 = cc11; //hard to map //same coords, 2nd floor
    private static final String cc28 ="BpcuZrlrO";
    private static final String cc29 ="BpcwRrlqN";
    private static final String cc210 = cc7; //same coords, 2nd floor
    private static final String cc211 = cc6;


    private static HashMap<String, String> geofiMap = new HashMap<String, String>();
    static{
        //base floor + 1st floor
        geofiMap.put("6c:99:89:0f:45:4", cc1);
        geofiMap.put("6c:99:89:9c:f0:4", cc1);
        geofiMap.put("6c:99:89:98:f8:a", cc1);

        geofiMap.put("1c:e6:c7:1d:66:b", cc2);
        geofiMap.put("1c:e6:c7:1f:66:d", cc2);
        geofiMap.put("6c:99:89:af:1b:a", cc2);
        geofiMap.put("6c:99:89:b1:1b:2", cc2);

        geofiMap.put("0c:68:03:4f:1c:8", cc3);
        geofiMap.put("0c:68:03:4d:24:2", cc3);

        geofiMap.put("6c:99:89:a1:12:3", cc4);
        geofiMap.put("6c:99:89:a3:11:d", cc4);

        geofiMap.put("6c:99:89:b1:12:3", cc5);
        geofiMap.put("6c:99:89:af:12:b", cc5);

        geofiMap.put("6c:99:89:b1:13:3", cc6);
        geofiMap.put("6c:99:89:af:13:b", cc6);

        geofiMap.put("6c:99:89:a1:00:9", cc7);
        geofiMap.put("6c:99:89:a3:00:3", cc7);

        geofiMap.put("6c:99:89:a1:00:7", cc8);
        geofiMap.put("6c:99:89:a3:00:1", cc8);

        geofiMap.put("6c:99:89:af:26:5", cc9);
        geofiMap.put("6c:99:89:b1:25:d", cc9);
        geofiMap.put("08:94:f6:52:10.c", cc9);


        //2nd floor
        geofiMap.put("5c:a4:8a:69:f3:0", cc21);
        geofiMap.put("5c:a4:8a:69:f3:5", cc21);

        geofiMap.put("5c:a4:8a:95:4c:f", cc22);

        geofiMap.put("5c:a4:8a:67:eb:2", cc23);
        geofiMap.put("5c:a4:8a:69:ea:d", cc23);

        geofiMap.put("5c:a4:8a:93:a4:f", cc24);
        geofiMap.put("5c:a4:8a:95:a4:b", cc24);

        geofiMap.put("1c:1d:86:2c:2f:4", cc25);
        geofiMap.put("1c:1d:86:2a:2f:d", cc25);

        geofiMap.put("24:01:c7:6d:ae:a", cc26);
        geofiMap.put("24:01:c7:91:27:5", cc26);

        geofiMap.put("1c:1d:86:2c:2c:9", cc28);
        geofiMap.put("1c:1d:86:2a:2d:1", cc28);

        geofiMap.put("5c:a4:8a:dc:6c:3", cc29);
        geofiMap.put("5c:a4:8a:e4:64:1", cc29);

        geofiMap.put("5c:a4:8a:95:53:f", cc210);
        geofiMap.put("5c:a4:8a:93:54:3", cc210);

        geofiMap.put("5c:a4:8a:69:ed:b", cc211);
        geofiMap.put("5c:a4:8a:67:ee:0", cc211);


        //base floor, south portion of the building
        geofiMap.put("6c:99:89:a7:63:d", cc10);
        geofiMap.put("6c:99:89:a9:63.6", cc10);

        geofiMap.put("6c:99:89:a9:63:f", cc11);
        geofiMap.put("6c:99:89:a7:64.6", cc11);

        geofiMap.put("6c:99:89:af:13:e", cc12);
        geofiMap.put("6c:99:89:b1:13.6", cc12);

    }

    static String[] symbols =
            {"0","1","2","3","4","5","6","7","8","9"
                    ,"A","B","C","D","E","F","G","H","I","J"
                    ,"K","L","M","N","O","P","Q","R","S","T"
                    ,"U","V","W","X","Y","Z","a","b","c","d"
                    ,"e","f","g","h","i","j","k","l","m","n"
                    ,"o","p","q","r","s","t","u","v","w","x"};

    static HashMap<String, Integer> symbolValues = new HashMap<String, Integer>();
    static {
        symbolValues.put("0",0);
        symbolValues.put("1",1);
        symbolValues.put("2",2);
        symbolValues.put("3",3);
        symbolValues.put("4",4);
        symbolValues.put("5",5);
        symbolValues.put("6",6);
        symbolValues.put("7",7);
        symbolValues.put("8",8);
        symbolValues.put("9",9);

        symbolValues.put("A",10);
        symbolValues.put("B",11);
        symbolValues.put("C",12);
        symbolValues.put("D",13);
        symbolValues.put("E",14);
        symbolValues.put("F",15);
        symbolValues.put("G",16);
        symbolValues.put("H",17);
        symbolValues.put("I",18);
        symbolValues.put("J",19);

        symbolValues.put("K",20);
        symbolValues.put("L",21);
        symbolValues.put("M",22);
        symbolValues.put("N",23);
        symbolValues.put("O",24);
        symbolValues.put("P",25);
        symbolValues.put("Q",26);
        symbolValues.put("R",27);
        symbolValues.put("S",28);
        symbolValues.put("T",29);

        symbolValues.put("U",30);
        symbolValues.put("V",31);
        symbolValues.put("W",32);
        symbolValues.put("X",33);
        symbolValues.put("Y",34);
        symbolValues.put("Z",35);
        symbolValues.put("a",36);
        symbolValues.put("b",37);
        symbolValues.put("c",38);
        symbolValues.put("d",39);

        symbolValues.put("e",40);
        symbolValues.put("f",41);
        symbolValues.put("g",42);
        symbolValues.put("h",43);
        symbolValues.put("i",44);
        symbolValues.put("j",45);
        symbolValues.put("k",46);
        symbolValues.put("l",47);
        symbolValues.put("m",48);
        symbolValues.put("n",49);

        symbolValues.put("o",50);
        symbolValues.put("p",51);
        symbolValues.put("q",52);
        symbolValues.put("r",53);
        symbolValues.put("s",54);
        symbolValues.put("t",55);
        symbolValues.put("u",56);
        symbolValues.put("v",57);
        symbolValues.put("w",58);
        symbolValues.put("x",59);
//        code = (HashMap)Collections.unmodifiableMap(code);
    }

    public static String getCodeFromBSSID(String BSSID){
        return geofiMap.get(BSSID);
    }


//			1.  Scale the latitude and longitude to 0<=l<360
//			2.  Represent the latitude and longitude as 5-character strings by multiplying by 144000,
//			rounding to the nearest integer, and converting
//			the result to Base-60 using the "digits" 0-9, A-Z
//			and a-w. To reduce ambiguity if the code is being
//			typed by hand, replace the letter "O" (uppercase
//			o) with the letter "y", and replace the letter "l"
//			(lowercase l) with "z". Otherwise, the replaced
//			characters might easily be mistaken for the digits
//			0 and 1.
//			3.  Compute the first character of the tag by taking
//			the high-order digit of the latitude, multiplying by
//			4, and adding the high-order digit of the longitude.
//			4.  The second through fifth characters of the tag are
//			the four low-order digits of the latitude.
//			5.  The sixth through ninth characters of the tag are
//			the four low-order digits of the longitude.

    public static String encode(double latitude, double longitude){
        // Scale the latitude and longitude to 0<=l<360
        latitude += 90;
        latitude *= 2;
        longitude += 180;
        // Represent the latitude and longitude as 5-character strings by:
        // multiplying by 144000
        latitude *= 144000;
        longitude *= 144000;
        //rounding to the nearest integer
        long lattd = Math.round(latitude); //loss of info
        long longtd = Math.round(longitude);
//    	System.out.format("Latitude %f longitude %f lattd %d longtd %d\n", latitude, longitude, lattd, longtd);
        // and converting the result to Base-60 using the "digits" 0-9, A-Z, a-w.
        String lati, lati1, longi, longi1;
        if(lattd != 0){
            lati = toBase60(lattd);
            lati1 = lati.substring(0, 1);
            lati = lati.substring(1);
        }else{
            lati = "0000";
            lati1 = "0";
        }

        if(longtd != 0){
            longi = toBase60(longtd);
            longi1 = longi.substring(0, 1);
            longi = longi.substring(1);
        }else{
            longi = "0000";
            longi1 = "0";
        }
        Integer composite = Integer.parseInt(lati1)*5 + Integer.parseInt(longi1);
        // Compute the first character of the tag by taking
        // the high-order digit of the latitude, multiplying by
        // 4, and adding the high-order digit of the longitude.
        return toBase60(composite)+lati+longi;
    }

    //Correction, multiplying by 4 is an abuse, because the case extreme 180, will
    //cause the encoded number to be 40000, hence divisible by 4. Multiplying by 5
    //is still safe as 4*5 is 20 + 4 still less than 59...
    public static Location decode(String code){

        String lati = code.substring(1,5);
        String longi = code.substring(5,9);

        Integer composite = (int)fromBase60(code.substring(0,1));
        Integer longi1 = composite%5;
        Integer lati1 = composite/5;

        double latitude = fromBase60(lati1 + lati);
        double longitude = fromBase60(longi1 + longi);

        latitude /= 144000;
        longitude /= 144000;

        latitude /= 2;
        latitude -= 90;
        longitude -= 180;

        Location loc = new Location(GEOFI); //simply serves as vessel to pass lat and long
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);

        return loc;
    }

    //HELPER METHODS
    public static String toBase60(long number){
        String converted = "";
        long remainder;
        long left = number;

        while(left > 0){
            remainder = left%60;
            left /= 60;
            converted = symbols[(int)remainder] + converted;
//		    System.out.println(""+converted);
        }

        if(converted.isEmpty())
            return "0";
        return converted;
    }

    public static long fromBase60(String code){
        String remainder = code, symbol;
        long total = 0;
        int iteration = 0;

        while(remainder.length() > 0){
            symbol = remainder.substring(remainder.length()-1, remainder.length());
            remainder = remainder.substring(0, remainder.length() - 1);
            total += symbolValues.get(symbol)*Math.pow(60, iteration++);
        }

        return total;
    }
}
