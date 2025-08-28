package com.fapr.bluetoothcontrol.models;

public class LogModel {
    String label;
    String message;

    public LogModel(String label, String message) {
        this.label = label;
        this.message = message;
    }

    public String getLabel() {
        return label;
    }

    public String getMessage() {
        return message;
    }
}
