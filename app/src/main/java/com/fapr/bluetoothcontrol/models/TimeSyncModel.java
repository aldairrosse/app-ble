package com.fapr.bluetoothcontrol.models;

public class TimeSyncModel {
    private long sync;

    public TimeSyncModel() {
    }

    public TimeSyncModel(long sync) {
        this.sync = sync;
    }

    public long getSync() {
        return sync;
    }

    public void setSync(long sync) {
        this.sync = sync;
    }
}
