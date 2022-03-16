package me.zipi.navitotesla.api;

import java.util.List;

import me.zipi.navitotesla.model.Github;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface GithubApi {

    @GET("/repos/{owner}/{repo}/releases?per_page=10")
    Call<List<Github.Release>> getReleases(@Path("owner") String owner, @Path("repo") String repo);

}
