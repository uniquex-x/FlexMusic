package com.example.flexmusicplayer.auth;

public final class AuthProcessSessionState {

    private static volatile boolean authenticatedInProcess;

    private AuthProcessSessionState() {
    }

    public static boolean isAuthenticatedInProcess() {
        return authenticatedInProcess;
    }

    public static void markAuthenticated() {
        authenticatedInProcess = true;
    }

    public static void markUnauthenticated() {
        authenticatedInProcess = false;
    }
}
