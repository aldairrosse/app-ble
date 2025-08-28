package com.fapr.bluetoothcontrol.services;

import static com.fapr.bluetoothcontrol.utils.BluetoothUtil.*;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.fapr.bluetoothcontrol.utils.Base32Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

/**
 * MANAGE THE CONNECTION AND COMMUNICATION WITH CLASSIC DEVICE
 * <p>
 */
public class ClassicDeviceService {
    // PROPERTIES
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice bluetoothDevice;
    private OutputStream outputStream;
    private InputStream inputStream;
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final ClassicDeviceEventListener listener;
    private final String address;
    private final Handler handler;
    private Thread readThread;
    private boolean stopReading = false;

    // EVENTS
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

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                if(device == null) return;
                if (device.equals(bluetoothDevice)) {
                    switch (bondState) {
                        case BluetoothDevice.BOND_BONDED:
                            startConnection();
                            break;
                        case BluetoothDevice.BOND_NONE:
                            listener.onError(ERROR_CONNECTION_FAILED);
                            break;
                    }
                }
            }
        }
    };

    // CONSTRUCTOR
    public ClassicDeviceService(Context context, String address, ClassicDeviceService.ClassicDeviceEventListener listener) {
        this.context = context;
        this.address = address;
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
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        context.registerReceiver(receiver, filter);
    }

    private boolean notDeviceReady() {
        if (bluetoothDevice == null) {
            handler.post(()-> listener.onError(ERROR_NOT_DEVICE));
            return true;
        }

        if (bluetoothAdapter == null) {
            handler.post(()-> listener.onError(ERROR_NOT_AVAILABLE));
            return true;
        }

        if (!bluetoothAdapter.isEnabled()) {
            handler.post(()-> listener.onError(ERROR_NOT_CONNECTED));
            return true;
        }

        return false;
    }

    private void startConnection() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                handler.post(()-> listener.onError(ERROR_BLUETOOTH_PERMISSION));
                return;
            }
        }

        Log.v("REMOTE", "ADD:" + bluetoothDevice.getAddress()
                + "\nADD32:" + Base32Util.encodeToBase32(bluetoothDevice.getAddress())
                + "\nTYPE:" + bluetoothDevice.getType() // 1 FOR CLASSIC
                + "\nUUIDS:" + Arrays.toString(bluetoothDevice.getUuids()));

        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            startReading();
            handler.post(listener::onConnected);
        } catch (IOException e) {
            handler.post(()-> listener.onError(ERROR_CONNECTION_FAILED));
        }
    }

    private void startReading() {
        stopReading = false;
        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (!stopReading) {
                try {
                    if(inputStream == null) {
                        throw new IOException("Could not read");
                    }

                    if ((bytes = inputStream.read(buffer)) > 0) {
                        final String receivedData = new String(buffer, 0, bytes);
                        handler.post(()-> listener.onDataReceived(receivedData));
                    }
                } catch (IOException e) {
                    handler.post(()-> listener.onError(ERROR_READ_FAILED));
                    stopReading = true;
                    break;
                }
            }
        });
        readThread.start();
    }

    private void stopReading() {
        stopReading = true;
        if (readThread != null) {
            readThread.interrupt();
        }
    }

    // PUBLIC METHODS
    public void connectDevice() {
        new Thread(() -> {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            if (notDeviceReady()) return;

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    handler.post(()-> listener.onError(ERROR_BLUETOOTH_PERMISSION));
                    return;
                }
            }

            if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
            if(bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                startConnection();
            } else {
                bluetoothDevice.createBond();
            }
        }).start();
    }

    public void disconnectDevice() {
        try {
            stopReading();
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
            handler.post(listener::onDisconnected);
        } catch (IOException e) {
            handler.post(()-> listener.onError(ERROR_DISCONNECTION_FAILED));
        }
    }

    public void sendData(@NonNull String data) {
        try {
            if(bluetoothDevice == null || outputStream == null){
                throw new IOException("Cannot sent data");
            }
            outputStream.write(data.getBytes());
        } catch (IOException e) {
            handler.post(()-> listener.onError(ERROR_SEND_FAILED));
        }
    }

    public interface ClassicDeviceEventListener {
        void onDataReceived(String data);
        void onConnected();
        void onDisconnected();
        void onConnectionLosing();
        void onError(int error);
    }
}
