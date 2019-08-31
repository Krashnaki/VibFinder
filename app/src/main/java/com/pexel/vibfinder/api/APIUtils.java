package com.pexel.vibfinder.api;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class APIUtils {

    private String baseUrl = "https://vibfinder.dumme.website/";
    private IAPI apiInterface;

    private void buildRetrofit() {

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new ResponseInterceptor())
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiInterface = retrofit.create(IAPI.class);
    }

    public static IAPI getApiInterface() {
        APIUtils apiUtils = new APIUtils();
        apiUtils.buildRetrofit();
        return apiUtils.apiInterface;
    }
}
