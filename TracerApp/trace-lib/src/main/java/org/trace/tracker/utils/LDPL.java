package org.trace.tracker.utils;

/**
 * @author  Kurt
 */
public class LDPL {

    //Dont use this, as LDPL is independent of frequencies!!
    public static double calculateFSPLDistance(double signalLevelInDbm, double freqInMHz) {
        double exp = (27.55 - (20 * Math.log10(freqInMHz)) + Math.abs(signalLevelInDbm)) / 20.0;
        return Math.pow(10.0, exp);
    }

    public static double getDistance(double signalLevelInDbm, double propagationExponent, double N, double powerTransmit){
        double exp = (1/(10*propagationExponent))*(-Math.abs(powerTransmit) - Math.abs(N) + Math.abs(signalLevelInDbm));
        return Math.pow(10.0, exp);
    }

    //Value for the pathloss exponent in here: http://www.cse.unt.edu/~rakl/Tum05.pdf
    public static double controlledLDPL(double signalLevelInDbm){
        double rawDistance = getDistance(signalLevelInDbm, 1.8, 0, -33);
        //if greater than 20m (25 for pathloss exp of 1.8 starts at -58 instead of -59)
        if(rawDistance > 25){
            //return 0.0388*rawDistance + 19.22; // 50m aos -91dBm  (20-50)
            //return 0.1034*rawDistance + 17.93; // 100m aos -91dBm (20-100)
            return (0.0152)*rawDistance + (24.62); // 50m aos -91dBm para pathloss exp de 1.8 em vez de 2 (25-50)
        }
        return rawDistance;
    }

}
