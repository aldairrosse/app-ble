package com.fapr.bluetoothcontrol.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.fapr.bluetoothcontrol.utils.Base32Util;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * VALIDATE AND GENERATE NUMBER TOKENS FROM ID
 * <p>
 * Manage number tokens from id, the token changes every 15 seconds.
 * <p>
 * By Frankil Aldair Pérez Rosales
 */
public class TokenService {
    private final String secretID;
    private static final String ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int TOTP_LENGTH = 6;

    public TokenService(String secretID) {
        this.secretID = Base32Util.encodeToBase32(secretID);
        Log.v("SECRET", this.secretID);
    }

    @NonNull
    public String generateTOTP() {
        byte[] secretKey = Base32Util.decodeBase32(secretID);
        long time = Calendar.getInstance().getTimeInMillis() / 1000;
        long timeStep = time / TIME_STEP_SECONDS;

        byte[] timeStepBytes = ByteBuffer.allocate(8).putLong(timeStep).array();

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey, ALGORITHM);

            mac.init(keySpec);

            byte[] hash = mac.doFinal(timeStepBytes);

            int offset = hash[hash.length - 1] & 0xF;
            int binaryCode = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binaryCode % (int) Math.pow(10, TOTP_LENGTH);

            return String.format("%0" + TOTP_LENGTH + "d", otp);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.e("TOTP error", Objects.requireNonNull(e.getMessage()));
            return "";
        }
    }

    public boolean isInvalid(String token) {
        String generatedToken = generateTOTP();
        return !generatedToken.equals(token);
    }
}
