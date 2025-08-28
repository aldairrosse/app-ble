package com.fapr.bluetoothcontrol.services;

import static com.fapr.bluetoothcontrol.utils.BluetoothUtil.ERROR_BLUETOOTH_PERMISSION;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.UUID;

public class AcceptService extends Thread {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String APP_NAME = "Bluetooth_Control";
    private BluetoothServerSocket mmServerSocket;
    private final Context context;

    public AcceptService(Context context) {
        // Use a temporary object that is later assigned to mmServerSocket
        // because mmServerSocket is final.
        BluetoothServerSocket tmp = null;
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.context = context;

        try {
            // MY_UUID is the app's UUID string, also used by the client code.
            if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return;
                }
            }
            tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, SPP_UUID);
        } catch (IOException e) {
            Log.e("ACCEPT ERR", "Socket's listen() method failed", e);
        }
        mmServerSocket = tmp;
    }

    public void run() {
        BluetoothSocket socket = null;
        // Keep listening until exception occurs or a socket is returned.
        while (true) {
            try {
                socket = mmServerSocket.accept();
            } catch (IOException e) {
                Log.e("ACCEPT ERR", "Socket's accept() method failed", e);
                break;
            }

            if (socket != null) {
                // A connection was accepted. Perform work associated with
                // the connection in a separate thread.
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        return;
                    }
                }

                Log.v("ACCEPT SUCCES", socket.getRemoteDevice().getName());
                try {
                    mmServerSocket.close();
                } catch (IOException e) {
                    Log.e("ACCEPT ERR", "Could not to close");
                }
                break;
            }
        }
    }

    // Closes the connect socket and causes the thread to finish.
    public void cancel() {
        if(mmServerSocket == null) return;
        try {
            mmServerSocket.close();
        } catch (IOException e) {
            Log.e("ACCEPT ERR", "Could not close the connect socket", e);
        }
    }
}