package org.trace.tracker.location;

// Created by Kurt on 09-04-2015.

// The Duty-Cycle service allows the use of a module periodically. The LBS application provides
// to the Collector Manager an application profile which species an energy consumption profile and
// the technologies with which location should be obtained. The energy profile received dictates in a
// Duty-Cycle service, the size of a cycle and the duty-cycled percentage of time. The Duty Cycle
// service also allows a programmer to specify in a given module whether or not there is a time-out
// renewal after a given event.
// The GPS Module duty-cycles GPS over a period of time with timeout renewal when a valid position
// is obtained via any other source (lowest priority). The Network Module is used whenever available
// and if no other technology is being used to collect information. The BT Module is used by
// opportunistic contact after obtaining an accurate position (e.g., from GPS).
public interface DutyCycleInterface{

    //Size Of Cycle in seconds, depends entirely on the technology... is the responsibility of each module
    //timeoutRenewal -> if operation is deferred after a new location is obtained is the responsability of the collector

    //dutyCycle -> percentage of time in operation
    public void requestLocationUpdates(double dutyCycle);

    public void removeLocationUpdates();

    //Is used by the Collector on each object that had timeoutRenewal
    //Only important for timeout renewal cases...in our case the GPS
    public void deferNextUpdate();

    public void requestInstantLocation();
}


//TODO Modules are given priorities by the programmer according to their perceived energy eficiency.