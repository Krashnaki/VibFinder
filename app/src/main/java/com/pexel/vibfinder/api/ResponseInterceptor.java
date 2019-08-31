package com.pexel.vibfinder.api;

import android.util.Log;

import com.pexel.vibfinder.BuildConfig;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ResponseInterceptor implements Interceptor {

    private static final String TAG = ResponseInterceptor.class.getSimpleName();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        if (BuildConfig.DEBUG) {

            MediaType contentType = response.body().contentType();
            String bodyString = response.body().string();

            Log.d(TAG, "intercept: URL: " + request.url().toString());
            Log.d(TAG, "intercept: Request: " + (request.body() != null ? request.body().toString() : "empty"));
            Log.d(TAG, "intercept: Response: " + bodyString);

            ResponseBody body = ResponseBody.create(contentType, bodyString);
            return response.newBuilder().body(body).build();
        } else {
            return response;
        }
    }
}