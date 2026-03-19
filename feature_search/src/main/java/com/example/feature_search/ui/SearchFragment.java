package com.example.feature_search.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.core_data.search.OnlineSearchRepository;
import com.example.core_data.search.SearchHistoryStore;
import com.example.core_data.search.SearchSuggestionRepository;
import com.example.core_data.search.TrackPlaybackRepository;
import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.search.GetSearchSuggestionsUseCase;
import com.example.core_domain.search.PlayTrackFromSearchUseCase;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchTrack;
import com.example.core_domain.search.SearchUseCase;
import com.example.feature_search.R;
import com.example.feature_search.ISearchHost;
import com.example.feature_search.action.SearchPlaybackCoordinator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment {

    private ISearchHost searchHost;
    private ExecutorService executorService;
    private Handler mainHandler;
    private SearchViewModel searchViewModel;
    private SearchPlaybackCoordinator searchPlaybackCoordinator;

    private EditText searchInput;
    private TextView resultCountView;
    private TextView statusView;
    private TextView featuredTitleView;
    private TextView featuredSubtitleView;
    private ChipGroup suggestionContainer;
    private LinearLayout resultContainer;
    private SearchResultAdapter resultAdapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof ISearchHost)) {
            throw new IllegalStateException("Host activity must implement ISearchHost");
        }
        searchHost = (ISearchHost) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_search_screen, container, false);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        searchViewModel = new SearchViewModel(
                new SearchUseCase(new OnlineSearchRepository()),
                new GetSearchSuggestionsUseCase(
                        new SearchSuggestionRepository(new SearchHistoryStore(requireContext()))));
        searchPlaybackCoordinator = new SearchPlaybackCoordinator(
                new PlayTrackFromSearchUseCase(new TrackPlaybackRepository()),
                executorService,
                mainHandler);
        initViews(rootView);
        bindListeners(rootView);
        loadSuggestions("");
        return rootView;
    }

    private void initViews(@NonNull View rootView) {
        searchInput = rootView.findViewById(R.id.search_input);
        resultCountView = rootView.findViewById(R.id.search_result_count);
        statusView = rootView.findViewById(R.id.search_status);
        suggestionContainer = rootView.findViewById(R.id.search_suggestion_container);
        resultContainer = rootView.findViewById(R.id.search_result_container);
        featuredTitleView = rootView.findViewById(R.id.search_featured_title);
        featuredSubtitleView = rootView.findViewById(R.id.search_featured_subtitle);
        resultAdapter = new SearchResultAdapter(requireContext());
    }

    private void bindListeners(@NonNull View rootView) {
        ImageButton backButton = rootView.findViewById(R.id.search_back_action);
        MaterialButton searchButton = rootView.findViewById(R.id.search_action);
        TextView clearHistoryButton = rootView.findViewById(R.id.search_clear_history_action);
        MaterialButton featuredPlayButton = rootView.findViewById(R.id.search_featured_play_action);
        backButton.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        searchButton.setOnClickListener(v -> submitSearch(searchInput.getText().toString()));
        clearHistoryButton.setOnClickListener(v -> {
            searchViewModel.clearHistory();
            renderSuggestions(Collections.emptyList());
            loadSuggestions("");
        });
        featuredPlayButton.setOnClickListener(v -> {
            if (resultAdapter.getCount() > 0) {
                SearchTrack track = resultAdapter.getItem(0);
                searchInput.setText(track.getTitle());
                searchInput.setSelection(track.getTitle().length());
                playTrack(track);
            }
        });
        searchInput.setOnEditorActionListener(this::handleEditorAction);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                loadSuggestions(editable == null ? "" : editable.toString());
            }
        });
    }

    private boolean handleEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        boolean isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
                || actionId == EditorInfo.IME_ACTION_DONE
                || (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && keyEvent.getAction() == KeyEvent.ACTION_DOWN);
        if (isSearchAction) {
            submitSearch(textView.getText().toString());
            return true;
        }
        return false;
    }

    private void submitSearch(@NonNull String keyword) {
        if (resultCountView == null || statusView == null) {
            return;
        }
        String trimmedKeyword = keyword.trim();
        if (TextUtils.isEmpty(trimmedKeyword)) {
            resultAdapter.submitTracks(Collections.emptyList());
            renderResultCards();
            resultCountView.setText(R.string.feature_search_status_idle);
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(R.string.feature_search_status_empty_query);
            updateFeaturedCard(null);
            loadSuggestions("");
            return;
        }
        resultCountView.setText(R.string.feature_search_status_loading);
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(R.string.feature_search_status_loading);
        final Handler handler = mainHandler;
        final SearchViewModel viewModel = searchViewModel;
        if (handler == null || viewModel == null) {
            return;
        }
        executorService.execute(() -> {
            try {
                SearchResultPage resultPage = viewModel.search(trimmedKeyword);
                List<String> history = viewModel.loadHistory();
                handler.post(() -> renderSearchResult(trimmedKeyword, resultPage, history));
            } catch (IOException ioException) {
                handler.post(() -> {
                    if (!isAdded() || resultAdapter == null || statusView == null) {
                        return;
                    }
                    resultAdapter.submitTracks(Collections.emptyList());
                    renderResultCards();
                    resultCountView.setText(R.string.feature_search_status_idle);
                    updateFeaturedCard(null);
                    statusView.setVisibility(View.VISIBLE);
                    statusView.setText(getString(R.string.feature_search_status_error, ioException.getMessage()));
                });
            }
        });
    }

    private void renderSearchResult(@NonNull String keyword,
                                    @NonNull SearchResultPage resultPage,
                                    @NonNull List<String> history) {
        if (!isAdded() || resultAdapter == null || statusView == null) {
            return;
        }
        renderSuggestions(history);
        resultAdapter.submitTracks(resultPage.getTracks());
        renderResultCards();
        updateFeaturedCard(resultPage);
        if (resultPage.getTracks().isEmpty()) {
            resultCountView.setText(R.string.feature_search_status_idle);
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(getString(R.string.feature_search_status_no_result, keyword));
            return;
        }
        resultCountView.setText(getString(R.string.feature_search_status_result_count, resultPage.getTotalCount()));
        statusView.setVisibility(View.GONE);
    }

    private void loadSuggestions(@NonNull String keyword) {
        if (executorService == null || searchViewModel == null || mainHandler == null) {
            return;
        }
        final Handler handler = mainHandler;
        final SearchViewModel viewModel = searchViewModel;
        executorService.execute(() -> {
            try {
                List<String> suggestions = viewModel.loadSuggestions(keyword);
                handler.post(() -> renderSuggestions(suggestions));
            } catch (IOException ignored) {
            }
        });
    }

    private void renderSuggestions(@NonNull List<String> suggestions) {
        if (!isAdded() || suggestionContainer == null) {
            return;
        }
        suggestionContainer.removeAllViews();
        if (suggestions.isEmpty()) {
            return;
        }
        for (String suggestion : suggestions) {
            Chip chip = new Chip(requireContext());
            chip.setText(suggestion);
            chip.setTextSize(13f);
            chip.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setChipBackgroundColorResource(android.R.color.white);
            chip.setChipCornerRadius(22f);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setCloseIconVisible(true);
            chip.setCloseIconResource(R.drawable.ic_feature_search_close);
            chip.setCloseIconTintResource(android.R.color.darker_gray);
            chip.setOnClickListener(v -> {
                searchInput.setText(suggestion);
                searchInput.setSelection(suggestion.length());
                submitSearch(suggestion);
            });
            chip.setOnCloseIconClickListener(v -> {
                searchInput.setText(suggestion);
                searchInput.setSelection(suggestion.length());
            });
            suggestionContainer.addView(chip);
        }
    }

    private void renderResultCards() {
        if (!isAdded() || resultContainer == null || resultAdapter == null) {
            return;
        }
        resultContainer.removeAllViews();
        for (int index = 0; index < resultAdapter.getCount(); index++) {
            View itemView = resultAdapter.getView(index, null, resultContainer);
            final SearchTrack track = resultAdapter.getItem(index);
            itemView.setOnClickListener(v -> playTrack(track));
            resultContainer.addView(itemView);
        }
    }

    private void playTrack(@NonNull SearchTrack track) {
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(R.string.feature_search_status_resolving);
        searchPlaybackCoordinator.play(track, new SearchPlaybackCoordinator.IListener() {
            @Override
            public void onPlaybackResolved(@NonNull SearchTrack resolvedTrack,
                                           @NonNull PlaybackRequest playbackRequest) {
                if (!isAdded()) {
                    return;
                }
                statusView.setText(R.string.feature_search_status_playing);
                searchHost.onSearchPlaybackRequested(resolvedTrack, playbackRequest);
            }

            @Override
            public void onPlaybackResolveFailed(@NonNull SearchTrack failedTrack,
                                                @NonNull IOException exception) {
                if (!isAdded()) {
                    return;
                }
                statusView.setVisibility(View.VISIBLE);
                statusView.setText(getString(R.string.feature_search_status_playback_failed, exception.getMessage()));
            }
        });
    }

    private void updateFeaturedCard(@Nullable SearchResultPage resultPage) {
        if (featuredTitleView == null || featuredSubtitleView == null) {
            return;
        }
        if (resultPage == null || resultPage.getTracks().isEmpty()) {
            featuredTitleView.setText(R.string.feature_search_featured_title);
            featuredSubtitleView.setText(R.string.feature_search_featured_subtitle);
            return;
        }
        SearchTrack topTrack = resultPage.getTracks().get(0);
        String featuredTitle = topTrack.getAlbumName().isEmpty() ? topTrack.getTitle() : topTrack.getAlbumName();
        featuredTitleView.setText(featuredTitle);
        String subtitle = topTrack.getArtistNames().isEmpty()
                ? getString(R.string.feature_search_featured_subtitle)
                : topTrack.getArtistNames().get(0) + " · " + formatYearAndType(topTrack);
        featuredSubtitleView.setText(subtitle);
    }

    @NonNull
    private String formatYearAndType(@NonNull SearchTrack track) {
        if (track.getDurationMs() > 0L) {
            return "Single";
        }
        return "Featured";
    }

    @Override
    public void onDestroyView() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        mainHandler = null;
        searchViewModel = null;
        searchPlaybackCoordinator = null;
        suggestionContainer = null;
        resultContainer = null;
        resultAdapter = null;
        searchInput = null;
        resultCountView = null;
        featuredTitleView = null;
        featuredSubtitleView = null;
        statusView = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        searchHost = null;
        super.onDetach();
    }
}
