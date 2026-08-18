package com.kmmaruf.gitnotifier.network.model;

/**
 * Subset of GitHub GET /rate_limit JSON.
 * https://docs.github.com/en/rest/rate-limit
 */
public class RateLimitResponse {
    public Resources resources;

    public static class Resources {
        public Core core;
    }

    public static class Core {
        public int limit;
        public int remaining;
        public long reset; // unix seconds
        public int used;
    }
}
