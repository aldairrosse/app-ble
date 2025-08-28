package com.fapr.bluetoothcontrol.models;

public class DeviceModel {
    String name;
    String mac;

    public DeviceModel(String name, String mac) {
        this.name = name;
        this.mac = mac;
    }

    public String getMac() {
        return mac;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
