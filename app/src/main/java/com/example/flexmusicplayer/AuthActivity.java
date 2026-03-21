package com.example.flexmusicplayer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.core_data.auth.SupabaseUserAccountRepository;
import com.example.core_domain.auth.IUserAccountRepository;
import com.example.core_domain.auth.UserRegistrationRequest;
import com.example.core_domain.auth.UserSignUpResult;
import com.example.flexmusicplayer.config.AppConfig;
import com.example.flexmusicplayer.databinding.ActivityAuthBinding;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = "AuthActivity";
    private static final String MODE_SIGN_IN = "sign_in";
    private static final String MODE_REGISTER = "register";

    private ActivityAuthBinding binding;
    private IUserAccountRepository userAccountRepository;
    private final ExecutorService accountExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        Intent intent = new Intent(context, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AppConfig.Features.isAuthEnabled()) {
            Log.d(TAG, "onCreate auth disabled, returning to main");
            openMain();
            return;
        }
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        userAccountRepository = new SupabaseUserAccountRepository(this);

        binding.btnSwitchToRegister.setOnClickListener(v -> showMode(MODE_REGISTER));
        binding.btnSwitchToSignIn.setOnClickListener(v -> showMode(MODE_SIGN_IN));
        binding.btnSignInSubmit.setOnClickListener(v -> attemptSignIn());
        binding.btnRegisterSubmit.setOnClickListener(v -> attemptRegister());

        showMode(MODE_SIGN_IN);
        restoreSessionOrShowAuth();
    }

    @Override
    protected void onDestroy() {
        accountExecutor.shutdownNow();
        binding = null;
        super.onDestroy();
    }

    private void restoreSessionOrShowAuth() {
        setLoading(true);
        accountExecutor.execute(() -> {
            try {
                if (userAccountRepository.loadCurrentProfile() != null) {
                    Log.d(TAG, "restoreSessionOrShowAuth restored active session");
                    runOnMainIfActive(this::openMain);
                    return;
                }
                Log.d(TAG, "restoreSessionOrShowAuth no active session");
            } catch (IOException ioException) {
                Log.w(TAG, "restoreSessionOrShowAuth failed, showing auth UI", ioException);
            }
            runOnMainIfActive(() -> setLoading(false));
        });
    }

    private void attemptSignIn() {
        clearInputErrors();
        String identifier = binding.signInIdentifierInput.getText().toString().trim();
        String password = binding.signInPasswordInput.getText().toString();
        if (!validateIdentifierAndPassword(binding.signInIdentifierInput, binding.signInPasswordInput, identifier, password)) {
            return;
        }
        setLoading(true);
        accountExecutor.execute(() -> {
            try {
                String resolvedEmail = userAccountRepository.resolveSignInEmail(identifier);
                if (TextUtils.isEmpty(resolvedEmail)) {
                    runOnMainIfActive(() -> {
                        setLoading(false);
                        showToast(R.string.account_not_found_register);
                    });
                    return;
                }
                userAccountRepository.signIn(resolvedEmail, password);
                Log.d(TAG, "attemptSignIn success identifier=" + identifier);
                runOnMainIfActive(this::openMain);
            } catch (IOException ioException) {
                Log.w(TAG, "attemptSignIn failed identifier=" + identifier, ioException);
                runOnMainIfActive(() -> {
                    setLoading(false);
                    if (isInvalidCredentialError(ioException)) {
                        showToast(R.string.account_wrong_password);
                    } else {
                        showToast(toDisplayMessage(ioException));
                    }
                });
            }
        });
    }

    private void attemptRegister() {
        clearInputErrors();
        String email = binding.registerEmailInput.getText().toString().trim();
        String userId = normalizeNullable(binding.registerUserIdInput.getText().toString());
        String password = binding.registerPasswordInput.getText().toString();
        if (!validateRegistration(email, userId, password)) {
            return;
        }
        setLoading(true);
        accountExecutor.execute(() -> {
            try {
                UserSignUpResult result = userAccountRepository.signUp(
                        new UserRegistrationRequest(email, password, userId));
                Log.d(TAG, "attemptRegister success email=" + email + " userId=" + userId);
                runOnMainIfActive(() -> {
                    setLoading(false);
                    if (result.hasActiveSession()) {
                        showToast(R.string.account_register_success);
                        openMain();
                    } else if (result.isEmailConfirmationPending()) {
                        showToast(R.string.account_register_check_email);
                    } else {
                        showToast(R.string.account_register_success);
                    }
                });
            } catch (IOException ioException) {
                Log.w(TAG, "attemptRegister failed email=" + email + " userId=" + userId, ioException);
                runOnMainIfActive(() -> {
                    setLoading(false);
                    if (isAlreadyExistsError(ioException)) {
                        showToast(R.string.account_already_exists);
                    } else {
                        showToast(toDisplayMessage(ioException));
                    }
                });
            }
        });
    }

    private boolean validateIdentifierAndPassword(@NonNull EditText identifierInput,
                                                  @NonNull EditText passwordInput,
                                                  @NonNull String identifier,
                                                  @NonNull String password) {
        if (TextUtils.isEmpty(identifier)) {
            identifierInput.setError(getString(R.string.account_identifier_required));
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError(getString(R.string.account_password_required));
            return false;
        }
        return true;
    }

    private boolean validateRegistration(@NonNull String email,
                                         @Nullable String userId,
                                         @NonNull String password) {
        if (TextUtils.isEmpty(email)) {
            binding.registerEmailInput.setError(getString(R.string.account_email_required));
            return false;
        }
        if (TextUtils.isEmpty(userId)) {
            binding.registerUserIdInput.setError(getString(R.string.account_user_id_required));
            return false;
        }
        if (userId.length() < 3) {
            binding.registerUserIdInput.setError(getString(R.string.account_username_too_short));
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            binding.registerPasswordInput.setError(getString(R.string.account_password_required));
            return false;
        }
        if (password.length() < 6) {
            binding.registerPasswordInput.setError(getString(R.string.account_password_too_short));
            return false;
        }
        return true;
    }

    private void showMode(@NonNull String mode) {
        if (binding == null) {
            return;
        }
        boolean signIn = MODE_SIGN_IN.equals(mode);
        binding.signInSection.setVisibility(signIn ? View.VISIBLE : View.GONE);
        binding.registerSection.setVisibility(signIn ? View.GONE : View.VISIBLE);
        clearInputErrors();
    }

    private void setLoading(boolean loading) {
        if (binding == null) {
            return;
        }
        binding.authLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.authScroll.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        binding.btnSignInSubmit.setEnabled(!loading);
        binding.btnRegisterSubmit.setEnabled(!loading);
    }

    private void openMain() {
        if ((binding == null && AppConfig.Features.isAuthEnabled()) || isFinishing() || isDestroyed()) {
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void clearInputErrors() {
        if (binding == null) {
            return;
        }
        binding.signInIdentifierInput.setError(null);
        binding.signInPasswordInput.setError(null);
        binding.registerEmailInput.setError(null);
        binding.registerUserIdInput.setError(null);
        binding.registerPasswordInput.setError(null);
    }

    @Nullable
    private String normalizeNullable(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isInvalidCredentialError(@NonNull IOException ioException) {
        String message = ioException.getMessage();
        return message != null && message.toLowerCase().contains("invalid login credentials");
    }

    private boolean isAlreadyExistsError(@NonNull IOException ioException) {
        String message = ioException.getMessage();
        if (message == null) {
            return false;
        }
        String normalizedMessage = message.toLowerCase();
        return normalizedMessage.contains("user already registered")
                || normalizedMessage.contains("already registered")
                || normalizedMessage.contains("already exists")
                || normalizedMessage.contains("duplicate key value")
                || normalizedMessage.contains("duplicate");
    }

    @NonNull
    private String toDisplayMessage(@NonNull IOException ioException) {
        String message = ioException.getMessage();
        return TextUtils.isEmpty(message) ? getString(R.string.error) : message;
    }

    private void showToast(int messageRes) {
        showToast(getString(messageRes));
    }

    private void showToast(@NonNull String message) {
        if (!isUiActive()) {
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void runOnMainIfActive(@NonNull Runnable action) {
        mainHandler.post(() -> {
            if (isUiActive()) {
                action.run();
            }
        });
    }

    private boolean isUiActive() {
        return binding != null && !isFinishing() && !isDestroyed();
    }
}
