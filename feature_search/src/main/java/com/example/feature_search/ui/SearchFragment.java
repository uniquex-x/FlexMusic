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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private TextView statusView;
    private LinearLayout suggestionContainer;
    private ListView resultListView;
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
        statusView = rootView.findViewById(R.id.search_status);
        suggestionContainer = rootView.findViewById(R.id.search_suggestion_container);
        resultListView = rootView.findViewById(R.id.search_result_list);
        resultAdapter = new SearchResultAdapter(requireContext());
        resultListView.setAdapter(resultAdapter);
    }

    private void bindListeners(@NonNull View rootView) {
        TextView backButton = rootView.findViewById(R.id.search_back_action);
        Button searchButton = rootView.findViewById(R.id.search_action);
        backButton.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        searchButton.setOnClickListener(v -> submitSearch(searchInput.getText().toString()));
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
        resultListView.setOnItemClickListener((parent, view, position, id) -> {
            SearchTrack track = resultAdapter.getItem(position);
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
        String trimmedKeyword = keyword.trim();
        if (TextUtils.isEmpty(trimmedKeyword)) {
            resultAdapter.submitTracks(Collections.emptyList());
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(R.string.feature_search_status_empty_query);
            loadSuggestions("");
            return;
        }
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
        if (resultPage.getTracks().isEmpty()) {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(getString(R.string.feature_search_status_no_result, keyword));
            return;
        }
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(getString(R.string.feature_search_status_result_count, resultPage.getTracks().size()));
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
            TextView emptyView = new TextView(requireContext());
            emptyView.setText(R.string.feature_search_history_empty);
            emptyView.setTextColor(0xFF8B88A1);
            suggestionContainer.addView(emptyView);
            return;
        }
        for (String suggestion : suggestions) {
            Button button = new Button(requireContext());
            button.setAllCaps(false);
            button.setText(suggestion);
            button.setTextSize(13f);
            button.setBackgroundResource(android.R.drawable.btn_default_small);
            button.setOnClickListener(v -> {
                searchInput.setText(suggestion);
                searchInput.setSelection(suggestion.length());
                submitSearch(suggestion);
            });
            suggestionContainer.addView(button);
        }
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
        resultListView = null;
        resultAdapter = null;
        searchInput = null;
        statusView = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        searchHost = null;
        super.onDetach();
    }
}
