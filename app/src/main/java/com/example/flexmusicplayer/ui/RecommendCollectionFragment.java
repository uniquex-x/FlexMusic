package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.core_data.search.TrackPlaybackRepository;
import com.example.core_domain.player.PlaybackRequest;
import com.example.core_domain.search.PlayTrackFromSearchUseCase;
import com.example.core_domain.search.SearchFilter;
import com.example.core_domain.search.SearchQuery;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchScope;
import com.example.core_domain.search.SearchTrack;
import com.example.core_recommend.IRecommendRepository;
import com.example.core_recommend.NativeRecommendRepository;
import com.example.core_recommend.RecommendCollectionPage;
import com.example.feature_search.ISearchHost;
import com.example.feature_search.action.SearchPlaybackCoordinator;
import com.example.feature_search.ui.SearchResultAdapter;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.databinding.FragmentRecommendCollectionBinding;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecommendCollectionFragment extends Fragment {

    private static final String TAG = "RecommendCollection";
    private static final String ARG_MODE = "mode";
    private static final String ARG_TARGET_ID = "target_id";
    private static final int MENU_ACTION_PLAY_NEXT = 1;
    private static final int MENU_ACTION_ADD_TO_QUEUE = 2;
    private static final String MODE_COLLECTION = "collection";
    private static final String MODE_CATEGORY = "category";

    private FragmentRecommendCollectionBinding binding;
    private IRecommendRepository recommendRepository;
    private SearchResultAdapter trackAdapter;
    private SearchPlaybackCoordinator searchPlaybackCoordinator;
    private ExecutorService executorService;
    private ISearchHost searchHost;
    @Nullable
    private RecommendCollectionPage currentPage;
    private int loadGeneration = 0;

    @NonNull
    public static RecommendCollectionFragment newCollection(@NonNull String collectionId) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_MODE, MODE_COLLECTION);
        arguments.putString(ARG_TARGET_ID, collectionId);
        RecommendCollectionFragment fragment = new RecommendCollectionFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull
    public static RecommendCollectionFragment newCategory(@NonNull String categoryId) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_MODE, MODE_CATEGORY);
        arguments.putString(ARG_TARGET_ID, categoryId);
        RecommendCollectionFragment fragment = new RecommendCollectionFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recommendRepository = new NativeRecommendRepository(requireContext().getApplicationContext());
        executorService = Executors.newSingleThreadExecutor();
        searchPlaybackCoordinator = new SearchPlaybackCoordinator(
                new PlayTrackFromSearchUseCase(new TrackPlaybackRepository()),
                executorService,
                new Handler(Looper.getMainLooper()));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRecommendCollectionBinding.inflate(inflater, container, false);
        searchHost = getActivity() instanceof ISearchHost ? (ISearchHost) getActivity() : null;
        trackAdapter = new SearchResultAdapter(requireContext());
        trackAdapter.setTrackActionListener(new SearchResultAdapter.ITrackActionListener() {
            @Override
            public void onTrackClicked(@NonNull SearchTrack track) {
                playTrack(track);
            }

            @Override
            public void onTrackMoreClicked(@NonNull View anchorView, @NonNull SearchTrack track) {
                showTrackActionMenu(anchorView, track);
            }
        });
        binding.recommendTrackList.setAdapter(trackAdapter);
        binding.recommendTrackList.setEmptyView(binding.recommendEmptyView);
        binding.recommendBackButton.setOnClickListener(v -> requireActivity().onBackPressed());
        binding.recommendPlayAllButton.setOnClickListener(v -> playAll());
        loadCollectionPage();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private void loadCollectionPage() {
        loadGeneration++;
        final int requestGeneration = loadGeneration;
        setStatus(getString(R.string.recommend_status_loading));
        binding.recommendLoadingView.setVisibility(View.VISIBLE);
        binding.recommendEmptyView.setVisibility(View.GONE);
        executorService.execute(() -> {
            try {
                RecommendCollectionPage page = loadPage(true);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> bindPreviewPage(page, requestGeneration));
            } catch (IOException exception) {
                Log.e(TAG, "loadCollectionPage failed", exception);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> showLoadError(exception));
            }
        });
    }

    @NonNull
    private RecommendCollectionPage loadPage(boolean preview) throws IOException {
        Bundle arguments = requireArguments();
        String mode = arguments.getString(ARG_MODE, MODE_COLLECTION);
        String targetId = arguments.getString(ARG_TARGET_ID, "");
        if (MODE_CATEGORY.equals(mode)) {
            return preview
                    ? recommendRepository.loadCategoryPreviewPage(targetId)
                    : recommendRepository.loadCategoryPage(targetId);
        }
        return preview
                ? recommendRepository.loadCollectionPreviewPage(targetId)
                : recommendRepository.loadCollectionPage(targetId);
    }

    private void bindPreviewPage(@NonNull RecommendCollectionPage page, int requestGeneration) {
        if (binding == null) {
            return;
        }
        if (requestGeneration != loadGeneration) {
            return;
        }
        currentPage = page;
        binding.recommendLoadingView.setVisibility(View.GONE);
        binding.recommendTitle.setText(page.getTitle());
        binding.recommendSubtitle.setText(page.getSubtitle());
        binding.recommendMeta.setText(getString(R.string.recommend_track_count, page.getTracks().size()));
        trackAdapter.submitTracks(page.getTracks());
        if (page.getTracks().isEmpty()) {
            setStatus(getString(R.string.recommend_status_empty_tracks));
        } else if (page.isComplete()) {
            setStatus(getString(R.string.recommend_status_loaded, page.getTracks().size()));
        } else {
            setStatus(getString(R.string.recommend_status_updating, page.getTracks().size()));
            loadFullPage(requestGeneration);
        }
    }

    private void loadFullPage(int requestGeneration) {
        executorService.execute(() -> {
            try {
                RecommendCollectionPage page = loadPage(false);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> bindFullPage(page, requestGeneration));
            } catch (IOException exception) {
                Log.w(TAG, "loadFullPage failed", exception);
            }
        });
    }

    private void bindFullPage(@NonNull RecommendCollectionPage page, int requestGeneration) {
        if (binding == null || requestGeneration != loadGeneration) {
            return;
        }
        currentPage = page;
        binding.recommendLoadingView.setVisibility(View.GONE);
        binding.recommendMeta.setText(getString(R.string.recommend_track_count, page.getTracks().size()));
        trackAdapter.submitTracks(page.getTracks());
        setStatus(getString(R.string.recommend_status_loaded, page.getTracks().size()));
    }

    private void showLoadError(@NonNull IOException exception) {
        if (binding == null) {
            return;
        }
        binding.recommendLoadingView.setVisibility(View.GONE);
        currentPage = null;
        trackAdapter.submitTracks(Collections.emptyList());
        setStatus(getString(R.string.recommend_status_error, exception.getMessage()));
    }

    private void playTrack(@NonNull SearchTrack track) {
        if (searchHost == null) {
            return;
        }
        SearchResultPage resultPage = buildResultPage();
        int startIndex = findTrackIndex(resultPage.getTracks(), track);
        setStatus(getString(R.string.recommend_status_resolving));
        searchPlaybackCoordinator.play(track, new SearchPlaybackCoordinator.IListener() {
            @Override
            public void onPlaybackResolved(@NonNull SearchTrack resolvedTrack,
                                           @NonNull PlaybackRequest playbackRequest) {
                if (!isAdded() || searchHost == null) {
                    return;
                }
                setStatus(getString(R.string.recommend_status_playing));
                searchHost.onSearchPlaybackRequested(resultPage, startIndex, playbackRequest);
            }

            @Override
            public void onPlaybackResolveFailed(@NonNull SearchTrack failedTrack,
                                                @NonNull IOException exception) {
                if (!isAdded()) {
                    return;
                }
                setStatus(getString(R.string.recommend_status_error, exception.getMessage()));
            }
        });
    }

    private void playAll() {
        if (searchHost == null || currentPage == null || currentPage.getTracks().isEmpty()) {
            return;
        }
        SearchResultPage resultPage = buildResultPage();
        SearchTrack firstTrack = resultPage.getTracks().get(0);
        setStatus(getString(R.string.recommend_status_resolving_queue));
        searchPlaybackCoordinator.play(firstTrack, new SearchPlaybackCoordinator.IListener() {
            @Override
            public void onPlaybackResolved(@NonNull SearchTrack resolvedTrack,
                                           @NonNull PlaybackRequest playbackRequest) {
                if (!isAdded() || searchHost == null) {
                    return;
                }
                setStatus(getString(R.string.recommend_status_queue_playing));
                searchHost.onSearchQueuePlaybackRequested(resultPage, 0, playbackRequest);
            }

            @Override
            public void onPlaybackResolveFailed(@NonNull SearchTrack failedTrack,
                                                @NonNull IOException exception) {
                if (!isAdded()) {
                    return;
                }
                setStatus(getString(R.string.recommend_status_error, exception.getMessage()));
            }
        });
    }

    private void showTrackActionMenu(@NonNull View anchorView, @NonNull SearchTrack track) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchorView);
        Menu menu = popupMenu.getMenu();
        menu.add(Menu.NONE, MENU_ACTION_PLAY_NEXT, Menu.NONE, R.string.recommend_action_play_next);
        menu.add(Menu.NONE, MENU_ACTION_ADD_TO_QUEUE, Menu.NONE, R.string.recommend_action_add_to_queue);
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
        if (searchHost == null) {
            return;
        }
        setStatus(getString(R.string.recommend_status_resolving));
        searchPlaybackCoordinator.play(track, new SearchPlaybackCoordinator.IListener() {
            @Override
            public void onPlaybackResolved(@NonNull SearchTrack resolvedTrack,
                                           @NonNull PlaybackRequest playbackRequest) {
                if (!isAdded() || searchHost == null) {
                    return;
                }
                if (playNext) {
                    searchHost.onSearchPlayNextRequested(resolvedTrack, playbackRequest);
                    setStatus(getString(R.string.recommend_status_added_next));
                } else {
                    searchHost.onSearchAddToQueueRequested(resolvedTrack, playbackRequest);
                    setStatus(getString(R.string.recommend_status_added_queue));
                }
            }

            @Override
            public void onPlaybackResolveFailed(@NonNull SearchTrack failedTrack,
                                                @NonNull IOException exception) {
                if (!isAdded()) {
                    return;
                }
                setStatus(getString(R.string.recommend_status_error, exception.getMessage()));
            }
        });
    }

    @NonNull
    private SearchResultPage buildResultPage() {
        if (currentPage == null) {
            return SearchResultPage.empty(new SearchQuery(""));
        }
        List<SearchTrack> tracks = currentPage.getTracks();
        return new SearchResultPage(
                currentPage.getTitle(),
                new SearchFilter(SearchScope.TRACKS, 1, Math.max(1, tracks.size())),
                false,
                tracks.size(),
                tracks,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    private int findTrackIndex(@NonNull List<SearchTrack> tracks, @NonNull SearchTrack targetTrack) {
        for (int index = 0; index < tracks.size(); index++) {
            SearchTrack track = tracks.get(index);
            if (track.getTrackId().equals(targetTrack.getTrackId())
                    && track.getProviderId().equals(targetTrack.getProviderId())) {
                return index;
            }
        }
        return 0;
    }

    private void setStatus(@NonNull String status) {
        if (binding != null) {
            binding.recommendStatus.setText(status);
        }
    }
}
