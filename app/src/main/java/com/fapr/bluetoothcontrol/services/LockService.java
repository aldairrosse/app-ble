package com.fapr.bluetoothcontrol.services;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

public class LockService {
    public static final String LOCK = "lock\n";
    public static final String UNLOCK = "unlock\n";
    public static final String ENABLE_SERIAL = "serial 1\n";
    //public static final String DISABLE_SERIAL = "serial 0\n";
    public static final String MODE_AUTO = "mode auto\n";
    public static final String MODE_MANUAL = "mode manual\n";
    //public static final String GET_INFO = "info\n";
    //public static final String GET_SETTINGS = "settings\n";
    public static final String GET_STATE = "output" + "state\n";
    //public static final String FACTORY_RESET = "factory" + "settings\n";

    @NonNull
    @Contract(pure = true)
    public static String setLockTimer(String seconds) {
        return "lock" + "timer " + seconds + "\n";
    }
}
