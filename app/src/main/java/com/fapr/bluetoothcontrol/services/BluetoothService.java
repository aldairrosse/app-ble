package com.fapr.bluetoothcontrol.services;

import static com.fapr.bluetoothcontrol.utils.BluetoothUtil.*;
import static com.fapr.bluetoothcontrol.utils.DataUtil.bytesToString;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

/**
 * MANAGE BLUETOOTH CONNECTIONS AND SEARCH FOR LE DEVICES
 * <p>
 * This service requires the user permissions for LOCATION and BLUETOOTH from device
 * The window for request permissions is launched using connect() and search() methods.
 * <p>
 * By Frankil Aldair Pérez Rosales
 */
public class BluetoothService {
    // PROPERTIES
    private final Context context;
    private final BluetoothEventListener listener;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothLeScanner;
    private final Handler handler;
    private boolean scanning;

    // EVENTS
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();
            ScanRecord scanRecord = result.getScanRecord();
            byte[] scanBytes = new byte[]{};
            if (scanRecord != null) scanBytes = scanRecord.getBytes();

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return;
                }
            }
            // Log.v("onScanLEDResult", device.getName() + " " + device.getAddress() + " \n" + bytesToString(scanBytes));
            listener.onFoundLEDevice(device.getName(), device.getAddress(), scanBytes);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            listener.onError(ERROR_ON_SCAN);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF: {
                        listener.onDisconnected();
                        break;
                    }

                    case BluetoothAdapter.STATE_ON: {
                        listener.onConnected();
                        break;
                    }

                    case BluetoothAdapter.ERROR: {
                        listener.onError(ERROR_ON_BLUETOOTH_STATE);
                        break;
                    }
                    default:
                        break;
                }
            }

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device != null) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            listener.onError(ERROR_BLUETOOTH_PERMISSION);
                            return;
                        }
                    }

                    listener.onFoundClassicDevice(device.getName(), device.getAddress());
                }
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                listener.onFinishSearch();
            }
        }
    };

    // CONSTRUCTOR
    public BluetoothService(Context context, BluetoothEventListener listener) {
        this.context = context;
        this.listener = listener;
        this.scanning = false;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        this.handler = new Handler(Looper.getMainLooper());
        isBluetoothReady();
        addReceiver();
    }

    // PRIVATE METHODS
    private void addReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(receiver, filter);
    }

    private boolean isBluetoothReady() {
        if (bluetoothAdapter == null) {
            listener.onError(ERROR_NOT_AVAILABLE);
            return false;
        }
        if (bluetoothLeScanner == null) {
            listener.onError(ERROR_NOT_LE_AVAILABLE);
            return false;
        }
        return true;
    }

    // PUBLIC METHODS
    public boolean isConnected() {
        if (!isBluetoothReady()) return false;
        return bluetoothAdapter.isEnabled();
    }

    public void searchLEDevices() {
        if (!isBluetoothReady())
            return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listener.onError(ERROR_SCAN_PERMISSION);
                return;
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onError(ERROR_LOCATION_PERMISSION);
            return;
        }

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && powerManager.isPowerSaveMode()) {
            listener.onError(ERROR_POWER_SAVE_MODE);
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = false;
        if (locationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLocationEnabled = locationManager.isLocationEnabled();
            } else {
                isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }
        }

        if (!isLocationEnabled) {
            listener.onError(ERROR_LOCATION_DISABLED);
            return;
        }

        if (!scanning) {
            handler.postDelayed(() -> {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        return;
                    }
                }

                scanning = false;
                bluetoothLeScanner.stopScan(scanCallback);
                listener.onFinishSearch();
            }, LE_SCAN_PERIOD);

            scanning = true;
            bluetoothLeScanner.startScan(scanCallback);
        }
    }

    public void searchClassicDevices() {
        if (!isBluetoothReady())
            return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listener.onError(ERROR_SCAN_PERMISSION);
                return;
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onError(ERROR_LOCATION_PERMISSION);
            return;
        }

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && powerManager.isPowerSaveMode()) {
            listener.onError(ERROR_POWER_SAVE_MODE);
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = false;
        if (locationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLocationEnabled = locationManager.isLocationEnabled();
            } else {
                isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }
        }

        if (!isLocationEnabled) {
            listener.onError(ERROR_LOCATION_DISABLED);
            return;
        }

        if(bluetoothAdapter.startDiscovery()) return;

        listener.onError(ERROR_ON_SCAN);
    }

    // INTERFACE IMPLEMENTS
    public interface BluetoothEventListener {
        void onConnected();
        void onDisconnected();
        void onError(int error);
        void onFoundLEDevice(String name, String address, byte[] data);
        void onFoundClassicDevice(String name, String address);
        void onFinishSearch();
    }
}