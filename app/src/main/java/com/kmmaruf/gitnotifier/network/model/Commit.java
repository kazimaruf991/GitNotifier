package com.kmmaruf.gitnotifier.network.model;
import java.io.Serializable;

public class Commit implements Serializable {
    public String sha;
    public String html_url;
    public CommitData commit;
    public static class CommitData implements Serializable {
        public String message;
        public Author author;
    }
    public static class Author implements Serializable {
        public String date;
    }
}