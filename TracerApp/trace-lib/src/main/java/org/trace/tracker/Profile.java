package org.trace.tracker;

import java.util.ArrayList;
import java.util.List;

/*Created by Kurt on 20-04-2015.
* v1.3 it flattens itself now
* v1.6 compares against modules of target (useful for collector)
* v1.7 getSecurityLevel()
* v1.8 added instantLocation
* v1.9 added energy consumption
* v2.0 added sync
*/
public class Profile {
    public enum SecurityLevel {WEAK, STRONG}
    public enum EnergyConsumption {LOW, MODERATE, HIGH}
    public enum Module {GPS, NETWORK, WIFIGEOFI, QR, NFC, BLE, DEAD_RECKONING, FUSED, ALL_AVAILABLE}
    public enum Synchronism {REALTIME, ASYNC}

    // Instant locations are not modules, because apps that have registered with the GPSModule can now
    // opportunistically receive updates from instant gps locations, because its the same module
    private boolean instantLocation;

    private static final String action = "org.trace.tracker.Profile";
    private List<Module> modules;
    private SecurityLevel security;
    private EnergyConsumption energy;
    private Synchronism sync;

    private String pkg;
    private String cls; //full path
    private String receiver; //full path

    public Profile(){
        modules = new ArrayList<Module>();
        security = SecurityLevel.WEAK;
        energy = EnergyConsumption.LOW;
        sync = Synchronism.ASYNC;
    }

    //cls should be written in its extensive form
    public Profile(String pkg, String cls){
        this.pkg = pkg;
        this.cls = cls;
        modules = new ArrayList<Module>();
        security = SecurityLevel.WEAK;
        energy = EnergyConsumption.LOW;
        sync = Synchronism.ASYNC;
    }

    public void addModule(Module module){ modules.add(module); }
    public void removeModule(Module module){
        modules.remove(module);
    }
    public List<Module> getModules(){
        return modules;
    }

    public void setReceiver(String receiver){
        this.receiver = receiver;
    }
    public String getReceiver(){
        return receiver;
    }

    public void setSynchronism(Synchronism synch){
        this.sync = synch;
    }
    public Synchronism getSynchronism(){
        return sync;
    }

    public void setSecurityLevel(SecurityLevel secLevel){
        security = secLevel;
    }
    public SecurityLevel getSecurityLevel(){
        return security;
    }

    public EnergyConsumption getEnergy() { return energy; }
    public void setEnergy(EnergyConsumption energy) { this.energy = energy; }

    public String getPkg() {
        return pkg;
    }
    public String getCls() {
        return cls;
    }

    public boolean isInstantLocation() {
        return instantLocation;
    }
    public void setInstantLocation(boolean instantLocation) {
        this.instantLocation = instantLocation;
    }

    public static String getAction(){
        return action;
    }

    //Returns null if there is none
    public Module getInstantModule(){
        for(Module m : modules){
            if(m.equals(Module.GPS) || m.equals(Module.NETWORK)
                    || m.equals(Module.WIFIGEOFI)){
                return m;
            }
        }
        return null;
    }

    public void clearModules(){
        modules = new ArrayList<Module>();
    }


    public String marshall(){
        String result = "";
        for(Module m : modules){
            result += m.toString()+"»";
        }
        result += "<"+ sync;
        result += "!"+energy.toString();
        result += "«"+security.toString();
        result += "-"+instantLocation;
        result += "#"+pkg;
        result += "="+cls;
        result += "~"+receiver;
        return result;
    }

    public static Profile unmarshall(String profileInString){
        String[] components = profileInString.split("~");
        String receiver = components[1];
        components = components[0].split("=");
        String cls = components[1];
        components = components[0].split("#");
        String pkg = components[1];
        components = components[0].split("-");
        boolean instant = Boolean.valueOf(components[1]);
        components = components[0].split("«");
        String security = components[1];
        components = components[0].split("!");
        String energy = components[1];
        components = components[0].split("<");
        String realtime = components[1];
        components = components[0].split("»");
        Profile result = new Profile(pkg, cls);
        result.setReceiver(receiver);
        result.setSecurityLevel(SecurityLevel.valueOf(security));
        result.setEnergy(EnergyConsumption.valueOf(energy));
        result.setInstantLocation(instant);
        result.setSynchronism(Synchronism.valueOf(realtime));
        for(String module : components){
            if(!module.isEmpty()){
                result.addModule(Module.valueOf(module));
            }
        }
        return result;
    }

    public String toString(){
        return marshall();
    }

    //Returns a list of modules that should be added to this object's list
    public List<Module> containsAllModules(Profile target){
        List<Module> toBeAdded = new ArrayList<Module>();
        List<Module> targetModules = target.getModules();
        for(Module module : targetModules){
            if(!modules.contains(module))
                toBeAdded.add(module);
        }
        return toBeAdded;
    }

    //To be used only by the collector
// public void addModule(Module module, SecurityLevel securityLevel){
//     if(!modules.contains(module)) //TEST this
//         modules.add(module);
//     else{
//         for(Module mod : modules){
//             if(mod.equals(module)){
//
//             }
//         }
//     }
// }

    //Collector
    public void setModules(List<Module> modules){
        this.modules = modules;
    }
}
