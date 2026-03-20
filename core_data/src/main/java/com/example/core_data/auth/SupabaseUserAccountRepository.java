package com.example.core_data.auth;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.core_domain.auth.AvatarUploadResult;
import com.example.core_domain.auth.IUserAccountRepository;
import com.example.core_domain.auth.UserAccountProfile;
import com.example.core_domain.auth.UserProfileUpdateRequest;
import com.example.core_domain.auth.UserRegistrationRequest;
import com.example.core_domain.auth.UserSignUpResult;
import com.example.core_network.auth.SupabaseAuthResultDto;
import com.example.core_network.auth.SupabaseAuthService;
import com.example.core_network.auth.SupabaseAuthSessionDto;
import com.example.core_network.auth.SupabaseAccountLookupService;
import com.example.core_network.auth.SupabaseProfileDto;
import com.example.core_network.auth.SupabaseProfileService;
import com.example.core_network.auth.SupabaseStorageObjectDto;
import com.example.core_network.auth.SupabaseStorageService;
import com.example.core_network.auth.SupabaseUserDto;

import java.io.IOException;

public class SupabaseUserAccountRepository implements IUserAccountRepository {

    private static final String TAG = "UserAccountRepo";
    private static final long SESSION_REFRESH_LEEWAY_SECONDS = 60L;

    private final SupabaseAccountLookupService accountLookupService;
    private final SupabaseAuthService authService;
    private final SupabaseProfileService profileService;
    private final SupabaseStorageService storageService;
    private final AuthSessionStore sessionStore;

    public SupabaseUserAccountRepository(@NonNull Context context) {
        this(new SupabaseAccountLookupService(),
                new SupabaseAuthService(),
                new SupabaseProfileService(),
                new SupabaseStorageService(),
                new AuthSessionStore(context));
    }

    public SupabaseUserAccountRepository(@NonNull SupabaseAccountLookupService accountLookupService,
                                         @NonNull SupabaseAuthService authService,
                                         @NonNull SupabaseProfileService profileService,
                                         @NonNull SupabaseStorageService storageService,
                                         @NonNull AuthSessionStore sessionStore) {
        this.accountLookupService = accountLookupService;
        this.authService = authService;
        this.profileService = profileService;
        this.storageService = storageService;
        this.sessionStore = sessionStore;
    }

    @Nullable
    @Override
    public UserAccountProfile loadCurrentProfile() throws IOException {
        AuthSessionStore.StoredSession storedSession = sessionStore.load();
        if (storedSession == null) {
            Log.d(TAG, "loadCurrentProfile no local session");
            return null;
        }
        SupabaseAuthSessionDto activeSession = ensureActiveSession(storedSession);
        SupabaseUserDto user = authService.getUser(activeSession.getAccessToken());
        SupabaseProfileDto profile = ensureProfile(activeSession, user);
        Log.d(TAG, "loadCurrentProfile userId=" + user.getId());
        return mergeUserAndProfile(user, profile);
    }

    @Nullable
    @Override
    public String resolveSignInEmail(@NonNull String identifier) throws IOException {
        return accountLookupService.resolveSignInEmail(identifier);
    }

    @NonNull
    @Override
    public UserSignUpResult signUp(@NonNull UserRegistrationRequest request) throws IOException {
        SupabaseAuthResultDto authResult = authService.signUp(
                request.getEmail(),
                request.getPassword(),
                request.getUsername());
        if (!authResult.hasActiveSession()) {
            Log.d(TAG, "signUp pending email confirmation email=" + request.getEmail());
            return UserSignUpResult.pendingConfirmation();
        }
        SupabaseAuthSessionDto session = authResult.getSession();
        sessionStore.save(session);
        SupabaseProfileDto profile = ensureProfile(session, session.getUser());
        UserAccountProfile accountProfile = mergeUserAndProfile(session.getUser(), profile);
        Log.d(TAG, "signUp authenticated userId=" + accountProfile.getId());
        return UserSignUpResult.authenticated(accountProfile);
    }

    @NonNull
    @Override
    public UserAccountProfile signIn(@NonNull String email,
                                     @NonNull String password) throws IOException {
        SupabaseAuthSessionDto session = authService.signIn(email, password);
        sessionStore.save(session);
        SupabaseProfileDto profile = ensureProfile(session, session.getUser());
        UserAccountProfile accountProfile = mergeUserAndProfile(session.getUser(), profile);
        Log.d(TAG, "signIn userId=" + accountProfile.getId());
        return accountProfile;
    }

    @NonNull
    @Override
    public AvatarUploadResult uploadAvatar(@NonNull String fileName,
                                           @NonNull String contentType,
                                           @NonNull byte[] data) throws IOException {
        SupabaseAuthSessionDto session = requireActiveSession();
        SupabaseStorageObjectDto avatarObject = storageService.uploadAvatar(
                session.getAccessToken(),
                session.getUser().getId(),
                fileName,
                contentType,
                data);
        Log.d(TAG, "uploadAvatar userId=" + session.getUser().getId()
                + " avatarPath=" + avatarObject.getObjectPath());
        return new AvatarUploadResult(
                avatarObject.getObjectPath(),
                avatarObject.getPublicUrl());
    }

    @NonNull
    @Override
    public UserAccountProfile updateProfile(@NonNull UserProfileUpdateRequest request) throws IOException {
        SupabaseAuthSessionDto session = requireActiveSession();
        SupabaseProfileDto profile = profileService.upsertProfile(
                session.getAccessToken(),
                session.getUser().getId(),
                request.getUsername(),
                request.getAvatarUrl(),
                request.getAvatarPath());
        UserAccountProfile accountProfile = mergeUserAndProfile(session.getUser(), profile);
        Log.d(TAG, "updateProfile userId=" + accountProfile.getId()
                + " username=" + accountProfile.getUsername());
        return accountProfile;
    }

    @Override
    public void signOut() throws IOException {
        AuthSessionStore.StoredSession storedSession = sessionStore.load();
        IOException signOutError = null;
        if (storedSession != null) {
            try {
                authService.signOut(storedSession.getAccessToken());
            } catch (IOException ioException) {
                signOutError = ioException;
            }
        }
        sessionStore.clear();
        Log.d(TAG, "signOut local session cleared");
        if (signOutError != null) {
            throw signOutError;
        }
    }

    @NonNull
    private SupabaseAuthSessionDto requireActiveSession() throws IOException {
        AuthSessionStore.StoredSession storedSession = sessionStore.load();
        if (storedSession == null) {
            throw new IOException("You are not signed in");
        }
        return ensureActiveSession(storedSession);
    }

    @NonNull
    private SupabaseAuthSessionDto ensureActiveSession(@NonNull AuthSessionStore.StoredSession storedSession)
            throws IOException {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (storedSession.getExpiresAtEpochSeconds() > nowSeconds + SESSION_REFRESH_LEEWAY_SECONDS) {
            return new SupabaseAuthSessionDto(
                    storedSession.getAccessToken(),
                    storedSession.getRefreshToken(),
                    storedSession.getExpiresAtEpochSeconds(),
                    new SupabaseUserDto(storedSession.getUserId(), storedSession.getEmail(), null));
        }
        Log.d(TAG, "ensureActiveSession refreshing userId=" + storedSession.getUserId());
        SupabaseAuthSessionDto refreshedSession = authService.refreshSession(storedSession.getRefreshToken());
        sessionStore.save(refreshedSession);
        return refreshedSession;
    }

    @NonNull
    private SupabaseProfileDto ensureProfile(@NonNull SupabaseAuthSessionDto session,
                                             @NonNull SupabaseUserDto user) throws IOException {
        SupabaseProfileDto profile = profileService.getProfile(session.getAccessToken(), user.getId());
        if (profile != null) {
            return profile;
        }
        Log.d(TAG, "ensureProfile creating missing row userId=" + user.getId());
        return profileService.upsertProfile(
                session.getAccessToken(),
                user.getId(),
                user.getUsername(),
                null,
                null);
    }

    @NonNull
    private UserAccountProfile mergeUserAndProfile(@NonNull SupabaseUserDto user,
                                                   @Nullable SupabaseProfileDto profile) {
        String username = profile != null && !TextUtils.isEmpty(profile.getUsername())
                ? profile.getUsername()
                : user.getUsername();
        String avatarUrl = profile != null ? profile.getAvatarUrl() : null;
        String avatarPath = profile != null ? profile.getAvatarPath() : null;
        return new UserAccountProfile(
                user.getId(),
                user.getEmail(),
                username,
                avatarUrl,
                avatarPath);
    }
}
