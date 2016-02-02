package org.trace.tracker;

// Created by Kurt on 22-04-2015.
public interface ModuleInterface {
    public void registerApp(Profile profile);
    public void unregisterApp(String cls);
    public boolean isSecuritySensitive();
    public boolean noAppsRegistered();
}
