package com.kmmaruf.gitnotifier.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.kmmaruf.gitnotifier.ui.common.Keys;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static GitHubApi api;
    private static final String BASE_URL = "https://api.github.com/";

    public static GitHubApi getApi(Context context) {
        if (api == null) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BASIC);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final String token = prefs.getString(Keys.PREFS_KEY_TOKEN, null);

            Interceptor authInterceptor = chain -> {
                Request.Builder builder = chain.request().newBuilder();
                if (token != null && !token.isEmpty()) {
                    builder.addHeader("Authorization", "token " + token);
                }
                return chain.proceed(builder.build());
            };

            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(log).addInterceptor(authInterceptor).build();

            Retrofit retrofit = new Retrofit.Builder().baseUrl(BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build();

            api = retrofit.create(GitHubApi.class);
        }
        return api;
    }
}