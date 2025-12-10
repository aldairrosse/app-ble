package com.fapr.bluetoothcontrol;

import static com.fapr.bluetoothcontrol.utils.Base32Util.SECRET_KEY;
import static com.fapr.bluetoothcontrol.utils.DataUtil.CLIENT_DEVICES;
import static com.fapr.bluetoothcontrol.utils.DataUtil.CLIENT_TOKEN;
import static com.fapr.bluetoothcontrol.utils.DataUtil.DATA_DEVICE_ADDRESS;
import static com.fapr.bluetoothcontrol.utils.DataUtil.DATA_DEVICE_NAME;
import static com.fapr.bluetoothcontrol.utils.DataUtil.SETTINGS_DATA;
import static com.fapr.bluetoothcontrol.utils.DataUtil.decrypt;
import static com.fapr.bluetoothcontrol.utils.DataUtil.startsWithHeader;
import static com.fapr.bluetoothcontrol.utils.SensorUtil.*;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fapr.bluetoothcontrol.adapters.DeviceListAdapter;
import com.fapr.bluetoothcontrol.databinding.DialogAddBinding;
import com.fapr.bluetoothcontrol.models.DeviceModel;
import com.fapr.bluetoothcontrol.services.BluetoothService;
import com.fapr.bluetoothcontrol.ui.login.LoginActivity;
import com.fapr.bluetoothcontrol.utils.BluetoothUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class MainControl extends AppCompatActivity implements BluetoothService.BluetoothEventListener, TabLayout.OnTabSelectedListener {
    private String deviceAddress = "";
    private Button buttonSearch;
    private TextView label;
    private TextView labelDevices;
    private RecyclerView devicesList;
    private ProgressBar progressBar;
    private MaterialSwitch materialSwitch;
    private BluetoothAdapter bluetoothAdapter;
    private final List<DeviceModel> doorFound = new ArrayList<>();
    private final List<DeviceModel> lockFound = new ArrayList<>();
    private List<String> devices;
    private String clientId;
    private BluetoothService service;
    private int type = SENSOR_TYPE_DOOR;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_control);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        materialSwitch = findViewById(R.id.bluetooth_switch);
        label = findViewById(R.id.bluetooth_label);
        labelDevices = findViewById(R.id.devices_label);
        buttonSearch = findViewById(R.id.bluetooth_search);
        progressBar = findViewById(R.id.progress);
        tabLayout = findViewById(R.id.option_tabs);
        MaterialButton logoutButton = findViewById(R.id.logout_button);
        MaterialButton addButton = findViewById(R.id.add_button);

        devicesList = findViewById(R.id.devices_list);
        devicesList.setLayoutManager(new LinearLayoutManager(this));
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        service = new BluetoothService(this, this);

        startControls();
        readDevices();

        logoutButton.setOnClickListener(view -> logout());
        addButton.setOnClickListener(view -> addDevice());
        materialSwitch.setOnClickListener(view -> handleBluetoothState());
        buttonSearch.setOnClickListener(view -> handleSearchDevices());
        tabLayout.addOnTabSelectedListener(this);

        if(service.isConnected()) {
            tryConnection();
        }
    }

    private void addDevice() {
        DialogAddBinding view = DialogAddBinding.inflate(getLayoutInflater());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Agregar dispositivo");
        builder.setView(view.getRoot());

        builder.setCancelable(false);

        builder.setPositiveButton("Agregar", null);
        builder.setNeutralButton("Cancelar", (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v-> {
            if(view.input.getText() == null) return;
            String token = view.input.getText().toString();
            if(token.isEmpty()) {
                view.inputLayout.setError("Debe ingresar una llave");
                return;
            }

            try {
                String decoded = decrypt(token, SECRET_KEY, clientId);
                saveOtherDevice(decoded);

                Toast.makeText(this, "Se ha agregado el dispositivo", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (Exception e) {
                Log.v("ERROR", "Llave inválida", e);
                view.inputLayout.setError("La llave es inválida");
            }
        });

        view.input.requestFocus();
        view.input.postDelayed(()-> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view.input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 250);
    }

    private void logout() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Cerrar sesión");
        builder.setMessage("Deberá conectarse a internet e ingresar el código del cliente para ingresar de nuevo. ¿Desea cerrar sesión?");
        builder.setPositiveButton("Cerrar sesión", (dialogInterface, i) -> {
            SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(DATA_DEVICE_ADDRESS);
            editor.remove(DATA_DEVICE_NAME);
            editor.remove(CLIENT_TOKEN);
            editor.apply();

            Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
            startActivity(intent);
            finish();
        });
        builder.setNeutralButton("Cancelar", (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });
        builder.show();
    }

    private void tryConnection() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        String address = prefs.getString(DATA_DEVICE_ADDRESS, "");
        String name = prefs.getString(DATA_DEVICE_NAME, "");

        if(address.isEmpty()) return;

        if(name.startsWith("ELOCK")) {
            handleItemClick(new DeviceModel(name, address), MotorControl.class);
        } else if(!name.isEmpty()) {
            handleItemClick(new DeviceModel(name, address), DoorControl.class);
        }
    }

    private void saveOtherDevice(String address) {
        devices.add(address);

        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(CLIENT_DEVICES, new HashSet<>(devices));
        editor.apply();
    }

    private void readDevices() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        devices = new ArrayList<>(prefs.getStringSet(CLIENT_DEVICES, new HashSet<>()));
        clientId = prefs.getString(CLIENT_TOKEN, "");
    }

    private void startControls() {
        if (!service.isConnected()) {
            buttonSearch.setEnabled(false);
            materialSwitch.setChecked(false);
            label.setText("El movil tiene la conexión Bluetooth apagada");
            return;
        }

        materialSwitch.setChecked(true);
        label.setText("El movil tiene la conexión Bluetooth encendida");
    }

    private void hideSearchViews() {
        if(type == SENSOR_TYPE_DOOR) buttonSearch.setText("Buscar chapa");
        if(type == SENSOR_TYPE_LOCK) buttonSearch.setText("Buscar anti-asalto");

        materialSwitch.setEnabled(true);
        buttonSearch.setEnabled(true);
        progressBar.setVisibility(View.GONE);
    }

    private void showListStatus(@NonNull List<DeviceModel> list) {
        if (list.isEmpty()) labelDevices.setText("No se encontraron dispositivos");
        else labelDevices.setText("Seleccione un dispositivo para conectarse");
    }

    private void handleBluetoothState() {
        if (bluetoothAdapter == null) return;

        buttonSearch.setEnabled(false);
        if (service.isConnected()) {
            label.setText("Desconectando...");
            enableOrDisable(false);
        } else {
            label.setText("Conectando...");
            enableOrDisable(true);
        }
    }

    private void enableOrDisable(boolean state) {
        Log.v("Version:", Build.VERSION.SDK_INT + "");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_CONNECT
                }, 0);
                label.setText("Se requieren permisos para manejar la conexión");
                return;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, 0);
            }
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            label.setText("El dispositivo no soporta Bluetooth");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (state) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivity(enableBtIntent);
            } else {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intent);
            }
            return;
        }

        if (state) {
            bluetoothAdapter.enable();
        } else {
            bluetoothAdapter.disable();
        }
    }

    private void handleSearchDevices() {
        progressBar.setVisibility(View.VISIBLE);
        buttonSearch.setEnabled(false);
        materialSwitch.setEnabled(false);
        buttonSearch.setText("Buscando...");

        if(type == SENSOR_TYPE_DOOR) {
            doorFound.clear();
            devicesList.setAdapter(new DeviceListAdapter(doorFound, item -> {}));
            labelDevices.setText("Buscando dispositivos de chapa...");
            service.searchLEDevices();
        }
        else if(type == SENSOR_TYPE_LOCK) {
            lockFound.clear();
            devicesList.setAdapter(new DeviceListAdapter(lockFound, item -> {}));
            labelDevices.setText("Buscando dispositivos de anti-asalto...");
            service.searchLEDevices();
            new Handler(Looper.getMainLooper()).postDelayed(() -> service.searchClassicDevices(), 3000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startControls();
    }

    @Override
    public void onConnected() {
        materialSwitch.setChecked(true);
        buttonSearch.setEnabled(true);
        label.setText("El movil ha activado la conexión Bluetooth");
    }

    @Override
    public void onDisconnected() {
        materialSwitch.setChecked(false);
        buttonSearch.setEnabled(false);
        label.setText("El movil ha desactivado la conexión Bluetooth");
    }

    @Override
    public void onError(int error) {
        hideSearchViews();
        labelDevices.setText("No se encontraron dispositivos");
        if(error == BluetoothUtil.ERROR_LOCATION_PERMISSION) {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            }, 0);
            labelDevices.setText("Se requieren permisos de ubicación para escanear dispositivos");
        }

        if(error == BluetoothUtil.ERROR_BLUETOOTH_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_CONNECT}, 0);
                label.setText("Se requieren permisos de Bluetooth para manejar la conexión");
            }
        }

        if(error == BluetoothUtil.ERROR_SCAN_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                }, 0);
                labelDevices.setText("Se requieren permiso de escaneo bluetooth");
            }
        }

        if(error == BluetoothUtil.ERROR_POWER_SAVE_MODE) {
            showAlert("Por favor, desactiva el modo de ahorro de energía.");
        }

        if(error == BluetoothUtil.ERROR_LOCATION_DISABLED) {
            showAlert("Se requiere activar la ubicación para escanear dispositivos cercanos");
        }

        if(error == BluetoothUtil.ERROR_ON_SCAN) {
            labelDevices.setText("No se encontraron dispositivos cercanos");
        }

        if(error == BluetoothUtil.ERROR_NOT_AVAILABLE) {
            showAlert("El dispositivo no soporta Bluetooth");
        }

        if(error == BluetoothUtil.ERROR_NOT_LE_AVAILABLE) {
            showAlert("El dispositivo no soporta Bluetooth LE");
        }

        Log.v("BLUETOOTH ERROR", "" + error);
    }

    @Override
    public void onFoundLEDevice(String name, String address, byte[] data) {
        if(address == null || name == null) return;
        if(!startsWithHeader(data)) return;
        if(type == SENSOR_TYPE_DOOR)
            handleFoundDevice(name, address, DoorControl.class, doorFound);
        if(type == SENSOR_TYPE_LOCK)
            handleFoundDevice(name, address, MotorControl.class, lockFound);
    }

    @SuppressLint("HardwareIds")
    @Override
    public void onFoundClassicDevice(String name, String address) {
        deviceAddress = bluetoothAdapter.getAddress();
        //deviceAddress = getBluetoothMac();
        if(address == null || name == null) return;
        if(!name.startsWith("ELOCK")) return;
        handleFoundDevice(name, address, MotorControl.class, lockFound);
    }

    private void handleFoundDevice(String name, String address, Class<?> activity, List<DeviceModel> list) {
        if (name == null) return;
        for (DeviceModel item : list) {
            if (item.getName().equals(name)) return;
        }
        list.add(new DeviceModel(name, address));
        devicesList.setAdapter(new DeviceListAdapter(list, item -> handleItemClick(item, activity)));
    }

    private void handleItemClick(DeviceModel item, Class<?> activity) {
        if (!service.isConnected()) {
            showAlert("Se requiere de conexión Bluetooth para conectar con: " + item.getName());
            return;
        }

        String mac = normalizeMac(item.getMac());
        String prefix = "ANT-" + mac;
        boolean autorizado = false;

        if (type == SENSOR_TYPE_DOOR) {
            if (devices.contains(mac)) {
                autorizado = true;
            }
        } else if (type == SENSOR_TYPE_LOCK) {
            if (devices.contains(prefix)) {
                autorizado = true;
            }
        }

        if (!autorizado) {
            Log.v("Validation", "MAC=" + item.getMac() + " Normalizada=" + mac + " Lista=" + devices);
            showAlert("El dispositivo no está registrado con el cliente. Verifique con el administrador o pide una llave.");
            return;
        }

        Intent intent = new Intent(getApplicationContext(), activity);
        intent.putExtra("type", type);
        intent.putExtra("name", item.getName());
        intent.putExtra("address", item.getMac());
        intent.putExtra("device_address", deviceAddress);
        startActivity(intent);
    }

    private void showAlert(String message) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Error");
        builder.setMessage(message);
        builder.setPositiveButton("Aceptar", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }

    @Override
    public void onFinishSearch() {
        hideSearchViews();
        if(type == SENSOR_TYPE_DOOR) {
            showListStatus(doorFound);
            tabLayout.selectTab(tabLayout.getTabAt(0), true);
        }

        if(type == SENSOR_TYPE_LOCK) {
            showListStatus(lockFound);
            tabLayout.selectTab(tabLayout.getTabAt(1), true);
        }
    }

    @Override
    public void onTabSelected(@NonNull TabLayout.Tab tab) {
        if(bluetoothAdapter != null && bluetoothAdapter.isEnabled() && !buttonSearch.isEnabled()) {
            if(type == SENSOR_TYPE_DOOR) tabLayout.selectTab(tabLayout.getTabAt(0), true);
            if(type == SENSOR_TYPE_LOCK) tabLayout.selectTab(tabLayout.getTabAt(1), true);
        }

        int pos = tabLayout.getSelectedTabPosition();
        if(pos == 0) {
            type = SENSOR_TYPE_DOOR;
            showListStatus(doorFound);
            devicesList.setAdapter(new DeviceListAdapter(doorFound, item -> handleItemClick(item, DoorControl.class)));
            buttonSearch.setText("Buscar chapa");
        }

        if(pos == 1) {
            type = SENSOR_TYPE_LOCK;
            showListStatus(lockFound);
            devicesList.setAdapter(new DeviceListAdapter(lockFound, item -> handleItemClick(item, MotorControl.class)));
            buttonSearch.setText("Buscar anti-asalto");
        }
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {

    }

    @NonNull
    private String normalizeMac(@NonNull String mac) {
        return mac.trim().replace(":", "").toUpperCase();
    }


    @Nullable
    public static String getBluetoothMac() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            Log.v("SIZE", interfaces.size() + "");
            for (NetworkInterface networkInterface : interfaces) {
                String interfaceName = networkInterface.getName().toLowerCase(); // Normalizar a minúsculas
                boolean v = interfaceName.contains("bluetooth") || interfaceName.contains("bt") ||
                        interfaceName.contains("bnep") || interfaceName.contains("hci") || interfaceName.equals("wlan0");
                Log.v("BLUETOOTH", "Interface: " + interfaceName + ", Valid: " + v);
                if (v) {

                    byte[] macBytes = networkInterface.getHardwareAddress();
                    Log.v("BLUETOOTH", "MAC Bytes: " + Arrays.toString(macBytes));
                    if (macBytes == null) {
                        return null;
                    }
                    StringBuilder macAddress = new StringBuilder();
                    for (byte b : macBytes) {
                        macAddress.append(String.format("%02X:", b));
                    }
                    if (macAddress.length() > 0) {
                        macAddress.deleteCharAt(macAddress.length() - 1);
                    }
                    return macAddress.toString();
                }
            }
        } catch (Exception e) {
            Log.e("BLUETOOTH", "Error al obtener la dirección MAC", e);
        }
        return null;
    }
}