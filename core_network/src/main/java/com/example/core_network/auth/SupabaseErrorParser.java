package com.example.core_network.auth;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

final class SupabaseErrorParser {

    private SupabaseErrorParser() {
    }

    @NonNull
    static String extractMessage(@NonNull IOException ioException,
                                 @NonNull String fallbackMessage) {
        String message = ioException.getMessage();
        if (TextUtils.isEmpty(message)) {
            return fallbackMessage;
        }
        int separatorIndex = message.indexOf(": {");
        if (separatorIndex < 0) {
            return message;
        }
        String jsonPayload = message.substring(separatorIndex + 2).trim();
        try {
            JSONObject errorObject = new JSONObject(jsonPayload);
            String extracted = trimToNull(errorObject.optString("msg"));
            if (TextUtils.isEmpty(extracted)) {
                extracted = trimToNull(errorObject.optString("error_description"));
            }
            if (TextUtils.isEmpty(extracted)) {
                extracted = trimToNull(errorObject.optString("message"));
            }
            if (TextUtils.isEmpty(extracted)) {
                extracted = trimToNull(errorObject.optString("error"));
            }
            if (!TextUtils.isEmpty(extracted)) {
                return extracted;
            }
        } catch (JSONException ignored) {
        }
        return message;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
