package com.example.core_network.search;

import androidx.annotation.NonNull;

public class SearchEndpointResolver {

    private static final String JAMENDO_API_BASE_URL = "https://api.jamendo.com/v3.0";

    @NonNull
    public String getCatalogEndpoint() {
        return JAMENDO_API_BASE_URL + "/tracks/";
    }

    @NonNull
    public String getPlaybackResolveEndpoint() {
        return JAMENDO_API_BASE_URL + "/tracks/";
    }
}
