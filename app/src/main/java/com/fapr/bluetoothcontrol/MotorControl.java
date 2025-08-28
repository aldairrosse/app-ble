package com.fapr.bluetoothcontrol;

import static com.fapr.bluetoothcontrol.utils.Base32Util.SECRET_KEY;
import static com.fapr.bluetoothcontrol.utils.DataUtil.*;

import android.content.Context;
import android.content.Intent;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fapr.bluetoothcontrol.adapters.LogListAdapter;
import com.fapr.bluetoothcontrol.api.RequestInterface;
import com.fapr.bluetoothcontrol.api.RetrofitClient;
import com.fapr.bluetoothcontrol.databinding.ActivityMotorControlBinding;
import com.fapr.bluetoothcontrol.databinding.DialogCodeBinding;
import com.fapr.bluetoothcontrol.models.LogModel;
import com.fapr.bluetoothcontrol.models.TimeSyncModel;
import com.fapr.bluetoothcontrol.services.ClassicDeviceService;
import com.fapr.bluetoothcontrol.services.LockService;
import com.fapr.bluetoothcontrol.services.TokenService;
import com.fapr.bluetoothcontrol.utils.BluetoothUtil;
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

public class MotorControl extends AppCompatActivity implements ClassicDeviceService.ClassicDeviceEventListener {
    private ActivityMotorControlBinding binding;
    private boolean pairingState = false;
    private boolean parkingEnabled = false;
    private String closeCommand = "locktimer 0005\n";
    private String deviceAddress;
    private String deviceName;

    private final List<LogModel> logList = new ArrayList<>();
    private LogListAdapter logAdapter;

    private ClassicDeviceService deviceService;
    private TokenService tokenService;
    private RetrofitClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            String date = getStringTime(Calendar.getInstance().getTimeInMillis());
            binding.labelTime.setText(date);
            handler.postDelayed(this, SECONDS_CLOCK_UPDATE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMotorControlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Recycler
        binding.logList.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogListAdapter(logList);
        binding.logList.setAdapter(logAdapter);


        // Initializing
        deviceAddress = getIntent().getStringExtra("address");
        if(deviceAddress == null) deviceAddress = "00:11:22:33:44:55";

        deviceService = new ClassicDeviceService(this, deviceAddress, this);
        tokenService = new TokenService(getSecret());
        client = new RetrofitClient(BASE_URI);

        // Starting
        deviceName = getIntent().getStringExtra("name");
        if(deviceName == null) deviceName = "DEVICE";
        binding.deviceName.setText(deviceName);

        enableToggleButtons(false);
        enableDisconnectButton(false);
        enableSyncButton(false);
        enableParkingButton(false);

        updateState(true);

        handleConnect();
        handlesDeviceTime();
        showSavedSync();

        // Listeners
        binding.parkingToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> handleParkingSate(isChecked));

        binding.buttonClosed.setOnClickListener(view -> handleActionSate(false));
        binding.buttonOpened.setOnClickListener(view -> handleActionSate(true));

        binding.syncButton.setOnClickListener(view -> handleConnect());
        binding.closeButton.setOnClickListener(view -> handleDisconnect());
        binding.timeButton.setOnClickListener(view -> handleSyncTime());

        binding.settingsButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, SettingsSecond.class);
            startActivity(intent);
        });

        binding.backButton.setOnClickListener(view -> {
            clearDevice();
            handleDisconnect();
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
            binding.motorState.setText("Apagado");
        } else {
            binding.iconClosed.setVisibility(View.GONE);
            binding.iconOpened.setVisibility(View.VISIBLE);
            binding.motorState.setText("Encendido");
        }
    }

    private void enableParkingButton(boolean enabled) {
        binding.parkingButton.setEnabled(enabled);
        binding.parkingButtonLabel1.setEnabled(enabled);
        binding.parkingButtonLabel2.setEnabled(enabled);
        if(enabled) {
            binding.parkingButton.setAlpha(1f);
        } else {
            binding.parkingButton.setAlpha(0.28f);
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

    private void enableDisconnectButton(boolean enabled) {
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

    private void enableToggleButtons(boolean enabled) {
        int secondary = getResources().getColor(R.color.md_theme_secondary);
        int green = Color.parseColor("#10A010");
        int red = Color.parseColor("#BF2222");
        binding.buttonOpened.setEnabled(enabled);
        binding.buttonClosed.setEnabled(enabled);

        if(enabled) {
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

    private void saveParkingEnabled(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(DATA_PARKING_ENABLED, enabled);
        editor.apply();
    }

    private void readSettings() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        int option = prefs.getInt(DATA_DELAY_OPTION, 0);
        pairingState = prefs.getBoolean(DATA_PAIRING_ENABLED, false);
        parkingEnabled = prefs.getBoolean(DATA_PARKING_ENABLED, false);

        switch (option) {
            case 0:
                closeCommand = LockService.setLockTimer("0005");
                break;
            case 1:
                closeCommand = LockService.setLockTimer("0010");
                break;
            case 2:
                closeCommand = LockService.setLockTimer("0030");
                break;
            case 3:
                closeCommand = LockService.setLockTimer("0060");
                break;
            case 4:
                closeCommand = LockService.setLockTimer("3600");
                break;
            case 5:
                closeCommand = LockService.setLockTimer("7200");
                break;
        }
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

    private boolean tokenIsInvalid(String t) {
        return tokenService.isInvalid(t);
    }

    private void sendBluetoothTriggerState(boolean open) {
        String value = LockService.LOCK;
        if(open) value = LockService.UNLOCK;
        deviceService.sendData(value);
    }

    private void sendPairingCommand() {
        Log.v("DEVICE", "mstaddr ");
        if(pairingState) {
            Log.v("TO_SEND", "mstaddr ");
        }
    }

    private void handlesDeviceTime() {
        handler.post(runnable);
    }


    private void validateToken(String actionText, OnSuccessToken listener) {
        DialogCodeBinding view = DialogCodeBinding.inflate(getLayoutInflater());
        view.input.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(6)
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Autorización");
        builder.setView(view.getRoot());

        builder.setCancelable(false);

        builder.setPositiveButton(actionText, null);
        builder.setNeutralButton("Cancelar", (dialog, which) -> {
            dialog.dismiss();
            listener.cancel();
        });

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
            listener.onSuccess();
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

    private void handleParkingSate(boolean checked) {
        if(checked == parkingEnabled) return;
        validateToken(checked ? "Activar parking" : "Desactivar parking", new OnSuccessToken() {
            @Override
            public void onSuccess() {
                if(checked) {
                    deviceService.sendData(LockService.MODE_MANUAL);
                    showLogMessage("El dispositivo está en modo Parking");
                } else {
                    deviceService.sendData(LockService.MODE_AUTO);
                    showLogMessage("Se desactivó el modo Parking del dispositivo");
                }
                parkingEnabled = checked;
                saveParkingEnabled(checked);
            }
            @Override
            public void cancel() {
                if(checked) {
                    binding.parkingToggle.uncheck(R.id.parking_button);
                } else {
                    binding.parkingToggle.check(R.id.parking_button);
                }
            }
        });
    }

    private void handleActionSate(boolean checked) {
        validateToken(checked ? "Encender motor" : "Apagar motor", new OnSuccessToken() {
            @Override
            public void onSuccess() {
                sendBluetoothTriggerState(checked);
                updateState(!checked);
            }

            @Override
            public void cancel() { }
        });
    }

    private void handleConnect() {
        enableSyncButton(false);
        binding.deviceStatus.setText("Sincronizando...");
        showLogMessage("Se ha iniciado la conexión con el dispositivo");
        deviceService.connectDevice();
    }

    private void handleDisconnect() {
        enableDisconnectButton(false);
        enableParkingButton(false);
        if(!parkingEnabled) {
            deviceService.sendData(closeCommand);
        }
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
                    Toast.makeText(MotorControl.this, "Error al sincronizar con el servidor", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(MotorControl.this, "No se pudo sincronizar con el servidor", Toast.LENGTH_SHORT).show();
                Log.v("FAIL REQUEST", Objects.requireNonNull(t.getMessage()));
                enableTimeButton(true);
            }
        });
    }

    @Override
    public void onDataReceived(String data) {
        Log.v("READ", data);
        if(data.contains("Device Unlocked")) {
            updateState(false);
        } else if(data.contains("Device Locked")) {
            updateState(true);
        }
    }

    @Override
    public void onConnected() {
        showLogMessage("El dispositivo se ha conectado correctamente");
        binding.deviceStatus.setText("Dispositivo sincronizado");
        enableSyncButton(false);
        enableDisconnectButton(true);
        enableParkingButton(true);
        enableToggleButtons(true);

        // STARTING STATE
        handler.post(()-> {
            deviceService.sendData(LockService.ENABLE_SERIAL);
            deviceService.sendData(LockService.GET_STATE);
            deviceService.sendData("settings");
            deviceService.sendData("info");
            sendPairingCommand();
        });

        saveDevice();
        if(parkingEnabled) {
            binding.parkingToggle.check(R.id.parking_button);
        }
    }

    @Override
    public void onDisconnected() {
        showLogMessage("El dispositivo se ha desconectado");
        binding.deviceStatus.setText("Dispositivo desconectado");

        enableSyncButton(true);
        enableDisconnectButton(false);
        enableParkingButton(false);
        enableToggleButtons(false);
    }

    @Override
    public void onConnectionLosing() {
        // LOCK WITH TIMER VAR
        handleDisconnect();
    }

    @Override
    public void onError(int error) {
        Log.v("DEV ERROR", error + "");
        if(error == BluetoothUtil.ERROR_CONNECTION_FAILED || error == BluetoothUtil.ERROR_NOT_CONNECTED) {
            binding.deviceStatus.setText("Dispositivo desconectado");
            showLogMessage("No fue posible establecer conexión con el dispositivo");
            enableSyncButton(true);
            enableDisconnectButton(false);
            enableParkingButton(false);
            enableToggleButtons(false);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(runnable);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        readSettings();
        sendPairingCommand();
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

    private interface OnSuccessToken {
        void onSuccess();
        void cancel();
    }
}