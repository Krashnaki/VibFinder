package com.pexel.vibfinder.api;

import com.pexel.vibfinder.objects.ReportDevice;
import com.pexel.vibfinder.objects.ResponseMessage;
import com.pexel.vibfinder.objects.Update;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface IAPI {

    @POST("/api/v1/reports/create")
    Call<ResponseMessage> reportDevice(@Body ReportDevice device);

    @POST("/api/v1/devices/submit")
    Call<ResponseMessage> submitDevice(@Body String address,
                                       @Body String deviceName,
                                       @Body List<String> uuids,
                                       @Body Double latitude,
                                       @Body Double longitude);

    @GET("/api/v1/info/update/latest")
    Call<Update> getLatestVersion();
}
