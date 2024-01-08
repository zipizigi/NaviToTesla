package me.zipi.navitotesla.api

import me.zipi.navitotesla.model.Github.Release
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubApi {
    @GET("/repos/{owner}/{repo}/releases?per_page=10")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<List<Release>>
}