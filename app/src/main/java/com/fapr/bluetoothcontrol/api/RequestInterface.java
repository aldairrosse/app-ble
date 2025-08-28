package com.fapr.bluetoothcontrol.api;

import com.fapr.bluetoothcontrol.models.KeyModel;
import com.fapr.bluetoothcontrol.models.LoginModel;
import com.fapr.bluetoothcontrol.models.TimeSyncModel;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface RequestInterface {
    @GET("/api/servercontrol/sync")
    Call<TimeSyncModel> getSyncTime();

    @POST("/api/v2/bluetooth/acceso")
    Call<KeyModel> getLoginKey(@Body LoginModel data);
}
