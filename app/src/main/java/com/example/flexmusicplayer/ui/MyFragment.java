package com.example.flexmusicplayer.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.core_data.auth.SupabaseUserAccountRepository;
import com.example.core_domain.auth.AvatarUploadResult;
import com.example.core_domain.auth.IUserAccountRepository;
import com.example.core_domain.auth.UserAccountProfile;
import com.example.core_domain.auth.UserProfileUpdateRequest;
import com.example.core_domain.auth.UserRegistrationRequest;
import com.example.core_domain.auth.UserSignUpResult;
import com.example.flexmusicplayer.AuthActivity;
import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.auth.AuthProcessSessionState;
import com.example.flexmusicplayer.config.AppConfig;
import com.example.flexmusicplayer.model.Playlist;
import com.example.flexmusicplayer.storage.PlaylistStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyFragment extends Fragment {

    public interface NavigationCallback {
        void navigateToFavorites();
        void navigateToRecent();
        void navigateToLocal();
        void navigateToTranscode();
        void navigateToPlaylistDetail(long playlistId);
    }

    private static final String TAG = "MyFragment";

    private NavigationCallback navigationCallback;
    private RecyclerView playlistsRecycler;
    private PlaylistAdapter playlistAdapter;
    private TextView playlistsEmptyText;
    private PlaylistStore playlistStore;

    private ImageView profileAvatarImage;
    private View profileOnlineDot;
    private TextView profileNameText;
    private TextView profileStatusBadge;
    private TextView profileAccountText;
    private TextView profileActionButton;

    private IUserAccountRepository userAccountRepository;
    private final ExecutorService accountExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String[]> avatarPickerLauncher;

    @Nullable
    private UserAccountProfile currentUserProfile;
    @Nullable
    private Uri pendingAvatarUri;
    @Nullable
    private AlertDialog activeProfileDialog;
    @Nullable
    private ImageView activeDialogAvatarPreview;
    @Nullable
    private TextView activeDialogAvatarHint;

    public void setNavigationCallback(NavigationCallback callback) {
        this.navigationCallback = callback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        onAvatarPicked(uri);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my, container, false);

        ImageView searchButton = view.findViewById(R.id.btn_search);
        ImageView settingsButton = view.findViewById(R.id.btn_settings);
        View favoritesCard = view.findViewById(R.id.card_favorites);
        View recentCard = view.findViewById(R.id.card_recent);
        View localCard = view.findViewById(R.id.card_local);
        View transcodeCard = view.findViewById(R.id.card_transcode);
        View createButton = view.findViewById(R.id.btn_create_playlist);
        View avatarFrame = view.findViewById(R.id.avatar_frame);

        playlistsRecycler = view.findViewById(R.id.playlists_recycler);
        playlistsEmptyText = view.findViewById(R.id.playlists_empty_text);
        profileAvatarImage = view.findViewById(R.id.profile_avatar_image);
        profileOnlineDot = view.findViewById(R.id.profile_online_dot);
        profileNameText = view.findViewById(R.id.profile_name_text);
        profileStatusBadge = view.findViewById(R.id.profile_status_badge);
        profileAccountText = view.findViewById(R.id.profile_account_text);
        profileActionButton = view.findViewById(R.id.btn_profile_action);

        playlistStore = new PlaylistStore(requireContext());
        if (AppConfig.Features.isAuthEnabled()) {
            userAccountRepository = new SupabaseUserAccountRepository(requireContext());
        }

        settingsButton.setOnClickListener(v -> openSettings());
        searchButton.setOnClickListener(v -> openSearch());
        favoritesCard.setOnClickListener(v -> navigateFavorites());
        recentCard.setOnClickListener(v -> navigateRecent());
        localCard.setOnClickListener(v -> navigateLocal());
        transcodeCard.setOnClickListener(v -> navigateTranscode());
        createButton.setOnClickListener(v -> showCreatePlaylistDialog());
        profileActionButton.setOnClickListener(v -> onProfileActionClicked());
        avatarFrame.setOnClickListener(v -> {
            if (currentUserProfile != null) {
                showEditProfileDialog(currentUserProfile);
            }
        });

        playlistsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        playlistsRecycler.setNestedScrollingEnabled(false);
        playlistAdapter = new PlaylistAdapter();
        playlistsRecycler.setAdapter(playlistAdapter);

        renderInitialProfileState();
        loadPlaylists();
        refreshUserProfile(false);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPlaylists();
        refreshUserProfile(false);
    }

    @Override
    public void onDestroyView() {
        if (activeProfileDialog != null) {
            activeProfileDialog.dismiss();
        }
        activeProfileDialog = null;
        activeDialogAvatarPreview = null;
        activeDialogAvatarHint = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        accountExecutor.shutdownNow();
        super.onDestroy();
    }

    private void openSearch() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSearch();
        }
    }

    private void openSettings() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSettings();
        }
    }

    private void navigateFavorites() {
        if (navigationCallback != null) {
            navigationCallback.navigateToFavorites();
        }
    }

    private void navigateRecent() {
        if (navigationCallback != null) {
            navigationCallback.navigateToRecent();
        }
    }

    private void navigateLocal() {
        if (navigationCallback != null) {
            navigationCallback.navigateToLocal();
        }
    }

    private void navigateTranscode() {
        if (navigationCallback != null) {
            navigationCallback.navigateToTranscode();
        }
    }

    private void loadPlaylists() {
        if (playlistStore == null) {
            return;
        }
        List<Playlist> playlists = playlistStore.loadPlaylists();
        playlistAdapter.setPlaylists(playlists);
        boolean isEmpty = playlists.isEmpty();
        playlistsRecycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        playlistsEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void refreshUserProfile(boolean showErrorToast) {
        if (!AppConfig.Features.isAuthEnabled()) {
            renderAuthDisabledProfile();
            return;
        }
        if (userAccountRepository == null) {
            return;
        }
        accountExecutor.execute(() -> {
            try {
                UserAccountProfile profile = userAccountRepository.loadCurrentProfile();
                mainHandler.post(() -> renderUserProfile(profile));
            } catch (IOException ioException) {
                android.util.Log.e(TAG, "refreshUserProfile failed", ioException);
                mainHandler.post(() -> {
                    renderGuestProfile();
                    if (showErrorToast && isAdded()) {
                        Toast.makeText(requireContext(),
                                toDisplayMessage(ioException),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void renderUserProfile(@Nullable UserAccountProfile profile) {
        currentUserProfile = profile;
        if (!isAdded()
                || profileNameText == null
                || profileStatusBadge == null
                || profileAccountText == null
                || profileActionButton == null
                || profileAvatarImage == null
                || profileOnlineDot == null) {
            return;
        }
        if (profile == null) {
            renderGuestProfile();
            return;
        }
        profileNameText.setText(resolveDisplayName(profile));
        profileStatusBadge.setText(R.string.my_signed_in_badge);
        profileAccountText.setText(getString(R.string.my_account_format, profile.getEmail()));
        profileActionButton.setText(R.string.account_edit_profile);
        profileActionButton.setEnabled(true);
        profileOnlineDot.setVisibility(View.VISIBLE);
        loadAvatarInto(profileAvatarImage, profile.getAvatarUrl());
    }

    private void renderGuestProfile() {
        currentUserProfile = null;
        if (!isAdded()
                || profileNameText == null
                || profileStatusBadge == null
                || profileAccountText == null
                || profileActionButton == null
                || profileAvatarImage == null
                || profileOnlineDot == null) {
            return;
        }
        profileNameText.setText(R.string.my_guest_name);
        profileStatusBadge.setText(R.string.my_guest_badge);
        profileAccountText.setText(R.string.my_guest_status);
        profileActionButton.setText(R.string.account_sign_in);
        profileActionButton.setEnabled(true);
        profileOnlineDot.setVisibility(View.GONE);
        showPlaceholderAvatar(profileAvatarImage);
    }

    private void renderInitialProfileState() {
        if (AppConfig.Features.isAuthEnabled()) {
            renderGuestProfile();
            return;
        }
        renderAuthDisabledProfile();
    }

    private void renderAuthDisabledProfile() {
        currentUserProfile = null;
        if (!isAdded()
                || profileNameText == null
                || profileStatusBadge == null
                || profileAccountText == null
                || profileActionButton == null
                || profileAvatarImage == null
                || profileOnlineDot == null) {
            return;
        }
        profileNameText.setText(R.string.my_auth_disabled_name);
        profileStatusBadge.setText(R.string.my_auth_disabled_badge);
        profileAccountText.setText(R.string.my_auth_disabled_status);
        profileActionButton.setText(R.string.account_auth_disabled_action);
        profileActionButton.setEnabled(false);
        profileOnlineDot.setVisibility(View.GONE);
        showPlaceholderAvatar(profileAvatarImage);
    }

    private void onProfileActionClicked() {
        if (!AppConfig.Features.isAuthEnabled()) {
            showToast(R.string.account_auth_disabled_message);
            return;
        }
        if (currentUserProfile == null) {
            openAuthScreen();
        } else {
            showEditProfileDialog(currentUserProfile);
        }
    }

    private void openAuthScreen() {
        if (!AppConfig.Features.isAuthEnabled()) {
            showToast(R.string.account_auth_disabled_message);
            return;
        }
        startActivity(AuthActivity.createIntent(requireContext()));
        requireActivity().finish();
    }

    private void showAccountEntryDialog() {
        String[] options = {
                getString(R.string.account_sign_in),
                getString(R.string.account_sign_up)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_login_or_register)
                .setMessage(R.string.account_identifier_email_only)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showSignInDialog();
                    } else if (which == 1) {
                        showSignUpDialog();
                    }
                })
                .show();
    }

    private void showSignInDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sign_in, null);
        EditText emailInput = dialogView.findViewById(R.id.input_email);
        EditText passwordInput = dialogView.findViewById(R.id.input_password);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_sign_in)
                .setView(dialogView)
                .setPositiveButton(R.string.account_sign_in, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            if (!validateEmailAndPassword(emailInput, passwordInput, email, password)) {
                return;
            }
            performSignIn(dialog, email, password);
        }));
        dialog.show();
    }

    private void showSignUpDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sign_up, null);
        EditText emailInput = dialogView.findViewById(R.id.input_email);
        EditText passwordInput = dialogView.findViewById(R.id.input_password);
        EditText usernameInput = dialogView.findViewById(R.id.input_username);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_sign_up)
                .setView(dialogView)
                .setPositiveButton(R.string.account_sign_up, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            String username = normalizeNullable(usernameInput.getText().toString());
            if (!validateEmailAndPassword(emailInput, passwordInput, email, password)) {
                return;
            }
            if (!validateUsername(usernameInput, username)) {
                return;
            }
            performSignUp(dialog, new UserRegistrationRequest(email, password, username));
        }));
        dialog.show();
    }

    private void showEditProfileDialog(@NonNull UserAccountProfile profile) {
        pendingAvatarUri = null;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null);
        ImageView avatarPreview = dialogView.findViewById(R.id.profile_avatar_preview);
        TextView emailText = dialogView.findViewById(R.id.profile_email_text);
        EditText usernameInput = dialogView.findViewById(R.id.input_username);
        TextView avatarHint = dialogView.findViewById(R.id.profile_avatar_hint);
        TextView selectAvatarButton = dialogView.findViewById(R.id.btn_select_avatar);

        emailText.setText(getString(R.string.my_account_format, profile.getEmail()));
        usernameInput.setText(profile.getUsername());
        avatarHint.setText(R.string.account_avatar_not_selected);
        loadAvatarInto(avatarPreview, profile.getAvatarUrl());
        selectAvatarButton.setOnClickListener(v -> avatarPickerLauncher.launch(new String[]{"image/*"}));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_edit_profile)
                .setView(dialogView)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.account_sign_out, null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            activeProfileDialog = null;
            activeDialogAvatarPreview = null;
            activeDialogAvatarHint = null;
            pendingAvatarUri = null;
        });
        dialog.setOnShowListener(ignored -> {
            activeProfileDialog = dialog;
            activeDialogAvatarPreview = avatarPreview;
            activeDialogAvatarHint = avatarHint;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String username = normalizeNullable(usernameInput.getText().toString());
                if (!validateUsername(usernameInput, username)) {
                    return;
                }
                performProfileSave(dialog, profile, username);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> performSignOut(dialog));
        });
        dialog.show();
    }

    private void performSignIn(@NonNull AlertDialog dialog,
                               @NonNull String email,
                               @NonNull String password) {
        setDialogButtonsEnabled(dialog, false);
        accountExecutor.execute(() -> {
            try {
                UserAccountProfile profile = userAccountRepository.signIn(email, password);
                mainHandler.post(() -> {
                    renderUserProfile(profile);
                    dialog.dismiss();
                    showToast(R.string.account_login_success);
                });
            } catch (IOException ioException) {
                android.util.Log.e(TAG, "performSignIn failed", ioException);
                mainHandler.post(() -> {
                    setDialogButtonsEnabled(dialog, true);
                    showToast(toDisplayMessage(ioException));
                });
            }
        });
    }

    private void performSignUp(@NonNull AlertDialog dialog,
                               @NonNull UserRegistrationRequest request) {
        setDialogButtonsEnabled(dialog, false);
        accountExecutor.execute(() -> {
            try {
                UserSignUpResult result = userAccountRepository.signUp(request);
                mainHandler.post(() -> {
                    dialog.dismiss();
                    if (result.hasActiveSession() && result.getProfile() != null) {
                        renderUserProfile(result.getProfile());
                        showToast(R.string.account_register_success);
                    } else if (result.isEmailConfirmationPending()) {
                        renderGuestProfile();
                        showToast(R.string.account_register_check_email);
                    }
                });
            } catch (IOException ioException) {
                android.util.Log.e(TAG, "performSignUp failed", ioException);
                mainHandler.post(() -> {
                    setDialogButtonsEnabled(dialog, true);
                    showToast(toDisplayMessage(ioException));
                });
            }
        });
    }

    private void performProfileSave(@NonNull AlertDialog dialog,
                                    @NonNull UserAccountProfile baseProfile,
                                    @Nullable String username) {
        Context appContext = requireContext().getApplicationContext();
        Uri selectedAvatarUri = pendingAvatarUri;
        setDialogButtonsEnabled(dialog, false);
        accountExecutor.execute(() -> {
            try {
                String avatarUrl = baseProfile.getAvatarUrl();
                String avatarPath = baseProfile.getAvatarPath();
                if (selectedAvatarUri != null) {
                    AvatarBinaryData avatarBinaryData = readAvatarBinaryData(appContext, selectedAvatarUri);
                    AvatarUploadResult uploadResult = userAccountRepository.uploadAvatar(
                            avatarBinaryData.fileName,
                            avatarBinaryData.contentType,
                            avatarBinaryData.data);
                    avatarUrl = uploadResult.getAvatarUrl();
                    avatarPath = uploadResult.getAvatarPath();
                }
                UserAccountProfile updatedProfile = userAccountRepository.updateProfile(
                        new UserProfileUpdateRequest(username, avatarUrl, avatarPath));
                mainHandler.post(() -> {
                    renderUserProfile(updatedProfile);
                    dialog.dismiss();
                    showToast(R.string.account_profile_saved);
                });
            } catch (IOException ioException) {
                android.util.Log.e(TAG, "performProfileSave failed", ioException);
                mainHandler.post(() -> {
                    setDialogButtonsEnabled(dialog, true);
                    showToast(toDisplayMessage(ioException));
                });
            }
        });
    }

    private void performSignOut(@NonNull AlertDialog dialog) {
        setDialogButtonsEnabled(dialog, false);
        accountExecutor.execute(() -> {
            try {
                userAccountRepository.signOut();
                AuthProcessSessionState.markUnauthenticated();
                mainHandler.post(() -> {
                    dialog.dismiss();
                    showToast(R.string.account_sign_out_success);
                    openAuthScreen();
                });
            } catch (IOException ioException) {
                AuthProcessSessionState.markUnauthenticated();
                android.util.Log.e(TAG, "performSignOut failed", ioException);
                mainHandler.post(() -> {
                    dialog.dismiss();
                    showToast(toDisplayMessage(ioException));
                    openAuthScreen();
                });
            }
        });
    }

    private boolean validateEmailAndPassword(@NonNull EditText emailInput,
                                             @NonNull EditText passwordInput,
                                             @NonNull String email,
                                             @NonNull String password) {
        if (TextUtils.isEmpty(email)) {
            emailInput.setError(getString(R.string.account_email_required));
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError(getString(R.string.account_password_required));
            return false;
        }
        if (password.length() < 6) {
            passwordInput.setError(getString(R.string.account_password_too_short));
            return false;
        }
        return true;
    }

    private boolean validateUsername(@NonNull EditText usernameInput,
                                     @Nullable String username) {
        if (!TextUtils.isEmpty(username) && username.length() < 3) {
            usernameInput.setError(getString(R.string.account_username_too_short));
            return false;
        }
        return true;
    }

    private void onAvatarPicked(@NonNull Uri uri) {
        pendingAvatarUri = uri;
        if (!isAdded()) {
            return;
        }
        if (activeDialogAvatarPreview != null) {
            showLocalAvatar(activeDialogAvatarPreview, uri);
        }
        if (activeDialogAvatarHint != null) {
            activeDialogAvatarHint.setText(getString(
                    R.string.account_avatar_selected,
                    resolveDisplayName(requireContext(), uri)));
        }
    }

    private void loadAvatarInto(@NonNull ImageView imageView,
                                @Nullable String avatarUrl) {
        if (TextUtils.isEmpty(avatarUrl)) {
            showPlaceholderAvatar(imageView);
            return;
        }
        imageView.setTag(avatarUrl);
        accountExecutor.execute(() -> {
            Bitmap bitmap = downloadBitmap(avatarUrl);
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                Object currentTag = imageView.getTag();
                if (currentTag == null || !TextUtils.equals(String.valueOf(currentTag), avatarUrl)) {
                    return;
                }
                if (bitmap == null) {
                    showPlaceholderAvatar(imageView);
                } else {
                    imageView.setColorFilter(null);
                    imageView.setPadding(0, 0, 0, 0);
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    @Nullable
    private Bitmap downloadBitmap(@NonNull String avatarUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(avatarUrl).openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            connection.setDoInput(true);
            connection.connect();
            try (InputStream inputStream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(inputStream);
            }
        } catch (IOException ioException) {
            android.util.Log.w(TAG, "downloadBitmap failed url=" + avatarUrl, ioException);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void showPlaceholderAvatar(@NonNull ImageView imageView) {
        imageView.setTag(null);
        imageView.setImageResource(R.drawable.ic_person);
        imageView.setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18));
        imageView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_700));
    }

    private void showLocalAvatar(@NonNull ImageView imageView,
                                 @NonNull Uri uri) {
        imageView.setTag(null);
        imageView.setColorFilter(null);
        imageView.setPadding(0, 0, 0, 0);
        imageView.setImageURI(uri);
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @NonNull
    private AvatarBinaryData readAvatarBinaryData(@NonNull Context context,
                                                  @NonNull Uri uri) throws IOException {
        String fileName = resolveDisplayName(context, uri);
        String contentType = context.getContentResolver().getType(uri);
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IOException(context.getString(R.string.account_avatar_read_failed));
            }
            byte[] buffer = new byte[8_192];
            int count;
            while ((count = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, count);
            }
            return new AvatarBinaryData(
                    fileName,
                    TextUtils.isEmpty(contentType) ? "application/octet-stream" : contentType,
                    outputStream.toByteArray());
        }
    }

    @NonNull
    private String resolveDisplayName(@NonNull Context context,
                                      @NonNull Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver()
                    .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String displayName = cursor.getString(index);
                    if (!TextUtils.isEmpty(displayName)) {
                        return displayName;
                    }
                }
            }
        } catch (Exception exception) {
            android.util.Log.w(TAG, "resolveDisplayName failed", exception);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String lastPathSegment = uri.getLastPathSegment();
        return TextUtils.isEmpty(lastPathSegment) ? "avatar" : lastPathSegment;
    }

    @NonNull
    private String resolveDisplayName(@NonNull UserAccountProfile profile) {
        if (!TextUtils.isEmpty(profile.getUsername())) {
            return profile.getUsername();
        }
        int separatorIndex = profile.getEmail().indexOf('@');
        if (separatorIndex > 0) {
            return profile.getEmail().substring(0, separatorIndex);
        }
        return profile.getEmail();
    }

    @Nullable
    private String normalizeNullable(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @NonNull
    private String toDisplayMessage(@NonNull IOException ioException) {
        String message = ioException.getMessage();
        if (TextUtils.isEmpty(message)) {
            return getString(R.string.error);
        }
        return message;
    }

    private void setDialogButtonsEnabled(@NonNull AlertDialog dialog,
                                         boolean enabled) {
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(enabled);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(enabled);
        }
    }

    private void showToast(int messageResId) {
        showToast(getString(messageResId));
    }

    private void showToast(@NonNull String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showCreatePlaylistDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_playlist, null);
        EditText nameInput = dialogView.findViewById(R.id.playlist_name_input);
        EditText descriptionInput = dialogView.findViewById(R.id.playlist_description_input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                nameInput.setError(getString(R.string.playlist_name_required));
                return;
            }
            playlistStore.createPlaylist(name, description);
            loadPlaylists();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showPlaylistOptions(@NonNull Playlist playlist) {
        String[] options = {getString(R.string.edit_playlist), getString(R.string.delete_playlist)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(playlist.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditPlaylistDialog(playlist);
                    } else if (which == 1) {
                        showDeletePlaylistDialog(playlist);
                    }
                })
                .show();
    }

    private void showEditPlaylistDialog(@NonNull Playlist playlist) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_playlist, null);
        EditText nameInput = dialogView.findViewById(R.id.playlist_name_input);
        EditText descriptionInput = dialogView.findViewById(R.id.playlist_description_input);
        nameInput.setText(playlist.getName());
        descriptionInput.setText(playlist.getDescription());

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                nameInput.setError(getString(R.string.playlist_name_required));
                return;
            }
            playlist.setName(name);
            playlist.setDescription(description);
            playlist.setModifiedDate(System.currentTimeMillis());
            playlistStore.updatePlaylist(playlist);
            loadPlaylists();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showDeletePlaylistDialog(@NonNull Playlist playlist) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_playlist_title)
                .setMessage(R.string.dialog_delete_playlist_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    playlistStore.deletePlaylist(playlist.getId());
                    loadPlaylists();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static class AvatarBinaryData {
        private final String fileName;
        private final String contentType;
        private final byte[] data;

        private AvatarBinaryData(@NonNull String fileName,
                                 @NonNull String contentType,
                                 @NonNull byte[] data) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.data = data;
        }
    }

    private class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
        private final List<Playlist> playlists = new ArrayList<>();

        void setPlaylists(@NonNull List<Playlist> updatedPlaylists) {
            playlists.clear();
            playlists.addAll(updatedPlaylists);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_playlist_vertical, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(playlists.get(position));
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView playlistName;
            private final TextView playlistMeta;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                playlistName = itemView.findViewById(R.id.playlist_name);
                playlistMeta = itemView.findViewById(R.id.song_count);
            }

            void bind(Playlist playlist) {
                playlistName.setText(playlist.getName());
                if (TextUtils.isEmpty(playlist.getDescription())) {
                    playlistMeta.setText(getString(R.string.songs_count, playlist.getSongCount()));
                } else {
                    playlistMeta.setText(getString(
                            R.string.playlist_meta_with_description,
                            playlist.getDescription(),
                            playlist.getSongCount()));
                }
                itemView.setOnClickListener(v -> {
                    if (navigationCallback != null) {
                        navigationCallback.navigateToPlaylistDetail(playlist.getId());
                    }
                });
                itemView.findViewById(R.id.options_button).setOnClickListener(v -> showPlaylistOptions(playlist));
            }
        }
    }
}
