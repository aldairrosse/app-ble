package com.fapr.bluetoothcontrol.models;

public class LoginModel {
    private String code;

    public LoginModel() {
    }

    public LoginModel(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
