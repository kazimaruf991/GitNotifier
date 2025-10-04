package com.kmmaruf.gitnotifier.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkUtils {

    /**
     * Checks if the device is connected to any network that has internet capabilities.
     */
    public static boolean isInternetAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            Network network = cm.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        }
        return false;
    }

    /**
     * Performs a real-world check to determine if actual internet access is available.
     */
    public static boolean hasActiveInternetConnection() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://clients3.google.com/generate_204").openConnection();

            connection.setRequestProperty("User-Agent", "GitNotifier");
            connection.setRequestProperty("Connection", "close");
            connection.setConnectTimeout(1500); // milliseconds
            connection.connect();

            return connection.getResponseCode() == 204;
        } catch (IOException e) {
            return false;
        }
    }
}

