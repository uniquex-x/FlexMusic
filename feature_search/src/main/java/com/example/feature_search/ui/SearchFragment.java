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
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
import com.example.core_domain.search.SearchAlbum;
import com.example.core_domain.search.SearchArtist;
import com.example.core_domain.search.SearchFilter;
import com.example.core_domain.search.SearchPlaylist;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchScope;
import com.example.core_domain.search.SearchTrack;
import com.example.core_domain.search.SearchUseCase;
import com.example.feature_search.ISearchHost;
import com.example.feature_search.R;
import com.example.feature_search.action.SearchPlaybackCoordinator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment {

    private static final String ARG_SPOTIFY_SEARCH_ENABLED = "spotify_search_enabled";
    private static final int MENU_ACTION_PLAY_NEXT = 1;
    private static final int MENU_ACTION_ADD_TO_QUEUE = 2;
    private static final long SUGGESTION_DEBOUNCE_MS = 220L;

    private ISearchHost searchHost;
    private ExecutorService searchExecutorService;
    private ExecutorService suggestionExecutorService;
    private Handler mainHandler;
    private SearchViewModel searchViewModel;
    private SearchPlaybackCoordinator searchPlaybackCoordinator;

    private EditText searchInput;
    private TextView historyLabelView;
    private TextView hotLabelView;
    private TextView clearHistoryView;
    private TextView resultCountView;
    private TextView statusView;
    private TextView featuredTitleView;
    private TextView featuredSubtitleView;
    private TextView playAllView;
    private ChipGroup suggestionContainer;
    private ChipGroup hotContainer;
    private ChipGroup scopeContainer;
    private LinearLayout resultContainer;
    private MaterialButton loadMoreButton;
    private SearchResultAdapter resultAdapter;

    private SearchScope selectedScope = SearchScope.TRACKS;
    private String currentKeyword = "";
    private SearchResultPage currentResultPage;
    private int suggestionRequestVersion = 0;
    private int searchRequestVersion = 0;
    private int landingContentRequestVersion = 0;
    private boolean hotSearchesLoaded = false;
    @NonNull
    private List<String> cachedHistory = Collections.emptyList();
    @NonNull
    private List<String> cachedHotSearches = Collections.emptyList();
    @Nullable
    private Runnable suggestionDebounceRunnable;

    @NonNull
    public static SearchFragment newInstance(boolean spotifySearchEnabled) {
        SearchFragment fragment = new SearchFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_SPOTIFY_SEARCH_ENABLED, spotifySearchEnabled);
        fragment.setArguments(args);
        return fragment;
    }

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
        searchExecutorService = Executors.newSingleThreadExecutor();
        suggestionExecutorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        searchViewModel = new SearchViewModel(
                new SearchUseCase(new OnlineSearchRepository(isSpotifySearchEnabled())),
                new GetSearchSuggestionsUseCase(
                        new SearchSuggestionRepository(new SearchHistoryStore(requireContext()))));
        searchPlaybackCoordinator = new SearchPlaybackCoordinator(
                new PlayTrackFromSearchUseCase(new TrackPlaybackRepository()),
                searchExecutorService,
                mainHandler);
        initViews(rootView);
        bindListeners(rootView);
        configureScopeChips();
        loadLandingContent(false);
        return rootView;
    }

    private void initViews(@NonNull View rootView) {
        searchInput = rootView.findViewById(R.id.search_input);
        historyLabelView = rootView.findViewById(R.id.search_history_label);
        hotLabelView = rootView.findViewById(R.id.search_hot_label);
        clearHistoryView = rootView.findViewById(R.id.search_clear_history_action);
        resultCountView = rootView.findViewById(R.id.search_result_count);
        statusView = rootView.findViewById(R.id.search_status);
        suggestionContainer = rootView.findViewById(R.id.search_suggestion_container);
        hotContainer = rootView.findViewById(R.id.search_hot_container);
        scopeContainer = rootView.findViewById(R.id.search_scope_container);
        resultContainer = rootView.findViewById(R.id.search_result_container);
        featuredTitleView = rootView.findViewById(R.id.search_featured_title);
        featuredSubtitleView = rootView.findViewById(R.id.search_featured_subtitle);
        loadMoreButton = rootView.findViewById(R.id.search_load_more_action);
        playAllView = rootView.findViewById(R.id.search_play_all_action);
        resultAdapter = new SearchResultAdapter(requireContext());
        resultAdapter.setTrackActionListener(new SearchResultAdapter.ITrackActionListener() {
            @Override
            public void onTrackClicked(@NonNull SearchTrack track) {
                playTrack(track);
            }

            @Override
            public void onTrackMoreClicked(@NonNull View anchorView, @NonNull SearchTrack track) {
                showTrackActionMenu(anchorView, track);
            }
        });
    }

    private void bindListeners(@NonNull View rootView) {
        ImageButton backButton = rootView.findViewById(R.id.search_back_action);
        MaterialButton searchButton = rootView.findViewById(R.id.search_action);
        MaterialButton featuredPlayButton = rootView.findViewById(R.id.search_featured_play_action);
        backButton.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        searchButton.setOnClickListener(v -> submitSearch(searchInput.getText().toString()));
        clearHistoryView.setOnClickListener(v -> {
            searchViewModel.clearHistory();
            cachedHistory = Collections.emptyList();
            loadLandingContent(false);
        });
        featuredPlayButton.setOnClickListener(v -> {
            if (currentResultPage != null && !currentResultPage.getTracks().isEmpty()) {
                playTrack(currentResultPage.getTracks().get(0));
            }
        });
        playAllView.setOnClickListener(v -> playAll());
        loadMoreButton.setOnClickListener(v -> loadMore());
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
                String keyword = editable == null ? "" : editable.toString().trim();
                if (keyword.isEmpty()) {
                    searchRequestVersion++;
                    cancelPendingSuggestionWork();
                    showLandingSuggestionState();
                    loadLandingContent(false);
                    return;
                }
                scheduleSuggestions(keyword);
            }
        });
    }

    private void configureScopeChips() {
        if (scopeContainer == null || !isAdded()) {
            return;
        }
        scopeContainer.removeAllViews();
        addScopeChip(SearchScope.ALL, R.string.feature_search_scope_all, false);
        addScopeChip(SearchScope.TRACKS, R.string.feature_search_scope_tracks, true);
        addScopeChip(SearchScope.ALBUMS, R.string.feature_search_scope_albums, false);
        addScopeChip(SearchScope.ARTISTS, R.string.feature_search_scope_artists, false);
        addScopeChip(SearchScope.PLAYLISTS, R.string.feature_search_scope_playlists, false);
        scopeContainer.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            View checkedChip = group.findViewById(checkedId);
            if (!(checkedChip instanceof Chip)) {
                return;
            }
            Object tag = checkedChip.getTag();
            if (!(tag instanceof SearchScope)) {
                return;
            }
            selectedScope = (SearchScope) tag;
            if (!TextUtils.isEmpty(currentKeyword)) {
                submitSearch(currentKeyword);
            }
        });
    }

    private void addScopeChip(@NonNull SearchScope scope, int labelRes, boolean checked) {
        Chip chip = new Chip(requireContext());
        chip.setId(View.generateViewId());
        chip.setTag(scope);
        chip.setText(labelRes);
        chip.setCheckable(true);
        chip.setCheckedIconVisible(false);
        chip.setChipCornerRadius(20f);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipBackgroundColorResource(android.R.color.white);
        chip.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
        chip.setChecked(checked);
        scopeContainer.addView(chip);
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
            searchRequestVersion++;
            clearResults();
            showLandingSuggestionState();
            loadLandingContent(false);
            return;
        }
        cancelPendingSuggestionWork();
        int requestVersion = ++searchRequestVersion;
        landingContentRequestVersion++;
        currentKeyword = trimmedKeyword;
        if (resultCountView != null) {
            resultCountView.setText(R.string.feature_search_status_loading);
        }
        showStatus(getString(R.string.feature_search_status_loading));
        final SearchViewModel viewModel = searchViewModel;
        final Handler handler = mainHandler;
        if (viewModel == null || handler == null) {
            return;
        }
        searchExecutorService.execute(() -> {
            try {
                SearchResultPage resultPage = viewModel.search(trimmedKeyword, selectedScope, SearchFilter.DEFAULT_PAGE);
                List<String> history = viewModel.loadHistory();
                handler.post(() -> renderSearchResult(requestVersion, trimmedKeyword, resultPage, history));
            } catch (IOException ioException) {
                handler.post(() -> renderSearchError(requestVersion, trimmedKeyword, ioException));
            }
        });
    }

    private void loadMore() {
        if (currentResultPage == null || !currentResultPage.isHasMore() || TextUtils.isEmpty(currentKeyword)) {
            return;
        }
        showStatus(getString(R.string.feature_search_status_loading_more));
        loadMoreButton.setVisibility(View.GONE);
        final SearchViewModel viewModel = searchViewModel;
        final Handler handler = mainHandler;
        final int nextPage = currentResultPage.getFilter().getPage() + 1;
        if (viewModel == null || handler == null) {
            return;
        }
        searchExecutorService.execute(() -> {
            try {
                SearchResultPage nextPageResult = viewModel.search(currentKeyword, selectedScope, nextPage);
                handler.post(() -> appendSearchResult(nextPageResult));
            } catch (IOException ioException) {
                handler.post(() -> showStatus(getString(R.string.feature_search_status_error, ioException.getMessage())));
            }
        });
    }

    private void scheduleSuggestions(@NonNull String keyword) {
        cancelPendingSuggestionWork();
        if (mainHandler == null) {
            return;
        }
        final int requestVersion = ++suggestionRequestVersion;
        suggestionDebounceRunnable = () -> loadSuggestions(keyword, requestVersion);
        mainHandler.postDelayed(suggestionDebounceRunnable, SUGGESTION_DEBOUNCE_MS);
    }

    private void loadSuggestions(@NonNull String keyword, int requestVersion) {
        final SearchViewModel viewModel = searchViewModel;
        final Handler handler = mainHandler;
        if (viewModel == null || handler == null || suggestionExecutorService == null) {
            return;
        }
        suggestionExecutorService.execute(() -> {
            try {
                List<String> suggestions = viewModel.loadSuggestions(keyword);
                handler.post(() -> {
                    if (requestVersion != suggestionRequestVersion) {
                        return;
                    }
                    renderSuggestionsOnly(suggestions);
                });
            } catch (IOException ignored) {
            }
        });
    }

    private void loadLandingContent(boolean forceRefreshHotSearches) {
        final SearchViewModel viewModel = searchViewModel;
        final Handler handler = mainHandler;
        if (viewModel == null || handler == null || suggestionExecutorService == null) {
            return;
        }
        final int requestVersion = ++landingContentRequestVersion;
        if (hotSearchesLoaded && !forceRefreshHotSearches) {
            if (!isSearchInputEmpty()) {
                return;
            }
            renderLandingSuggestions(cachedHistory, cachedHotSearches);
            return;
        }
        suggestionExecutorService.execute(() -> {
            List<String> history = viewModel.loadHistory();
            List<String> hotSearches;
            try {
                hotSearches = forceRefreshHotSearches || !hotSearchesLoaded
                        ? viewModel.loadHotSearches()
                        : cachedHotSearches;
            } catch (IOException ignored) {
                hotSearches = cachedHotSearches;
            }
            cachedHistory = history;
            cachedHotSearches = hotSearches;
            hotSearchesLoaded = true;
            List<String> finalHotSearches = hotSearches;
            handler.post(() -> {
                if (requestVersion != landingContentRequestVersion || !isSearchInputEmpty()) {
                    return;
                }
                renderLandingSuggestions(history, finalHotSearches);
            });
        });
    }

    private void renderSearchResult(int requestVersion,
                                    @NonNull String keyword,
                                    @NonNull SearchResultPage resultPage,
                                    @NonNull List<String> history) {
        if (!isAdded()) {
            return;
        }
        if (requestVersion != searchRequestVersion || !keyword.equals(getCurrentInputKeyword())) {
            return;
        }
        currentKeyword = keyword;
        currentResultPage = resultPage;
        cachedHistory = history;
        renderHistoryOnly(history);
        renderResultSections(resultPage);
        updateFeaturedCard(resultPage);
        if (resultPage.getTracks().isEmpty()
                && resultPage.getAlbums().isEmpty()
                && resultPage.getArtists().isEmpty()
                && resultPage.getPlaylists().isEmpty()) {
            resultCountView.setText(R.string.feature_search_status_idle);
            playAllView.setVisibility(View.GONE);
            loadMoreButton.setVisibility(View.GONE);
            showStatus(getString(R.string.feature_search_status_no_result, keyword));
            return;
        }
        resultCountView.setText(getString(R.string.feature_search_status_result_count, resultPage.getTotalCount()));
        playAllView.setVisibility(resultPage.getTracks().isEmpty() ? View.GONE : View.VISIBLE);
        loadMoreButton.setVisibility(resultPage.isHasMore() ? View.VISIBLE : View.GONE);
        hideStatus();
    }

    private void appendSearchResult(@NonNull SearchResultPage nextPage) {
        if (!isAdded()) {
            return;
        }
        currentResultPage = mergePages(currentResultPage, nextPage);
        renderResultSections(currentResultPage);
        updateFeaturedCard(currentResultPage);
        resultCountView.setText(getString(R.string.feature_search_status_result_count, currentResultPage.getTotalCount()));
        playAllView.setVisibility(currentResultPage.getTracks().isEmpty() ? View.GONE : View.VISIBLE);
        loadMoreButton.setVisibility(currentResultPage.isHasMore() ? View.VISIBLE : View.GONE);
        hideStatus();
    }

    private void renderSearchError(int requestVersion,
                                   @NonNull String keyword,
                                   @NonNull IOException ioException) {
        if (!isAdded()) {
            return;
        }
        if (requestVersion != searchRequestVersion || !keyword.equals(getCurrentInputKeyword())) {
            return;
        }
        clearResults();
        showStatus(getString(R.string.feature_search_status_error, ioException.getMessage()));
    }

    private void renderSuggestionsOnly(@NonNull List<String> suggestions) {
        if (!isAdded()) {
            return;
        }
        historyLabelView.setText(R.string.feature_search_suggestions_title);
        clearHistoryView.setVisibility(View.GONE);
        hotLabelView.setVisibility(View.GONE);
        hotContainer.setVisibility(View.GONE);
        renderChipGroup(suggestionContainer, suggestions, false);
    }

    private void renderLandingSuggestions(@NonNull List<String> history, @NonNull List<String> hotSearches) {
        if (!isAdded()) {
            return;
        }
        showLandingSuggestionState();
        renderChipGroup(suggestionContainer, history, false);
        renderChipGroup(hotContainer, hotSearches, false);
        clearHistoryView.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
        hotLabelView.setVisibility(hotSearches.isEmpty() ? View.GONE : View.VISIBLE);
        hotContainer.setVisibility(hotSearches.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderHistoryOnly(@NonNull List<String> history) {
        if (!isAdded()) {
            return;
        }
        cachedHistory = history;
        showLandingSuggestionState();
        renderChipGroup(suggestionContainer, history, false);
        clearHistoryView.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
        hotLabelView.setVisibility(View.GONE);
        hotContainer.setVisibility(View.GONE);
    }

    private void showLandingSuggestionState() {
        historyLabelView.setText(R.string.feature_search_history_title);
    }

    private void renderChipGroup(@NonNull ChipGroup chipGroup,
                                 @NonNull List<String> values,
                                 boolean closeIconVisible) {
        chipGroup.removeAllViews();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) {
                continue;
            }
            Chip chip = new Chip(requireContext());
            chip.setText(value);
            chip.setTextSize(13f);
            chip.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setChipBackgroundColorResource(android.R.color.white);
            chip.setChipCornerRadius(22f);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setCloseIconVisible(closeIconVisible);
            chip.setCloseIconResource(R.drawable.ic_feature_search_close);
            chip.setCloseIconTintResource(android.R.color.darker_gray);
            chip.setOnClickListener(v -> {
                searchInput.setText(value);
                searchInput.setSelection(value.length());
                submitSearch(value);
            });
            if (closeIconVisible) {
                chip.setOnCloseIconClickListener(v -> {
                    searchInput.setText(value);
                    searchInput.setSelection(value.length());
                });
            }
            chipGroup.addView(chip);
        }
    }

    private void renderResultSections(@NonNull SearchResultPage resultPage) {
        if (!isAdded()) {
            return;
        }
        resultContainer.removeAllViews();
        resultAdapter.submitTracks(resultPage.getTracks());

        if (!resultPage.getTracks().isEmpty()) {
            addSectionHeader(getString(R.string.feature_search_section_tracks), resultPage.getTracks().size());
            renderTrackCards();
        }
        if (!resultPage.getAlbums().isEmpty()) {
            addSectionHeader(getString(R.string.feature_search_section_albums), resultPage.getAlbums().size());
            for (SearchAlbum album : resultPage.getAlbums()) {
                resultContainer.addView(createAlbumView(album));
            }
        }
        if (!resultPage.getArtists().isEmpty()) {
            addSectionHeader(getString(R.string.feature_search_section_artists), resultPage.getArtists().size());
            for (SearchArtist artist : resultPage.getArtists()) {
                resultContainer.addView(createArtistView(artist));
            }
        }
        if (!resultPage.getPlaylists().isEmpty()) {
            addSectionHeader(getString(R.string.feature_search_section_playlists), resultPage.getPlaylists().size());
            for (SearchPlaylist playlist : resultPage.getPlaylists()) {
                resultContainer.addView(createPlaylistView(playlist));
            }
        }
    }

    private void addSectionHeader(@NonNull String title, int count) {
        TextView sectionHeader = new TextView(requireContext());
        sectionHeader.setText(getString(R.string.feature_search_section_count, title, count));
        sectionHeader.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
        sectionHeader.setTextSize(12f);
        sectionHeader.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.topMargin = resultContainer.getChildCount() == 0 ? 0 : dpToPx(20);
        sectionHeader.setLayoutParams(layoutParams);
        resultContainer.addView(sectionHeader);
    }

    private void renderTrackCards() {
        for (int index = 0; index < resultAdapter.getCount(); index++) {
            View itemView = resultAdapter.getView(index, null, resultContainer);
            resultContainer.addView(itemView);
        }
    }

    @NonNull
    private View createAlbumView(@NonNull SearchAlbum album) {
        View itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_search_entity, resultContainer, false);
        bindEntityView(
                itemView,
                album.getTitle(),
                album.getArtistNames().isEmpty() ? getString(R.string.feature_search_entity_action_refine)
                        : TextUtils.join(" / ", album.getArtistNames()),
                getString(R.string.feature_search_entity_meta_tracks, album.getTrackCount()),
                R.drawable.bg_feature_search_track_art_b,
                v -> refineSearch(album.getTitle()));
        return itemView;
    }

    @NonNull
    private View createArtistView(@NonNull SearchArtist artist) {
        View itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_search_entity, resultContainer, false);
        bindEntityView(
                itemView,
                artist.getName(),
                getString(R.string.feature_search_entity_action_refine),
                getString(R.string.feature_search_scope_tracks),
                R.drawable.bg_feature_search_track_art_c,
                v -> refineSearch(artist.getName()));
        return itemView;
    }

    @NonNull
    private View createPlaylistView(@NonNull SearchPlaylist playlist) {
        View itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_search_entity, resultContainer, false);
        bindEntityView(
                itemView,
                playlist.getTitle(),
                TextUtils.isEmpty(playlist.getCreatorName())
                        ? getString(R.string.feature_search_entity_action_refine)
                        : getString(R.string.feature_search_entity_meta_creator, playlist.getCreatorName()),
                getString(R.string.feature_search_entity_meta_tracks, playlist.getTrackCount()),
                R.drawable.bg_feature_search_track_art_a,
                v -> refineSearch(playlist.getTitle()));
        return itemView;
    }

    private void bindEntityView(@NonNull View itemView,
                                @NonNull String title,
                                @NonNull String subtitle,
                                @NonNull String meta,
                                int backgroundRes,
                                @NonNull View.OnClickListener onClickListener) {
        View artFrame = itemView.findViewById(R.id.search_entity_art_frame);
        TextView titleView = itemView.findViewById(R.id.search_entity_title);
        TextView subtitleView = itemView.findViewById(R.id.search_entity_subtitle);
        TextView metaView = itemView.findViewById(R.id.search_entity_meta);
        artFrame.setBackgroundResource(backgroundRes);
        titleView.setText(title);
        subtitleView.setText(subtitle);
        metaView.setText(meta);
        itemView.setOnClickListener(onClickListener);
    }

    private void refineSearch(@NonNull String keyword) {
        searchInput.setText(keyword);
        searchInput.setSelection(keyword.length());
        boolean scopeChanged = selectedScope != SearchScope.TRACKS;
        checkScope(SearchScope.TRACKS);
        if (!scopeChanged) {
            submitSearch(keyword);
        }
    }

    private void checkScope(@NonNull SearchScope scope) {
        selectedScope = scope;
        for (int index = 0; index < scopeContainer.getChildCount(); index++) {
            View child = scopeContainer.getChildAt(index);
            if (!(child instanceof Chip)) {
                continue;
            }
            Object tag = child.getTag();
            ((Chip) child).setChecked(tag == scope);
        }
    }

    private void showTrackActionMenu(@NonNull View anchorView, @NonNull SearchTrack track) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchorView);
        Menu menu = popupMenu.getMenu();
        menu.add(Menu.NONE, MENU_ACTION_PLAY_NEXT, Menu.NONE, R.string.feature_search_track_action_next);
        menu.add(Menu.NONE, MENU_ACTION_ADD_TO_QUEUE, Menu.NONE, R.string.feature_search_track_action_queue);
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_ACTION_PLAY_NEXT) {
                enqueueTrack(track, true);
                return true;
            }
            if (item.getItemId() == MENU_ACTION_ADD_TO_QUEUE) {
                enqueueTrack(track, false);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void enqueueTrack(@NonNull SearchTrack track, boolean playNext) {
        showStatus(getString(R.string.feature_search_status_resolving));
        searchPlaybackCoordinator.play(track, new SearchPlaybackCoordinator.IListener() {
            @Override
            public void onPlaybackResolved(@NonNull SearchTrack resolvedTrack,
                                           @NonNull PlaybackRequest playbackRequest) {
                if (!isAdded()) {
                    return;
                }
                if (playNext) {
                    searchHost.onSearchPlayNextRequested(resolvedTrack, playbackRequest);
                    showStatus(getString(R.string.feature_search_status_added_next));
                } else {
                    searchHost.onSearchAddToQueueRequested(resolvedTrack, playbackRequest);
                    showStatus(getString(R.string.feature_search_status_added_queue));
                }
            }

            @Override
            public void onPlaybackResolveFailed(@NonNull SearchTrack failedTrack,
                                                @NonNull IOException exception) {
                if (!isAdded()) {
                    return;
                }
                showStatus(getString(R.string.feature_search_status_playback_failed, exception.getMessage()));
            }
        });
    }

    private void playTrack(@NonNull SearchTrack track) {
        SearchResultPage playbackPage = buildPlaybackPage(track);
        int startIndex = findTrackIndex(playbackPage.getTracks(), track);
        showStatus(getString(R.string.feature_search_status_resolving));
        searchPlaybackCoordinator.play(track, new SearchPlaybackCoordinator.IListener() {
            @Override
            public void onPlaybackResolved(@NonNull SearchTrack resolvedTrack,
                                           @NonNull PlaybackRequest playbackRequest) {
                if (!isAdded()) {
                    return;
                }
                showStatus(resolvePlaybackStatus(resolvedTrack, R.string.feature_search_status_playing));
                searchHost.onSearchPlaybackRequested(playbackPage, startIndex, playbackRequest);
            }

            @Override
            public void onPlaybackResolveFailed(@NonNull SearchTrack failedTrack,
                                                @NonNull IOException exception) {
                if (!isAdded()) {
                    return;
                }
                showStatus(getString(R.string.feature_search_status_playback_failed, exception.getMessage()));
            }
        });
    }

    private void playAll() {
        if (currentResultPage == null || currentResultPage.getTracks().isEmpty()) {
            return;
        }
        SearchResultPage playbackPage = currentResultPage;
        SearchTrack firstTrack = playbackPage.getTracks().get(0);
        showStatus(getString(R.string.feature_search_status_resolving_queue));
        searchPlaybackCoordinator.play(firstTrack, new SearchPlaybackCoordinator.IListener() {
            @Override
            public void onPlaybackResolved(@NonNull SearchTrack resolvedTrack,
                                           @NonNull PlaybackRequest playbackRequest) {
                if (!isAdded()) {
                    return;
                }
                showStatus(resolvePlaybackStatus(resolvedTrack, R.string.feature_search_status_queue_playing));
                searchHost.onSearchQueuePlaybackRequested(playbackPage, 0, playbackRequest);
            }

            @Override
            public void onPlaybackResolveFailed(@NonNull SearchTrack failedTrack,
                                                @NonNull IOException exception) {
                if (!isAdded()) {
                    return;
                }
                showStatus(getString(R.string.feature_search_status_playback_failed, exception.getMessage()));
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
                : topTrack.getArtistNames().get(0) + " · " + formatType(topTrack);
        featuredSubtitleView.setText(subtitle);
    }

    @NonNull
    private String formatType(@NonNull SearchTrack track) {
        if (track.getDurationMs() > 0L) {
            return "Single";
        }
        return "Featured";
    }

    @NonNull
    private SearchResultPage mergePages(@Nullable SearchResultPage currentPage,
                                        @NonNull SearchResultPage nextPage) {
        if (currentPage == null) {
            return nextPage;
        }
        List<SearchTrack> tracks = new ArrayList<>(currentPage.getTracks());
        tracks.addAll(nextPage.getTracks());
        List<SearchAlbum> albums = new ArrayList<>(currentPage.getAlbums());
        albums.addAll(nextPage.getAlbums());
        List<SearchArtist> artists = new ArrayList<>(currentPage.getArtists());
        artists.addAll(nextPage.getArtists());
        List<SearchPlaylist> playlists = new ArrayList<>(currentPage.getPlaylists());
        playlists.addAll(nextPage.getPlaylists());
        return new SearchResultPage(
                nextPage.getKeyword(),
                nextPage.getFilter(),
                nextPage.isHasMore(),
                nextPage.getTotalCount(),
                tracks,
                albums,
                artists,
                playlists);
    }

    private void clearResults() {
        currentKeyword = "";
        currentResultPage = null;
        if (resultAdapter != null) {
            resultAdapter.submitTracks(Collections.emptyList());
        }
        if (resultContainer != null) {
            resultContainer.removeAllViews();
        }
        if (resultCountView != null) {
            resultCountView.setText(R.string.feature_search_status_idle);
        }
        if (playAllView != null) {
            playAllView.setVisibility(View.GONE);
        }
        if (loadMoreButton != null) {
            loadMoreButton.setVisibility(View.GONE);
        }
        updateFeaturedCard(null);
        showStatus(getString(R.string.feature_search_status_empty_query));
    }

    @NonNull
    private SearchResultPage buildPlaybackPage(@NonNull SearchTrack track) {
        if (currentResultPage != null && !currentResultPage.getTracks().isEmpty()) {
            return currentResultPage;
        }
        return new SearchResultPage(
                TextUtils.isEmpty(currentKeyword) ? track.getTitle() : currentKeyword,
                new SearchFilter(SearchScope.TRACKS, SearchFilter.DEFAULT_PAGE, SearchFilter.DEFAULT_PAGE_SIZE),
                false,
                1,
                Collections.singletonList(track),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    private int findTrackIndex(@NonNull List<SearchTrack> tracks, @NonNull SearchTrack targetTrack) {
        for (int index = 0; index < tracks.size(); index++) {
            SearchTrack track = tracks.get(index);
            if (track == targetTrack) {
                return index;
            }
            if (track.getTrackId().equals(targetTrack.getTrackId())
                    && track.getProviderId().equals(targetTrack.getProviderId())) {
                return index;
            }
        }
        return 0;
    }

    private void cancelPendingSuggestionWork() {
        suggestionRequestVersion++;
        if (mainHandler != null && suggestionDebounceRunnable != null) {
            mainHandler.removeCallbacks(suggestionDebounceRunnable);
        }
        suggestionDebounceRunnable = null;
    }

    @NonNull
    private String getCurrentInputKeyword() {
        if (searchInput == null) {
            return "";
        }
        return searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
    }

    private boolean isSearchInputEmpty() {
        return TextUtils.isEmpty(getCurrentInputKeyword());
    }

    private boolean isSpotifySearchEnabled() {
        Bundle arguments = getArguments();
        return arguments != null && arguments.getBoolean(ARG_SPOTIFY_SEARCH_ENABLED, false);
    }

    private void showStatus(@NonNull String message) {
        if (statusView == null) {
            return;
        }
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(message);
    }

    @NonNull
    private String resolvePlaybackStatus(@NonNull SearchTrack track, int fallbackResId) {
        if (track.isPreviewPlayback() && !TextUtils.isEmpty(track.getPlaybackNotice())) {
            return track.getPlaybackNotice();
        }
        return getString(fallbackResId);
    }

    private void hideStatus() {
        if (statusView == null) {
            return;
        }
        statusView.setVisibility(View.GONE);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        cancelPendingSuggestionWork();
        if (searchExecutorService != null) {
            searchExecutorService.shutdownNow();
            searchExecutorService = null;
        }
        if (suggestionExecutorService != null) {
            suggestionExecutorService.shutdownNow();
            suggestionExecutorService = null;
        }
        mainHandler = null;
        searchViewModel = null;
        searchPlaybackCoordinator = null;
        searchInput = null;
        historyLabelView = null;
        hotLabelView = null;
        clearHistoryView = null;
        resultCountView = null;
        statusView = null;
        featuredTitleView = null;
        featuredSubtitleView = null;
        playAllView = null;
        suggestionContainer = null;
        hotContainer = null;
        scopeContainer = null;
        resultContainer = null;
        loadMoreButton = null;
        resultAdapter = null;
        currentResultPage = null;
        currentKeyword = "";
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        searchHost = null;
        super.onDetach();
    }
}
