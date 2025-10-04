package com.kmmaruf.gitnotifier.network.model;

import java.io.Serializable;

public class Release implements Serializable {
    public long id;
    public String name;
    public String tag_name;
    public String body;
    public String html_url;
}