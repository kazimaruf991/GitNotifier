package com.kmmaruf.gitnotifier.network.model;

import java.io.Serializable;

public class RateLimitInfo implements Serializable {
    public String limit;
    public String remaining;
    public String used;
    public String reset;
    public String resource;

    public RateLimitInfo() {
    }

    public RateLimitInfo(String limit, String remaining, String used, String reset, String resource) {
        this.limit = limit;
        this.remaining = remaining;
        this.used = used;
        this.reset = reset;
        this.resource = resource;
    }
}