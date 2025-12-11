package com.fapr.bluetoothcontrol.utils;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DataUtil {
    public static final long SECONDS_SYNC_MARGIN = 5000;
    public static final long SECONDS_CLOCK_UPDATE = 1000;
    public static final String TIME_SYNC_DATA = "TIME_SYNC_DATA";
    public static final String LAST_SYNC_TIME = "LAST_SYNC_TIME";
    public static final String BASE_URI = "https://api-v3-dev.navigation.com.mx/";
    public static final String SETTINGS_DATA = "SETTINGS_DATA";
    public static final String DATA_DELAY_OPTION = "DATA_DELAY_OPTION";
    public static final String DATA_PARKING_ENABLED = "DATA_PARKING_ENABLED";
    public static final String DATA_PAIRING_ENABLED = "DATA_PAIRING_ENABLED";
    public static  final String DATA_DEVICE_ADDRESS = "DATA_DEVICE_ADDRESS";
    public static final String DATA_DEVICE_NAME = "DATA_DEVICE_NAME";
    public static final String SENSOR_PASSWORD = "654321";
    public static final UUID SERVICE_UUID = UUID.fromString("27760001-999C-4D6A-9FC4-C7272BE10900");
    public static final UUID CHARACTERISTIC_UUID = UUID.fromString("27763561-999C-4D6A-9FC4-C7272BE10900");
    public static final String CLIENT_TOKEN = "CLIENT_TOKEN";
    public static final String CLIENT_DEVICES = "CLIENT_DEVICES";
    private static final byte[] pattern = new byte[] {
            (byte) 0x12,
            (byte) 0x16,
            (byte) 0xFF,
            (byte) 0xBF,
            (byte) 0x0E,
            (byte) 0x05
    };
    @NonNull
    public static String bytesToString(@NonNull byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = String.format("%02X", b);
            hexString.append(hex).append(" ");
        }
        return hexString.toString();
    }

    public static boolean startsWithHeader(@Nullable byte[] data) {
        if (data == null || data.length < pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[i] != pattern[i]) return false;
        }
        return true;
    }

    @NonNull
    @Contract("_, _, _ -> new")
    public static String decrypt(@NonNull String encryptedText, String password, String id) throws Exception {
        String[] parts = encryptedText.split(":");
        byte[] iv = Base64.decode(parts[0], Base64.DEFAULT);
        byte[] cipherText = Base64.decode(parts[1], Base64.DEFAULT);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest((password + id).getBytes(StandardCharsets.UTF_8));

        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));

        byte[] original = cipher.doFinal(cipherText);
        return new String(original, StandardCharsets.UTF_8);
    }

    @NonNull
    public static String normalizeMac(@NonNull String mac) {
        return mac.trim().replace(":", "").toUpperCase();
    }

}
