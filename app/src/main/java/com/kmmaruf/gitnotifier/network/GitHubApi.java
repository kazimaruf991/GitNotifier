package com.kmmaruf.gitnotifier.network;

import com.kmmaruf.gitnotifier.network.model.*;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface GitHubApi {
    @GET("repos/{owner}/{repo}/branches")
    Call<List<Branch>> listBranches(
        @Path("owner") String owner, @Path("repo") String repo);

    @GET("repos/{owner}/{repo}/commits")
    Call<List<Commit>> listCommits(
        @Path("owner") String owner,
        @Path("repo") String repo,
        @Query("sha") String branch,
        @Query("per_page") int perPage);

    @GET("repos/{owner}/{repo}/releases")
    Call<List<Release>> listReleases(
        @Path("owner") String owner, @Path("repo") String repo);
}