package com.example.core_network.radio;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RadioBrowserEndpointResolver {

    private static final List<String> ENDPOINTS = Collections.unmodifiableList(Arrays.asList(
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info"
    ));

    @NonNull
    public List<String> getSearchEndpoints() {
        return ENDPOINTS;
    }

    @NonNull
    public List<String> getPlaybackEndpoints() {
        return ENDPOINTS;
    }
}
