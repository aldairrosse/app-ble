package com.fapr.bluetoothcontrol.ui.login;

import static com.fapr.bluetoothcontrol.utils.DataUtil.BASE_URI;
import static com.fapr.bluetoothcontrol.utils.DataUtil.CLIENT_DEVICES;
import static com.fapr.bluetoothcontrol.utils.DataUtil.CLIENT_TOKEN;
import static com.fapr.bluetoothcontrol.utils.DataUtil.SETTINGS_DATA;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fapr.bluetoothcontrol.MainControl;
import com.fapr.bluetoothcontrol.R;
import com.fapr.bluetoothcontrol.api.RequestInterface;
import com.fapr.bluetoothcontrol.api.RetrofitClient;
import com.fapr.bluetoothcontrol.databinding.ActivityLoginBinding;
import com.fapr.bluetoothcontrol.models.KeyModel;
import com.fapr.bluetoothcontrol.models.LoginModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private RetrofitClient client;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        client = new RetrofitClient(BASE_URI);

        binding.buttonLogin.setOnClickListener(view -> login());

        readToken();
    }

    private void login() {
        if(binding.codeInput.getEditText() == null) return;

        if(binding.codeInput.getEditText().getText().toString().isEmpty()) {
            showLoginFailed("Ingresa un código");
            return;
        }
        String code = binding.codeInput.getEditText().getText().toString().trim();
        RequestInterface request = client.getRetrofitClient().create(RequestInterface.class);
        enableUi(false);
        request.getLoginKey(new LoginModel(code)).enqueue(new Callback<KeyModel>() {
            @Override
            public void onResponse(Call<KeyModel> call, Response<KeyModel> response) {
                enableUi(true);
                Log.v("Response", response.message() + " " + response.code() + " " + response.toString());
                if(!response.isSuccessful()) {
                    showLoginFailed("El código es incorrecto");
                    return;
                }

                KeyModel key = response.body();
                if(key == null || key.getData() == null) {
                    showAlert("Respuesta incorrecta del servidor");
                    return;
                }

                saveToken(key.getData(), key.getDevices());
                init();
            }

            @Override
            public void onFailure(Call<KeyModel> call, Throwable t) {
                enableUi(true);
                showAlert("No es posible conectar con el servidor, intenta más tarde.");
            }
        });
    }

    private void saveToken(String token, List<String> devices) {
        Log.v("TokenSaved", token + ":" + devices);
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(CLIENT_TOKEN, token);
        editor.putStringSet(CLIENT_DEVICES, new HashSet<>(devices));
        editor.apply();
    }

    private void enableUi(boolean enable) {
        binding.codeInput.setEnabled(enable);
        binding.buttonLogin.setEnabled(enable);

        if(!enable) {
            binding.buttonLogin.setText("Iniciando sesión...");
            binding.codeInput.setError(null);
        } else {
            binding.buttonLogin.setText("Ingresar");
        }
    }

    private void init() {
        Intent intent = new Intent(this, MainControl.class);
        startActivity(intent);
        finish();
    }

    private void readToken() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_DATA, MODE_PRIVATE);
        String token = prefs.getString(CLIENT_TOKEN, null);
        if(token == null) return;
        init();
    }

    private void showLoginFailed(String error) {
        binding.codeInput.setError(error);
    }

    private void showAlert(String message) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Error");
        builder.setMessage(message);
        builder.setPositiveButton("Aceptar", (dialog, which) -> {
            dialog.dismiss();
        });
    }
}