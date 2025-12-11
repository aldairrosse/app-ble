package com.fapr.bluetoothcontrol;

import static com.fapr.bluetoothcontrol.utils.Base32Util.SECRET_KEY;
import static com.fapr.bluetoothcontrol.utils.DataUtil.*;
import static com.fapr.bluetoothcontrol.utils.DataUtil.bytesToString;
import static com.fapr.bluetoothcontrol.utils.SensorUtil.*;

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
import com.fapr.bluetoothcontrol.services.ClassicDeviceService;
import com.fapr.bluetoothcontrol.services.DoorService;
import com.fapr.bluetoothcontrol.services.LockService;
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

public class UnifiedControl extends AppCompatActivity implements 
    LEDeviceService.LEDeviceEventListener, 
    ClassicDeviceService.ClassicDeviceEventListener {
    
    private ActivityDoorControlBinding binding;
    private String deviceAddress;
    private String deviceName;
    private int deviceType;
    private boolean isClassicDevice;
    private boolean parkingEnabled = false;
    private String closeCommand = "locktimer 0005\n";
    private final List<LogModel> logList = new ArrayList<>();
    private LogListAdapter logAdapter;

    private LEDeviceService leDeviceService;
    private ClassicDeviceService classicDeviceService;
    private DoorService doorService;
    private LockService lockService;
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
        binding = ActivityDoorControlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Get intent data
        deviceAddress = getIntent().getStringExtra("address");
        deviceName = getIntent().getStringExtra("name");
        deviceType = getIntent().getIntExtra("type", SENSOR_TYPE_DOOR);
        isClassicDevice = deviceName != null && deviceName.startsWith("ELOCK");

        if(deviceAddress == null) deviceAddress = "00:11:22:33:44:55";
        if(deviceName == null) deviceName = "DEVICE";

        // Setup recycler
        binding.logList.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogListAdapter(logList);
        binding.logList.setAdapter(logAdapter);

        // Initialize services
        initializeServices();
        
        // Setup UI
        binding.deviceName.setText(deviceName);
        setupDeviceIcon();
        enableToggleButtons(false);
        enableCloseButton(false);
        enableSyncButton(false);
        updateState(true);

        // Setup listeners
        setupListeners();
        
        // Read settings for Classic devices
        if (isClassicDevice) {
            readSettings();
        }

        // Start
        handleConnect();
        handlesDeviceTime();
        showSavedSync();
    }

    private void initializeServices() {
        if (isClassicDevice) {
            classicDeviceService = new ClassicDeviceService(this, deviceAddress, this);
            lockService = new LockService();
        } else {
            leDeviceService = new LEDeviceService(this, deviceAddress, SERVICE_UUID, CHARACTERISTIC_UUID, this);
            if (deviceType == SENSOR_TYPE_DOOR) {
                doorService = new DoorService(SENSOR_PASSWORD);
            } else {
                lockService = new LockService();
            }
        }
        
        tokenService = new TokenService(getSecret());
        client = new RetrofitClient(BASE_URI);
    }

    private void setupListeners() {
        binding.syncButton.setOnClickListener(view -> handleConnect());
        binding.closeButton.setOnClickListener(view -> handleDisconnect());
        binding.timeButton.setOnClickListener(view -> handleSyncTime());
        
        // Setup parking button for Classic LOCK devices
        if (isClassicDevice && deviceType == SENSOR_TYPE_LOCK) {
            binding.parkingToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> handleParkingState(isChecked));
        }
        
        if (deviceType == SENSOR_TYPE_LOCK && !isClassicDevice) {
            // LE LOCK: Solo un botón "Desactivar antirobo"
            binding.buttonOpened.setVisibility(View.GONE);
            binding.buttonClosed.setText("Desactivar antirobo");
            binding.buttonClosed.setOnClickListener(view -> handleLELockAction());
            binding.doorState.setVisibility(View.GONE);
        } else {
            // DOOR o Classic LOCK: Dos botones
            binding.buttonClosed.setOnClickListener(view -> handleActionSate(false));
            binding.buttonOpened.setOnClickListener(view -> handleActionSate(true));
            
            // Configurar textos según tipo
            if (deviceType == SENSOR_TYPE_DOOR) {
                binding.buttonOpened.setText("Abrir puerta");
                binding.buttonClosed.setText("Cerrar puerta");
            } else {
                binding.buttonOpened.setText("Encender motor");
                binding.buttonClosed.setText("Apagar motor");
            }
        }

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
            if (deviceType == SENSOR_TYPE_DOOR) {
                binding.doorState.setText("Cerrada");
            } else {
                binding.doorState.setText("Apagado");
            }
        } else {
            binding.iconClosed.setVisibility(View.GONE);
            binding.iconOpened.setVisibility(View.VISIBLE);
            if (deviceType == SENSOR_TYPE_DOOR) {
                binding.doorState.setText("Abierta");
            } else {
                binding.doorState.setText("Encendido");
            }
        }
    }

    private void enableToggleButtons(boolean enable) {
        int secondary = getResources().getColor(R.color.md_theme_secondary);
        int green = Color.parseColor("#10A010");
        int red = Color.parseColor("#BF2222");
        
        if (deviceType == SENSOR_TYPE_LOCK && !isClassicDevice) {
            // LE LOCK: Solo botón cerrado
            binding.buttonClosed.setEnabled(enable);
            binding.buttonClosed.setBackgroundColor(enable ? red : secondary);
            binding.buttonClosed.setAlpha(enable ? 1f : 0.28f);
        } else {
            // DOOR o Classic LOCK: Ambos botones
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
    }

    private void enableTimeButton(boolean enabled) {
        binding.timeButton.setEnabled(enabled);
        binding.timeButtonLabel1.setEnabled(enabled);
        binding.timeButtonLabel2.setEnabled(enabled);
        binding.timeButton.setAlpha(enabled ? 1f : 0.28f);
    }

    private void enableCloseButton(boolean enabled) {
        binding.closeButton.setEnabled(enabled);
        binding.closeLabel.setEnabled(enabled);
        binding.closeButton.setAlpha(enabled ? 1f : 0.28f);
    }

    private void enableSyncButton(boolean enabled) {
        binding.syncButton.setEnabled(enabled);
        binding.syncLabel.setEnabled(enabled);
        binding.syncButton.setAlpha(enabled ? 1f : 0.28f);
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

    private void setupDeviceIcon() {
        View iconView = binding.deviceType.getChildAt(0);
        if (deviceType == SENSOR_TYPE_DOOR) {
            iconView.setBackgroundResource(R.drawable.outline_sensor_door_24);
        } else {
            iconView.setBackgroundResource(R.drawable.baseline_key_24);
        }
    }

    private void handleLELockAction() {
        DialogCodeBinding view = DialogCodeBinding.inflate(getLayoutInflater());
        view.input.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(6)
        });
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Autorización");
        builder.setView(view.getRoot());
        builder.setPositiveButton("Desactivar antirobo", null);
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
            
            leDeviceService.sendData(new byte[]{0x01});
            enableToggleButtons(false);
            startCountdown();
            
            handler.postDelayed(() -> {
                leDeviceService.sendData(new byte[]{0x00});
                enableToggleButtons(true);
            }, 5000);
            
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

    private void startCountdown() {
        binding.doorState.setVisibility(View.VISIBLE);
        
        Handler countdownHandler = new Handler(Looper.getMainLooper());
        for (int i = 5; i >= 0; i--) {
            final int seconds = i;
            countdownHandler.postDelayed(() -> {
                if (seconds > 0) {
                    binding.doorState.setText("Desactivando... " + seconds + "s");
                } else {
                    binding.doorState.setVisibility(View.GONE);
                }
            }, (5 - i) * 1000);
        }
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
        if (isClassicDevice) {
            String value = open ? LockService.UNLOCK : LockService.LOCK;
            classicDeviceService.sendData(value);
        } else {
            if (deviceType == SENSOR_TYPE_DOOR) {
                byte[] value = open ? new byte[]{0x01} : new byte[]{0x00};
                leDeviceService.sendData(doorService.getSensorCommand(0x6c, value));
            } else {
                String value = open ? LockService.UNLOCK : LockService.LOCK;
                leDeviceService.sendData(value.getBytes());
            }
        }
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

        String actionText = deviceType == SENSOR_TYPE_DOOR ? 
            (checked ? "Abrir puerta" : "Cerrar puerta") :
            (checked ? "Encender motor" : "Apagar motor");

        builder.setPositiveButton(actionText, null);
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
        
        if (isClassicDevice) {
            classicDeviceService.connectDevice();
        } else {
            leDeviceService.connectDevice();
        }
    }

    private void handleDisconnect() {
        enableCloseButton(false);
        if (isClassicDevice) {
            if (!parkingEnabled) {
                classicDeviceService.sendData(closeCommand);
            }
            classicDeviceService.disconnectDevice();
        } else {
            leDeviceService.disconnectDevice();
        }
    }

    private void handleSyncTime() {
        enableTimeButton(false);

        RequestInterface request = client.getRetrofitClient().create(RequestInterface.class);
        request.getSyncTime().enqueue(new Callback<TimeSyncModel>() {
            @Override
            public void onResponse(Call<TimeSyncModel> call, Response<TimeSyncModel> response) {
                enableTimeButton(true);
                if(!response.isSuccessful()) {
                    Toast.makeText(UnifiedControl.this, "Error al sincronizar con el servidor", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(UnifiedControl.this, "No se pudo sincronizar con el servidor", Toast.LENGTH_SHORT).show();
                Log.v("FAIL REQUEST", Objects.requireNonNull(t.getMessage()));
                enableTimeButton(true);
            }
        });
    }

    // LE Device Events
    @Override
    public void onCommunicationReady() {
        showLogMessage("El dispositivo está listo para recibir instrucciones");
        enableToggleButtons(true);

        if (deviceType == SENSOR_TYPE_DOOR) {
            handler.post(()-> leDeviceService.sendData(doorService.getSensorCommand(0x6d, null)));
        }
    }

    @Override
    public void onDataReceived(@NonNull byte[] data) {
        Log.v("onDataReceived", bytesToString(data).trim());

        if (deviceType == SENSOR_TYPE_DOOR && data.length > 2 && data[1] == 0x6d) {
            boolean trigger = doorService.getTriggerStatus(data);
            updateState(!trigger);
        }
    }

    // Classic Device Events
    @Override
    public void onDataReceived(String data) {
        Log.v("read", data);
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
        enableCloseButton(true);
        enableToggleButtons(true);
        
        if (isClassicDevice && deviceType == SENSOR_TYPE_LOCK) {
            enableParkingButton(true);
        }
        
        saveDevice();

        if (isClassicDevice) {
            handler.post(()-> {
                classicDeviceService.sendData(LockService.ENABLE_SERIAL);
                classicDeviceService.sendData(LockService.GET_STATE);
                classicDeviceService.sendData("settings");
                classicDeviceService.sendData("info");
            });
            
            if (parkingEnabled) {
                binding.parkingToggle.check(R.id.parking_button);
            }
        }
    }

    @Override
    public void onDisconnected() {
        showLogMessage("El dispositivo se ha desconectado");
        binding.deviceStatus.setText("Desconectado del dispositivo");
        enableSyncButton(true);
        enableCloseButton(false);
        enableToggleButtons(false);
        
        if (isClassicDevice && deviceType == SENSOR_TYPE_LOCK) {
            enableParkingButton(false);
        }
    }

    @Override
    public void onConnectionLosing() {
        if (isClassicDevice) {
            handleDisconnect();
        }
    }

    @Override
    public void onError(int error) {
        Log.v("DEV ERROR", error + "");
    }

    private void readSettings() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        int option = prefs.getInt(DATA_DELAY_OPTION, 0);
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

    private void saveParkingEnabled(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(DATA_PARKING_ENABLED, enabled);
        editor.apply();
    }

    private void enableParkingButton(boolean enabled) {
        binding.parkingButton.setEnabled(enabled);
        binding.parkingButtonLabel1.setEnabled(enabled);
        binding.parkingButtonLabel2.setEnabled(enabled);
        binding.parkingButton.setAlpha(enabled ? 1f : 0.28f);
    }

    private void handleParkingState(boolean checked) {
        if(checked == parkingEnabled) return;
        
        validateToken(checked ? "Activar parking" : "Desactivar parking", new OnSuccessToken() {
            @Override
            public void onSuccess() {
                if(checked) {
                    classicDeviceService.sendData(LockService.MODE_MANUAL);
                    showLogMessage("El dispositivo está en modo Parking");
                } else {
                    classicDeviceService.sendData(LockService.MODE_AUTO);
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

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(runnable);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isClassicDevice) {
            readSettings();
        }
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