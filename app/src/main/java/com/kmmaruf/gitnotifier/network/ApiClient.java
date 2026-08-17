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
    private static volatile GitHubApi api;
    private static final String BASE_URL = "https://api.github.com/";
    private static final Object LOCK = new Object();

    public static GitHubApi getApi(Context context) {
        if (api == null) {
            synchronized (LOCK) {
                if (api == null) {
                    createClient(context.getApplicationContext());
                }
            }
        }
        return api;
    }

    /**
     * Always read the current token from SharedPreferences so that
     * changing the token in Settings takes effect without process restart.
     */
    private static void createClient(Context context) {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);

        Interceptor authInterceptor = chain -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String token = prefs.getString(Keys.PREFS_KEY_TOKEN, null);

            Request.Builder builder = chain.request().newBuilder();
            if (token != null && !token.trim().isEmpty()) {
                builder.addHeader("Authorization", "token " + token.trim());
            }
            // Recommended by GitHub
            builder.addHeader("Accept", "application/vnd.github+json");
            builder.addHeader("X-GitHub-Api-Version", "2022-11-28");
            return chain.proceed(builder.build());
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(log)
                .addInterceptor(authInterceptor)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(GitHubApi.class);
    }

    /**
     * Call this when the user changes the token so a fresh client is created
     * on the next request (or immediately if desired).
     */
    public static void reset() {
        synchronized (LOCK) {
            api = null;
        }
    }
}
