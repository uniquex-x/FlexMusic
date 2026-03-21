package com.example.flexmusicplayer.ui;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.feature_transcode.TranscodeRepository;
import com.example.feature_transcode.TranscodeSettings;
import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.storage.LocalMusicStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TranscodeFragment extends Fragment {

    private View startState;
    private View resultState;
    private View selectedFileRow;
    private TextView selectedCountText;
    private TextView selectedFileText;
    private TextView resultActiveName;
    private TextView resultActiveTarget;
    private TextView resultActiveProgress;
    private TextView resultActiveBadge;
    private ProgressBar resultActiveBar;
    private MaterialButton selectFileButton;
    private MaterialButton startTranscodeButton;
    private MaterialButton transcodeAgainButton;
    private ChipGroup formatChipGroup;
    private ActivityResultLauncher<String[]> filePickerLauncher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService transcodeExecutor = Executors.newSingleThreadExecutor();
    private TranscodeRepository transcodeRepository;
    private LocalMusicStore localMusicStore;
    private Uri selectedFileUri;
    private String selectedFileName;
    private boolean transcodeInFlight = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        transcodeRepository = new TranscodeRepository(requireContext());
        localMusicStore = new LocalMusicStore(requireContext());
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onFileSelected);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transcode, container, false);
        initViews(view);
        setupClickListeners();
        showStartState();
        return view;
    }

    @Override
    public void onDestroyView() {
        startState = null;
        resultState = null;
        selectedFileRow = null;
        selectedCountText = null;
        selectedFileText = null;
        resultActiveName = null;
        resultActiveTarget = null;
        resultActiveProgress = null;
        resultActiveBadge = null;
        resultActiveBar = null;
        selectFileButton = null;
        startTranscodeButton = null;
        transcodeAgainButton = null;
        formatChipGroup = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        transcodeExecutor.shutdownNow();
        super.onDestroy();
    }

    private void initViews(View view) {
        ImageButton backButton = view.findViewById(R.id.btn_back);
        startState = view.findViewById(R.id.transcode_start_state);
        resultState = view.findViewById(R.id.transcode_result_state);
        selectedFileRow = view.findViewById(R.id.transcode_selected_file_row);
        selectedCountText = view.findViewById(R.id.transcode_selected_count);
        selectedFileText = view.findViewById(R.id.transcode_selected_file);
        resultActiveName = view.findViewById(R.id.transcode_result_active_name);
        resultActiveTarget = view.findViewById(R.id.transcode_result_active_target);
        resultActiveProgress = view.findViewById(R.id.transcode_result_active_progress);
        resultActiveBadge = view.findViewById(R.id.transcode_result_active_badge);
        resultActiveBar = view.findViewById(R.id.transcode_result_active_bar);
        selectFileButton = view.findViewById(R.id.btn_select_file);
        startTranscodeButton = view.findViewById(R.id.btn_start_transcode);
        transcodeAgainButton = view.findViewById(R.id.btn_transcode_again);
        formatChipGroup = view.findViewById(R.id.transcode_format_group);
        backButton.setOnClickListener(v -> navigateBack());
        view.findViewById(R.id.btn_remove_selected_file).setOnClickListener(v -> clearSelectedFile());
    }

    private void setupClickListeners() {
        selectFileButton.setOnClickListener(v -> filePickerLauncher.launch(new String[]{"audio/*"}));
        startTranscodeButton.setOnClickListener(v -> onStartTranscodeClicked());
        transcodeAgainButton.setOnClickListener(v -> showStartState());
    }

    private void onFileSelected(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        selectedFileUri = uri;
        selectedFileName = resolveDisplayName(uri);

        selectedFileText.setText(selectedFileName);
        selectedFileRow.setVisibility(View.VISIBLE);
        selectedCountText.setText(getString(R.string.transcode_selected_count, 1));
        startTranscodeButton.setEnabled(true);
    }

    private void onStartTranscodeClicked() {
        if (selectedFileUri == null || selectedFileName == null || selectedFileName.trim().isEmpty() || transcodeInFlight) {
            return;
        }
        transcodeInFlight = true;
        startTranscodeButton.setEnabled(false);
        selectFileButton.setEnabled(false);
        transcodeAgainButton.setEnabled(false);
        TranscodeSettings.OutputFormat outputFormat = resolveSelectedFormat();
        String targetName = buildTargetName(selectedFileName, outputFormat);
        resultActiveName.setText(targetName);
        resultActiveTarget.setText(getString(
                R.string.transcode_target_short,
                outputFormat.getFileExtension().toUpperCase(java.util.Locale.US)));
        resultActiveBadge.setVisibility(View.VISIBLE);
        resultActiveBadge.setText(R.string.transcode_section_in_progress);
        resultActiveProgress.setText(getString(R.string.transcode_progress_processing));
        resultActiveBar.setIndeterminate(true);
        showResultState();
        Uri sourceUri = selectedFileUri;
        transcodeExecutor.execute(() -> {
            try {
                File outputFile = transcodeRepository.transcode(sourceUri, selectedFileName, outputFormat);
                mainHandler.post(() -> onTranscodeSucceeded(outputFile, outputFormat));
            } catch (IOException ioException) {
                mainHandler.post(() -> onTranscodeFailed(ioException));
            }
        });
    }

    private void showStartState() {
        startState.setVisibility(View.VISIBLE);
        resultState.setVisibility(View.GONE);
        if (!transcodeInFlight) {
            clearSelectedFile();
        }
    }

    private void clearSelectedFile() {
        selectedFileUri = null;
        selectedFileName = null;
        selectedFileText.setText(R.string.transcode_no_file_selected);
        selectedCountText.setText(getString(R.string.transcode_selected_count, 0));
        selectedFileRow.setVisibility(View.VISIBLE);
        startTranscodeButton.setEnabled(false);
        selectFileButton.setEnabled(true);
    }

    private void showResultState() {
        startState.setVisibility(View.GONE);
        resultState.setVisibility(View.VISIBLE);
    }

    @NonNull
    private TranscodeSettings.OutputFormat resolveSelectedFormat() {
        int checkedId = formatChipGroup.getCheckedChipId();
        if (checkedId == R.id.chip_format_flac) {
            return TranscodeSettings.OutputFormat.FLAC;
        }
        if (checkedId == R.id.chip_format_ogg) {
            return TranscodeSettings.OutputFormat.OGG;
        }
        if (checkedId == R.id.chip_format_wav) {
            return TranscodeSettings.OutputFormat.WAV;
        }
        return TranscodeSettings.OutputFormat.MP3;
    }

    @NonNull
    private String buildTargetName(@NonNull String sourceName,
                                   @NonNull TranscodeSettings.OutputFormat outputFormat) {
        String targetName = sourceName;
        int dot = sourceName.lastIndexOf('.');
        if (dot > 0) {
            targetName = sourceName.substring(0, dot);
        }
        return targetName + "_converted." + outputFormat.getFileExtension();
    }

    private void onTranscodeSucceeded(@NonNull File outputFile,
                                      @NonNull TranscodeSettings.OutputFormat outputFormat) {
        if (!isAdded()
                || resultActiveName == null
                || resultActiveTarget == null
                || resultActiveProgress == null
                || resultActiveBadge == null
                || resultActiveBar == null
                || transcodeAgainButton == null
                || selectFileButton == null
                || startTranscodeButton == null) {
            return;
        }
        transcodeInFlight = false;
        resultActiveName.setText(outputFile.getName());
        localMusicStore.addSongs(java.util.Collections.singletonList(Uri.fromFile(outputFile)));
        resultActiveTarget.setText(getString(
                R.string.transcode_target_short,
                outputFormat.getFileExtension().toUpperCase(java.util.Locale.US)));
        resultActiveBadge.setVisibility(View.GONE);
        resultActiveProgress.setText(getString(R.string.transcode_progress_done));
        resultActiveBar.setIndeterminate(false);
        resultActiveBar.setProgress(100);
        transcodeAgainButton.setEnabled(true);
        selectFileButton.setEnabled(true);
        startTranscodeButton.setEnabled(false);
        android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.transcode_saved_to, outputFile.getAbsolutePath()),
                android.widget.Toast.LENGTH_LONG).show();
    }

    private void onTranscodeFailed(@NonNull IOException ioException) {
        if (!isAdded()
                || selectFileButton == null
                || startTranscodeButton == null
                || transcodeAgainButton == null) {
            return;
        }
        transcodeInFlight = false;
        showStartState();
        selectFileButton.setEnabled(true);
        startTranscodeButton.setEnabled(selectedFileUri != null);
        transcodeAgainButton.setEnabled(true);
        android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.transcode_failed_message, ioException.getMessage()),
                android.widget.Toast.LENGTH_LONG).show();
    }

    @NonNull
    private String resolveDisplayName(@NonNull Uri uri) {
        try (android.database.Cursor cursor = requireContext().getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (columnIndex >= 0) {
                    String displayName = cursor.getString(columnIndex);
                    if (displayName != null && !displayName.trim().isEmpty()) {
                        return displayName;
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        String lastPathSegment = uri.getLastPathSegment();
        if (lastPathSegment == null || lastPathSegment.trim().isEmpty()) {
            return getString(R.string.transcode_unknown_file);
        }
        int slash = lastPathSegment.lastIndexOf('/');
        return slash >= 0 ? lastPathSegment.substring(slash + 1) : lastPathSegment;
    }

    private void navigateBack() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onBackPressed();
            return;
        }
        requireActivity().finish();
    }
}
