package com.example.core_domain.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

/**
 * @brief Repository contract for authenticated user session and profile management.
 *
 * Implementations are responsible for coordinating account registration,
 * password-based sign-in, session persistence, profile loading, profile updates,
 * avatar uploads, and logout. Callers must not depend on transport details such
 * as Supabase Auth, PostgREST, or Storage endpoints.
 */
public interface IUserAccountRepository {

    /**
     * @brief Load the current authenticated profile using any persisted session.
     * @return The authenticated profile, or null when no valid local session exists.
     * @throws IOException When the remote session or profile cannot be restored.
     */
    @Nullable
    UserAccountProfile loadCurrentProfile() throws IOException;

    /**
     * @brief Resolve a user-entered account identifier into the email used by password Auth.
     * @param identifier User-entered email address or user ID.
     * @return The canonical email for sign-in, or null when no such account exists.
     * @throws IOException When the account lookup request fails.
     */
    @Nullable
    String resolveSignInEmail(@NonNull String identifier) throws IOException;

    /**
     * @brief Register a new password-based account.
     * @param request Registration payload containing email, password, and optional username.
     * @return Result describing whether a live session was created or email confirmation is pending.
     * @throws IOException When the registration request or profile initialization fails.
     */
    @NonNull
    UserSignUpResult signUp(@NonNull UserRegistrationRequest request) throws IOException;

    /**
     * @brief Sign in with an email account and password.
     * @param email User email address used as the account identifier.
     * @param password Raw password provided by the user.
     * @return The authenticated profile resolved for the active session.
     * @throws IOException When credentials are invalid or the session cannot be established.
     */
    @NonNull
    UserAccountProfile signIn(@NonNull String email,
                              @NonNull String password) throws IOException;

    /**
     * @brief Upload a new avatar object for the current authenticated user.
     * @param fileName Original or generated file name used to derive the object path.
     * @param contentType MIME type that should be stored with the uploaded object.
     * @param data Raw avatar bytes.
     * @return Uploaded avatar path and public URL.
     * @throws IOException When no authenticated session exists or the upload fails.
     */
    @NonNull
    AvatarUploadResult uploadAvatar(@NonNull String fileName,
                                    @NonNull String contentType,
                                    @NonNull byte[] data) throws IOException;

    /**
     * @brief Update the current authenticated user's profile fields.
     * @param request Profile fields that should be persisted for the current user.
     * @return The updated authenticated profile snapshot.
     * @throws IOException When the user is signed out or the remote profile update fails.
     */
    @NonNull
    UserAccountProfile updateProfile(@NonNull UserProfileUpdateRequest request) throws IOException;

    /**
     * @brief Clear the current authenticated session locally and remotely.
     * @throws IOException When the remote logout request fails before local cleanup completes.
     */
    void signOut() throws IOException;
}
