package com.fapr.bluetoothcontrol.services;

import static com.fapr.bluetoothcontrol.utils.BluetoothUtil.*;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.UUID;

/**
 * MANAGE THE CONNECTION AND COMMUNICATION WITH LE DEVICE
 * <p>
 * By Frankil Aldair Pérez Rosales
 */
public class LEDeviceService {
    // PROPERTIES
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic characteristic;
    private final LEDeviceEventListener listener;
    private final String address;
    private final UUID serviceUUID;
    private final UUID characteristicUUID;
    private final Handler handler;

    // EVENTS
    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        return;
                    }
                }
                bluetoothGatt.discoverServices();
                handler.post(listener::onConnected);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post(listener::onDisconnected);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            BluetoothGattService service = gatt.getService(serviceUUID);
            if (service == null) {
                handler.post(()->listener.onError(ERROR_NOT_SERVICE));
                return;
            }

            characteristic = service.getCharacteristic(characteristicUUID);
            if (characteristic == null) {
                handler.post(()->listener.onError(ERROR_NOT_CHARACTERISTIC));
                return;
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    handler.post(()->listener.onError(ERROR_BLUETOOTH_PERMISSION));
                    return;
                }
            }

            bluetoothGatt.setCharacteristicNotification(characteristic, true);
            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                BluetoothGattDescriptor gattDescriptor = characteristic.getDescriptor(descriptor.getUuid());
                gattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                bluetoothGatt.writeDescriptor(descriptor);
            }

            handler.post(listener::onCommunicationReady);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            handler.post(() -> listener.onDataReceived(characteristic.getValue()));
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            String action = intent.getAction();
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            final int con_state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR);

            if (Intent.ACTION_SHUTDOWN.equals(action) ||
                    BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action) ||
                    BluetoothAdapter.STATE_TURNING_OFF == state ||
                    BluetoothAdapter.STATE_DISCONNECTING == state ||
                    BluetoothAdapter.STATE_TURNING_OFF == con_state ||
                    BluetoothAdapter.STATE_DISCONNECTING == con_state ||
                    BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                handler.post(listener::onConnectionLosing);
            }
        }
    };

    // CONSTRUCTOR
    public LEDeviceService(Context context, String address, UUID serviceUUID, UUID characteristicUUID, LEDeviceEventListener listener) {
        this.context = context;
        this.address = address;
        this.serviceUUID = serviceUUID;
        this.characteristicUUID = characteristicUUID;
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.handler = new Handler(Looper.getMainLooper());
        addReceiver();
    }

    // PRIVATE METHODS
    private void addReceiver(){
        IntentFilter filter = new IntentFilter();

        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_SHUTDOWN);

        context.registerReceiver(receiver, filter);
    }

    private boolean notDeviceReady() {
        if (bluetoothDevice == null) {
            listener.onError(ERROR_NOT_DEVICE);
            return true;
        }

        if (bluetoothAdapter == null) {
            listener.onError(ERROR_NOT_AVAILABLE);
            return true;
        }

        if (!bluetoothAdapter.isEnabled()) {
            listener.onError(ERROR_NOT_CONNECTED);
            return true;
        }

        return false;
    }

    // PUBLIC METHODS
    public void connectDevice() {
        bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
        if (notDeviceReady()) return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listener.onError(ERROR_BLUETOOTH_PERMISSION);
                return;
            }
        }

        if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
        bluetoothGatt = bluetoothDevice.connectGatt(context, false, callback);
    }

    public void disconnectDevice() {
        if (bluetoothGatt == null || bluetoothDevice == null) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listener.onError(ERROR_BLUETOOTH_PERMISSION);
                return;
            }
        }

        bluetoothGatt.disconnect();
        bluetoothDevice = null;
    }

    public void sendData(byte[] data) {
        if (characteristic == null) {
            listener.onError(ERROR_NOT_CHARACTERISTIC);
            return;
        }

        if(notDeviceReady()) return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listener.onError(ERROR_BLUETOOTH_PERMISSION);
                return;
            }
        }

        characteristic.setValue(data);
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    public interface LEDeviceEventListener {
        void onCommunicationReady();
        void onDataReceived(byte[] data);
        void onConnected();
        void onDisconnected();
        void onConnectionLosing();
        void onError(int error);
    }
}
