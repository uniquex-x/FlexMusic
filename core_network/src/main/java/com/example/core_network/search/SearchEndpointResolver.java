package com.example.core_network.search;

import androidx.annotation.NonNull;

public class SearchEndpointResolver {

    private static final String JAMENDO_API_BASE_URL = "https://api.jamendo.com/v3.0";

    @NonNull
    public String getTracksEndpoint() {
        return JAMENDO_API_BASE_URL + "/tracks/";
    }

    @NonNull
    public String getAlbumsEndpoint() {
        return JAMENDO_API_BASE_URL + "/albums/";
    }

    @NonNull
    public String getArtistsEndpoint() {
        return JAMENDO_API_BASE_URL + "/artists/";
    }

    @NonNull
    public String getPlaylistsEndpoint() {
        return JAMENDO_API_BASE_URL + "/playlists/";
    }

    @NonNull
    public String getAutocompleteEndpoint() {
        return JAMENDO_API_BASE_URL + "/autocomplete/";
    }

    @NonNull
    public String getCatalogEndpoint() {
        return getTracksEndpoint();
    }

    @NonNull
    public String getPlaybackResolveEndpoint() {
        return getTracksEndpoint();
    }
}
