package com.example.flexmusicplayer.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.flexmusicplayer.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

public class TranscodeFragment extends Fragment {

    private View startState;
    private View resultState;
    private View selectedFileRow;
    private TextView selectedCountText;
    private TextView selectedFileText;
    private TextView resultActiveName;
    private TextView resultActiveTarget;
    private TextView resultActiveProgress;
    private MaterialButton selectFileButton;
    private MaterialButton startTranscodeButton;
    private MaterialButton transcodeAgainButton;
    private ChipGroup formatChipGroup;
    private ActivityResultLauncher<String[]> filePickerLauncher;
    private String selectedFileName;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        selectFileButton = view.findViewById(R.id.btn_select_file);
        startTranscodeButton = view.findViewById(R.id.btn_start_transcode);
        transcodeAgainButton = view.findViewById(R.id.btn_transcode_again);
        formatChipGroup = view.findViewById(R.id.transcode_format_group);
        backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
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

        String path = uri.getLastPathSegment();
        if (path == null || path.trim().isEmpty()) {
            selectedFileName = getString(R.string.transcode_unknown_file);
        } else {
            int slash = path.lastIndexOf('/');
            selectedFileName = slash >= 0 ? path.substring(slash + 1) : path;
        }

        selectedFileText.setText(selectedFileName);
        selectedFileRow.setVisibility(View.VISIBLE);
        selectedCountText.setText(getString(R.string.transcode_selected_count, 1));
        startTranscodeButton.setEnabled(true);
    }

    private void onStartTranscodeClicked() {
        if (selectedFileName == null || selectedFileName.trim().isEmpty()) {
            return;
        }

        String selectedFormat = resolveSelectedFormat();
        String targetName = selectedFileName;
        int dot = selectedFileName.lastIndexOf('.');
        if (dot > 0) {
            targetName = selectedFileName.substring(0, dot);
        }
        targetName = targetName + "_converted." + selectedFormat.toLowerCase();

        resultActiveName.setText(targetName);
        resultActiveTarget.setText(getString(R.string.transcode_target_short, selectedFormat.toUpperCase()));
        resultActiveProgress.setText("74%");
        showResultState();
    }

    private void showStartState() {
        startState.setVisibility(View.VISIBLE);
        resultState.setVisibility(View.GONE);
        clearSelectedFile();
    }

    private void clearSelectedFile() {
        selectedFileName = null;
        selectedFileText.setText(R.string.transcode_no_file_selected);
        selectedCountText.setText(getString(R.string.transcode_selected_count, 0));
        selectedFileRow.setVisibility(View.VISIBLE);
        startTranscodeButton.setEnabled(false);
    }

    private void showResultState() {
        startState.setVisibility(View.GONE);
        resultState.setVisibility(View.VISIBLE);
    }

    private String resolveSelectedFormat() {
        int checkedId = formatChipGroup.getCheckedChipId();
        if (checkedId == R.id.chip_format_flac) {
            return "flac";
        }
        if (checkedId == R.id.chip_format_aac) {
            return "aac";
        }
        if (checkedId == R.id.chip_format_wav) {
            return "wav";
        }
        return "mp3";
    }
}
