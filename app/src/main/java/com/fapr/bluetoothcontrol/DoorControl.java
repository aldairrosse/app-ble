package com.fapr.bluetoothcontrol;


import static com.fapr.bluetoothcontrol.utils.Base32Util.SECRET_KEY;
import static com.fapr.bluetoothcontrol.utils.DataUtil.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fapr.bluetoothcontrol.adapters.LogListAdapter;
import com.fapr.bluetoothcontrol.api.RequestInterface;
import com.fapr.bluetoothcontrol.api.RetrofitClient;
import com.fapr.bluetoothcontrol.databinding.ActivityDoorControlBinding;
import com.fapr.bluetoothcontrol.databinding.DialogCodeBinding;
import com.fapr.bluetoothcontrol.models.LogModel;
import com.fapr.bluetoothcontrol.models.TimeSyncModel;
import com.fapr.bluetoothcontrol.services.LEDeviceService;
import com.fapr.bluetoothcontrol.services.DoorService;
import com.fapr.bluetoothcontrol.services.TokenService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DoorControl extends AppCompatActivity implements LEDeviceService.LEDeviceEventListener {
    private ActivityDoorControlBinding binding;
    private String deviceAddress;
    private String deviceName;
    private final List<LogModel> logList = new ArrayList<>();
    private LogListAdapter logAdapter;

    private LEDeviceService deviceService;
    private DoorService doorService;
    private TokenService tokenService;
    private RetrofitClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            // Log.v("TOKEN", date + ":" + tokenService.generateTOTP());
            String date = getStringTime(Calendar.getInstance().getTimeInMillis());
            binding.labelTime.setText(date);
            handler.postDelayed(this, SECONDS_CLOCK_UPDATE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDoorControlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Recycler
        binding.logList.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogListAdapter(logList);
        binding.logList.setAdapter(logAdapter);

        // Initializing
        deviceAddress = getIntent().getStringExtra("address");
        if(deviceAddress == null) deviceAddress = "00:11:22:33:44:55";

        deviceService = new LEDeviceService(this, deviceAddress, SERVICE_UUID, CHARACTERISTIC_UUID, this);
        doorService = new DoorService(SENSOR_PASSWORD);
        tokenService = new TokenService(getSecret());
        client = new RetrofitClient(BASE_URI);

        // Starting
        deviceName = getIntent().getStringExtra("name");
        if(deviceName == null) deviceName = "DEVICE";
        binding.deviceName.setText(deviceName);

        enableToggleButtons(false);
        enableCloseButton(false);
        enableSyncButton(false);

        updateState(true);

        handleConnect();
        handlesDeviceTime();
        showSavedSync();

        // Listeners
        binding.syncButton.setOnClickListener(view -> handleConnect());
        binding.closeButton.setOnClickListener(view -> handleDisconnect());
        binding.timeButton.setOnClickListener(view -> handleSyncTime());
        binding.buttonClosed.setOnClickListener(view -> handleActionSate(false));
        binding.buttonOpened.setOnClickListener(view -> handleActionSate(true));

        binding.backButton.setOnClickListener(view -> {
            handleDisconnect();
            clearDevice();
            getOnBackPressedDispatcher().onBackPressed();
        });
    }

    private String getSecret() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        return prefs.getString(CLIENT_TOKEN, SECRET_KEY);
    }

    private void updateState(boolean isClosed) {
        if (isClosed) {
            binding.iconClosed.setVisibility(View.VISIBLE);
            binding.iconOpened.setVisibility(View.GONE);
            binding.doorState.setText("Cerrada");
        } else {
            binding.iconClosed.setVisibility(View.GONE);
            binding.iconOpened.setVisibility(View.VISIBLE);
            binding.doorState.setText("Abierta");
        }
    }

    private void enableToggleButtons(boolean enable) {
        int secondary = getResources().getColor(R.color.md_theme_secondary);
        int green = Color.parseColor("#10A010");
        int red = Color.parseColor("#BF2222");
        binding.buttonOpened.setEnabled(enable);
        binding.buttonClosed.setEnabled(enable);

        if(enable) {
            binding.buttonOpened.setBackgroundColor(green);
            binding.buttonClosed.setBackgroundColor(red);
            binding.buttonOpened.setAlpha(1f);
            binding.buttonClosed.setAlpha(1f);
        } else {
            binding.buttonClosed.setBackgroundColor(secondary);
            binding.buttonOpened.setBackgroundColor(secondary);
            binding.buttonOpened.setAlpha(0.28f);
            binding.buttonClosed.setAlpha(0.28f);
        }
    }

    private void enableTimeButton(boolean enabled) {
        binding.timeButton.setEnabled(enabled);
        binding.timeButtonLabel1.setEnabled(enabled);
        binding.timeButtonLabel2.setEnabled(enabled);
        if(enabled) {
            binding.timeButton.setAlpha(1f);
        } else {
            binding.timeButton.setAlpha(0.28f);
        }
    }

    private void enableCloseButton(boolean enabled) {
        binding.closeButton.setEnabled(enabled);
        binding.closeLabel.setEnabled(enabled);
        if(enabled) {
            binding.closeButton.setAlpha(1f);
        } else {
            binding.closeButton.setAlpha(0.28f);
        }
    }

    private void enableSyncButton(boolean enabled) {
        binding.syncButton.setEnabled(enabled);
        binding.syncLabel.setEnabled(enabled);
        if(enabled) {
            binding.syncButton.setAlpha(1f);
        } else {
            binding.syncButton.setAlpha(0.28f);
        }
    }

    private void timeStatus(boolean status) {
        if(status) {
            binding.timeSuccess.setVisibility(View.VISIBLE);
            binding.timeError.setVisibility(View.GONE);
        } else {
            binding.timeSuccess.setVisibility(View.GONE);
            binding.timeError.setVisibility(View.VISIBLE);
        }
    }

    private void saveSyncTime(long lastSyncTime) {
        SharedPreferences prefs = getSharedPreferences(TIME_SYNC_DATA, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(LAST_SYNC_TIME, lastSyncTime);
        editor.apply();
    }

    private void showSavedSync() {
        SharedPreferences prefs = getSharedPreferences(TIME_SYNC_DATA, MODE_PRIVATE);
        long lastSyncTime = prefs.getLong(LAST_SYNC_TIME, 0);
        if(lastSyncTime == 0) {
            timeStatus(false);
            handleSyncTime();
            return;
        }
        timeStatus(true);
    }

    private void showLogMessage(String message) {
        String date = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        logList.add(0, new LogModel(date, message));
        logAdapter.notifyItemInserted(0);

        if(logList.size() > 15) {
            logList.remove(15);
            logAdapter.notifyItemRemoved(15);
        }
    }

    public String getStringTime(long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(time));
    }

    private boolean tokenIsInvalid(String token) {
        return tokenService.isInvalid(token);
    }

    private void sendBluetoothTriggerState(boolean open) {
        byte[] value = new byte[] { 0x00 };
        if(open) value = new byte[] { 0x01 };
        deviceService.sendData(doorService.getSensorCommand(0x6c, value));
    }

    private void handlesDeviceTime() {
        handler.post(runnable);
    }

    private void handleActionSate(boolean checked) {
        DialogCodeBinding view = DialogCodeBinding.inflate(getLayoutInflater());
        view.input.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(6)
        });
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Autorización");
        builder.setView(view.getRoot());

        builder.setPositiveButton(checked ? "Abrir puerta" : "Cerrar puerta", null);
        builder.setNeutralButton("Cancelar", (dialog, which) -> dialog.dismiss());
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v-> {
            if(view.input.getText() == null) return;
            String token = view.input.getText().toString();
            if(token.isEmpty()) {
                view.inputLayout.setError("Código de autorización requerido");
                return;
            }
            if(tokenIsInvalid(token)) {
                view.inputLayout.setError("El código ingresado es inválido");
                return;
            }
            sendBluetoothTriggerState(checked);
            updateState(!checked);
            dialog.dismiss();
        });

        view.input.requestFocus();
        view.input.postDelayed(()-> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view.input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 250);
    }

    private void handleConnect() {
        enableSyncButton(false);
        binding.deviceStatus.setText("Sincronizando...");
        showLogMessage("Se ha iniciado la conexión con el dispositivo");
        deviceService.connectDevice();
    }

    private void handleDisconnect() {
        enableCloseButton(false);
        deviceService.disconnectDevice();
    }

    private void handleSyncTime() {
        enableTimeButton(false);

        RequestInterface request = client.getRetrofitClient().create(RequestInterface.class);
        request.getSyncTime().enqueue(new Callback<TimeSyncModel>() {
            @Override
            public void onResponse(Call<TimeSyncModel> call, Response<TimeSyncModel> response) {
                enableTimeButton(true);
                if(!response.isSuccessful()) {
                    Toast.makeText(DoorControl.this, "Error al sincronizar con el servidor", Toast.LENGTH_SHORT).show();
                    return;
                }

                long serverSyncTime = response.body().getSync();
                long deviceSyncTime = Calendar.getInstance().getTimeInMillis();
                long offsetTime = Math.abs(serverSyncTime - deviceSyncTime);

                if (offsetTime > SECONDS_SYNC_MARGIN) {
                    timeStatus(false);
                    return;
                }

                timeStatus(true);
                saveSyncTime(deviceSyncTime);
                Log.d("TIME_SYNC","Time offset: " + offsetTime + " ms\n" +
                                "Server time: " + serverSyncTime + "\n" +
                                "Device time: " + deviceSyncTime);
            }

            @Override
            public void onFailure(Call<TimeSyncModel> call, Throwable t) {
                Toast.makeText(DoorControl.this, "No se pudo sincronizar con el servidor", Toast.LENGTH_SHORT).show();
                Log.v("FAIL REQUEST", Objects.requireNonNull(t.getMessage()));
                enableTimeButton(true);
            }
        });
    }

    @Override
    public void onCommunicationReady() {
        showLogMessage("El dispositivo está listo para recibir instrucciones");
        enableToggleButtons(true);

        // STARTING STATE
        handler.post(()-> deviceService.sendData(doorService.getSensorCommand(0x6d, null)));
    }

    @Override
    public void onDataReceived(@NonNull byte[] data) {
        Log.v("onDataReceived", bytesToString(data).trim());

        if(data.length > 2 && data[1] == 0x6d) {
            boolean trigger = doorService.getTriggerStatus(data);
            updateState(!trigger);
        }
    }

    @Override
    public void onConnected() {
        showLogMessage("El dispositivo se ha conectado correctamente");
        binding.deviceStatus.setText("Dispositivo sincronizado");
        enableSyncButton(false);
        enableCloseButton(true);
        saveDevice();
    }

    @Override
    public void onDisconnected() {
        showLogMessage("El dispositivo se ha desconectado");
        binding.deviceStatus.setText("Desconectado del dispositivo");
        enableSyncButton(true);
        enableCloseButton(false);
        enableToggleButtons(false);
    }

    @Override
    public void onConnectionLosing() {
        // CHANGE ACTION ON LOSING : COULD SEND TRIGGER OFF
    }

    @Override
    public void onError(int error) {
        Log.v("DEV ERROR", error + "");
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(runnable);
        super.onDestroy();
    }

    private void saveDevice() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(DATA_DEVICE_ADDRESS, deviceAddress);
        editor.putString(DATA_DEVICE_NAME, deviceName);
        editor.apply();
    }

    private void clearDevice() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(DATA_DEVICE_ADDRESS);
        editor.remove(DATA_DEVICE_NAME);
        editor.apply();
    }
}