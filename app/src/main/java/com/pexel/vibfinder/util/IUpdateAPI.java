package com.pexel.vibfinder.util;

import com.pexel.vibfinder.objects.Update;

import retrofit2.Call;
import retrofit2.http.GET;

public interface IUpdateAPI {

    @GET("update.json")
    Call<Update> getLatestVersion();
}
