package com.fapr.bluetoothcontrol;

import static com.fapr.bluetoothcontrol.utils.Base32Util.SECRET_KEY;
import static com.fapr.bluetoothcontrol.utils.DataUtil.*;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fapr.bluetoothcontrol.services.TokenService;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

public class SettingsSecond extends AppCompatActivity {
    private TextInputLayout authInput;
    private AutoCompleteTextView delayInput;
    private MaterialSwitch pairingSwitch;
    private boolean pairingState = false;
    private int delayOption = 0;
    private TokenService tokenService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_second);

        Button authButton = findViewById(R.id.auth_button);
        Button backButton = findViewById(R.id.back_button);
        authInput = findViewById(R.id.auth_input);
        delayInput = findViewById(R.id.delay_input);
        pairingSwitch = findViewById(R.id.pairing_switch);

        tokenService = new TokenService(SECRET_KEY);
        readSaveSettings();

        backButton.setOnClickListener((v)-> getOnBackPressedDispatcher().onBackPressed());
        pairingSwitch.setOnCheckedChangeListener((compoundButton, b) -> pairingState = b);
        authButton.setOnClickListener((v)->saveSettings());
        delayInput.setOnItemClickListener((adapterView, view, i, l) -> delayOption = i);
    }

    private void readSaveSettings() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        delayOption = prefs.getInt(DATA_DELAY_OPTION, 0);
        pairingState = prefs.getBoolean(DATA_PAIRING_ENABLED, false);
        if(pairingState) pairingSwitch.setChecked(true);
        String[] options = getResources().getStringArray(R.array.delayValues);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.simple_item, options);
        delayInput.setAdapter(adapter);
        if (delayOption >= 0 && delayOption < options.length) {
            delayInput.setText(options[delayOption], false);
        }
    }

    private void saveSettings() {
        String token = Objects.requireNonNull(authInput.getEditText()).getText().toString();
        if(tokenService.isInvalid(token)) {
            authInput.setError("El token de autenticación es inválido");
            return;
        }
        authInput.setError(null);
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(DATA_DELAY_OPTION, delayOption);
        editor.putBoolean(DATA_PAIRING_ENABLED, pairingState);
        editor.apply();
        authInput.getEditText().setText("");
        Toast.makeText(this, "Los ajustes han sido guardados", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        readSaveSettings();
    }
}