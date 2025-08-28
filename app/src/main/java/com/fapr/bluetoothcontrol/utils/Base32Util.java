package com.fapr.bluetoothcontrol.utils;

import androidx.annotation.NonNull;

import java.util.Arrays;

public class Base32Util {
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    @NonNull
    public static String encodeToBase32(@NonNull String input) {
        byte[] bytes = input.getBytes();
        StringBuilder base32 = new StringBuilder((bytes.length * 8 + 4) / 5);

        int currByte, digit, nextByte;

        for (int i = 0; i < bytes.length;) {
            currByte = bytes[i++] & 255;

            base32.append(BASE32_ALPHABET.charAt(currByte >> 3));
            digit = (currByte & 7) << 2;

            if (i >= bytes.length) {
                base32.append(BASE32_ALPHABET.charAt(digit));
                break;
            }

            nextByte = bytes[i++] & 255;
            digit |= nextByte >> 6;
            base32.append(BASE32_ALPHABET.charAt(digit));
            base32.append(BASE32_ALPHABET.charAt((nextByte >> 1) & 31));
            digit = (nextByte & 1) << 4;

            if (i >= bytes.length) {
                base32.append(BASE32_ALPHABET.charAt(digit));
                break;
            }

            currByte = bytes[i++] & 255;
            digit |= currByte >> 4;
            base32.append(BASE32_ALPHABET.charAt(digit));
            digit = (currByte & 15) << 1;

            if (i >= bytes.length) {
                base32.append(BASE32_ALPHABET.charAt(digit));
                break;
            }

            nextByte = bytes[i++] & 255;
            digit |= nextByte >> 7;
            base32.append(BASE32_ALPHABET.charAt(digit));
            base32.append(BASE32_ALPHABET.charAt((nextByte >> 2) & 31));
            digit = (nextByte & 3) << 3;

            if (i >= bytes.length) {
                base32.append(BASE32_ALPHABET.charAt(digit));
                break;
            }

            currByte = bytes[i++] & 255;
            digit |= currByte >> 5;
            base32.append(BASE32_ALPHABET.charAt(digit));
            base32.append(BASE32_ALPHABET.charAt(currByte & 31));
        }

        return base32.toString();
    }

    @NonNull
    public static byte[] decodeBase32(@NonNull String base32) {
        byte[] bytes = new byte[base32.length() * 5 / 8];
        int buffer = 0, bitsLeft = 0, index = 0;

        for (char c : base32.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) throw new IllegalArgumentException("Invalid Base32 character: " + c);

            buffer <<= 5;
            buffer |= value & 31;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                bytes[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }

        return bytes;
    }

    public static final String SECRET_KEY = "pe9MTzo9TNKZCFl1hR2Kg";
}


