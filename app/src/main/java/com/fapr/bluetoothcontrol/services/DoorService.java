package com.fapr.bluetoothcontrol.services;
import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;

/**
 * MANAGE TSR1-B SENSOR COMMANDS AND SIMILAR
 * <p>
 * Get the encrypted commands and decrypt the info for the user.
 * Use the sensor password for correct managing. This service don´t
 * communicate with the sensor.
 * <p>
 */
public class DoorService {
    public static final Integer RESPONSE_SUCCESSFUL = 0x00;
    /*
    Other status vars
    public static final Integer PASSWORD_ERROR = 0x01;
    public static final Integer CRC_ERROR = 0x02;
    public static final Integer INVALID_OPERATION = 0x03;
    public static final Integer FORMAT_ERROR = 0x4;
    public static final Integer OPERATION_VALUE_ERROR = 0x05;
    public static final Integer OPERATION_NOT_SUPPORTED = 0x06;
    */
    public static final Integer RESPONSE_ERROR = 0x08;
    private final String password;

    public DoorService(String password){
        this.password = password;
    }

    public int getResponseStatus(@NonNull byte[] response) {
        if(response.length > 0) {
            return response[0];
        }
        return RESPONSE_ERROR;
    }

    public boolean getTriggerStatus(@NonNull byte[] response) {
        if(getResponseStatus(response) != RESPONSE_SUCCESSFUL) return false;
        return response[2] == 0x01;
    }

    @NonNull
    public byte[] getSensorCommand(Integer operation, byte[] content) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // PUT PASSWORD AND OPERATION
            outputStream.write(password.getBytes());
            outputStream.write(operation);

            // PUT CONTENT
            if(content != null) {
                outputStream.write(content, 0, content.length);
            }

            // CALCULATE CRC
            byte[] realContent = outputStream.toByteArray();
            int crc = calculateCRC(realContent, realContent.length);

            outputStream.write(crc);
            return outputStream.toByteArray();
        } catch (Exception e) {
            return new byte[] { };
        }
    }

    private static int calculateCRC(byte[] bytes, int len) {
        int crc = 0xff;
        for (int j = 0; j < len; j++) {
            crc = crc ^ bytes[j];
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x80) == 0x80) {
                    crc = (crc << 1) ^ 0x31;
                } else {
                    crc = crc << 1;
                }
            }
        }
        return crc & 0xff;
    }
}
