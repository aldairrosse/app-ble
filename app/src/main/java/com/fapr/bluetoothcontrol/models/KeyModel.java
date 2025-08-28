package com.fapr.bluetoothcontrol.models;

import java.util.List;

public class KeyModel {
    private String data;
    private List<String> devices;

    public KeyModel() {
    }

    public KeyModel(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public List<String> getDevices() {
        return devices;
    }

    public void setDevices(List<String> devices) {
        this.devices = devices;
    }
}
