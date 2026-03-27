package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.core_recommend.IRecommendRepository;
import com.example.core_recommend.NativeRecommendRepository;
import com.example.core_recommend.RecommendBrowseCategory;
import com.example.core_recommend.RecommendCard;
import com.example.core_recommend.RecommendCategory;
import com.example.core_recommend.RecommendHomeFeed;
import com.example.core_recommend.RecommendSection;
import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.databinding.FragmentMainPageBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.util.List;

public class MainPageFragment extends Fragment {

    private static final String TAG = "MainPageFragment";
    private static final String STATE_SELECTED_CATEGORY = "selected_category";
    private static final int INACTIVE_TAB_COLOR = 0xFF8B88A1;

    private FragmentMainPageBinding binding;
    private IRecommendRepository recommendRepository;
    @Nullable
    private RecommendHomeFeed recommendHomeFeed;
    private RecommendCategory selectedCategory = RecommendCategory.DAILY_RECOMMEND;
    private boolean restoredSelection = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recommendRepository = new NativeRecommendRepository(requireContext().getApplicationContext());
        if (savedInstanceState != null) {
            selectedCategory = RecommendCategory.fromId(
                    savedInstanceState.getString(STATE_SELECTED_CATEGORY));
            restoredSelection = true;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMainPageBinding.inflate(inflater, container, false);
        setupStaticActions();
        loadRecommendFeed();
        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_CATEGORY, selectedCategory.getId());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupStaticActions() {
        binding.btnSettings.setOnClickListener(v -> openSettings());
        binding.btnAvatar.setOnClickListener(v -> {
            BottomNavigationView navigationView = requireActivity().findViewById(R.id.bottom_navigation);
            navigationView.setSelectedItemId(R.id.nav_my);
        });
        binding.searchBarContainer.setOnClickListener(v -> openSearch());
        binding.tabDailyRecommendContainer.setOnClickListener(v -> selectCategory(RecommendCategory.DAILY_RECOMMEND));
        binding.tabTrendingContainer.setOnClickListener(v -> selectCategory(RecommendCategory.TRENDING));
        binding.tabTopListContainer.setOnClickListener(v -> selectCategory(RecommendCategory.TOP_LIST));
        binding.browseViewAll.setOnClickListener(v -> openRecommendCatalog());
    }

    private void loadRecommendFeed() {
        try {
            recommendHomeFeed = recommendRepository.loadHomeFeed();
            if (!restoredSelection) {
                selectedCategory = recommendHomeFeed.getDefaultCategory();
            }
            bindBrowseCategories(recommendHomeFeed.getBrowseCategories());
            renderSelectedCategory();
            Log.d(TAG, "loadRecommendFeed sections=" + recommendHomeFeed.getSections().size()
                    + " browseCategories=" + recommendHomeFeed.getBrowseCategories().size());
        } catch (IOException exception) {
            Log.e(TAG, "loadRecommendFeed failed", exception);
        }
    }

    private void selectCategory(@NonNull RecommendCategory category) {
        selectedCategory = category;
        renderSelectedCategory();
        Log.d(TAG, "selectCategory category=" + category.getId());
    }

    private void renderSelectedCategory() {
        if (binding == null || recommendHomeFeed == null) {
            return;
        }
        RecommendSection section = recommendHomeFeed.findSection(selectedCategory);
        if (section == null) {
            section = recommendHomeFeed.findSection(recommendHomeFeed.getDefaultCategory());
        }
        if (section == null) {
            return;
        }

        bindTabState(binding.tabDailyRecommend, binding.tabDailyRecommendIndicator,
                selectedCategory == RecommendCategory.DAILY_RECOMMEND);
        bindTabState(binding.tabTrending, binding.tabTrendingIndicator,
                selectedCategory == RecommendCategory.TRENDING);
        bindTabState(binding.tabTopList, binding.tabTopListIndicator,
                selectedCategory == RecommendCategory.TOP_LIST);

        List<RecommendCard> cards = section.getCards();
        if (!cards.isEmpty()) {
            bindRecommendCard(
                    binding.primaryRecommendCard,
                    binding.primaryRecommendLabel,
                    binding.primaryRecommendTitle,
                    binding.primaryRecommendSubtitle,
                    section.getCategory(),
                    cards.get(0));
        }
        if (cards.size() > 1) {
            binding.secondaryRecommendCard.setVisibility(View.VISIBLE);
            bindRecommendCard(
                    binding.secondaryRecommendCard,
                    binding.secondaryRecommendLabel,
                    binding.secondaryRecommendTitle,
                    binding.secondaryRecommendSubtitle,
                    section.getCategory(),
                    cards.get(1));
        } else {
            binding.secondaryRecommendCard.setVisibility(View.GONE);
        }
    }

    private void bindTabState(@NonNull TextView tabView, @NonNull View indicatorView, boolean selected) {
        int selectedColor = ContextCompat.getColor(requireContext(), R.color.primary_500);
        tabView.setTextColor(selected ? selectedColor : INACTIVE_TAB_COLOR);
        indicatorView.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    private void bindRecommendCard(@NonNull MaterialCardView cardView,
                                   @NonNull TextView labelView,
                                   @NonNull TextView titleView,
                                   @NonNull TextView subtitleView,
                                   @NonNull RecommendCategory category,
                                   @NonNull RecommendCard card) {
        cardView.setCardBackgroundColor(card.getBackgroundColor());
        labelView.setText(getCategoryLabel(category));
        labelView.setTextColor(card.getSubtitleColor());
        titleView.setText(getCardTitle(card.getId()));
        titleView.setTextColor(card.getTitleColor());
        subtitleView.setText(getCardSubtitle(card.getId()));
        subtitleView.setTextColor(card.getSubtitleColor());
        cardView.setOnClickListener(v -> openRecommendCollection(card.getId()));
    }

    private void bindBrowseCategories(@NonNull List<RecommendBrowseCategory> browseCategories) {
        bindBrowseCategory(
                binding.browseCategoryPopCard,
                binding.browseCategoryPopTitle,
                findBrowseCategory(browseCategories, "pop"),
                "pop",
                getString(R.string.home_category_pop));
        bindBrowseCategory(
                binding.browseCategoryRockCard,
                binding.browseCategoryRockTitle,
                findBrowseCategory(browseCategories, "rock"),
                "rock",
                getString(R.string.home_category_rock));
        bindBrowseCategory(
                binding.browseCategoryElectronicCard,
                binding.browseCategoryElectronicTitle,
                findBrowseCategory(browseCategories, "electronic"),
                "electronic",
                getString(R.string.home_category_electronic));
        bindBrowseCategory(
                binding.browseCategoryHitsCard,
                binding.browseCategoryHitsTitle,
                findBrowseCategory(browseCategories, "global_hits"),
                "global_hits",
                getString(R.string.home_category_hits));
    }

    private void bindBrowseCategory(@NonNull MaterialCardView cardView,
                                    @NonNull TextView titleView,
                                    @Nullable RecommendBrowseCategory category,
                                    @NonNull String categoryId,
                                    @NonNull String fallbackTitle) {
        titleView.setText(category != null ? category.getTitle() : fallbackTitle);
        if (category != null) {
            cardView.setCardBackgroundColor(category.getBackgroundColor());
        }
        cardView.setOnClickListener(v -> openRecommendCategory(categoryId));
    }

    @Nullable
    private RecommendBrowseCategory findBrowseCategory(@NonNull List<RecommendBrowseCategory> browseCategories,
                                                       @NonNull String id) {
        for (RecommendBrowseCategory browseCategory : browseCategories) {
            if (id.equals(browseCategory.getId())) {
                return browseCategory;
            }
        }
        return null;
    }

    @NonNull
    private String getCategoryLabel(@NonNull RecommendCategory category) {
        if (category == RecommendCategory.TRENDING) {
            return getString(R.string.home_trending_tab);
        }
        if (category == RecommendCategory.TOP_LIST) {
            return getString(R.string.home_top_list_tab);
        }
        return getString(R.string.home_daily_recommend_tab);
    }

    @NonNull
    private String getCardTitle(@NonNull String cardId) {
        int titleResId;
        switch (cardId) {
            case "modern_jazz":
                titleResId = R.string.home_modern_jazz;
                break;
            case "late_night_drive":
                titleResId = R.string.home_late_night_drive;
                break;
            case "festival_radar":
                titleResId = R.string.home_festival_radar;
                break;
            case "global_chart":
                titleResId = R.string.home_global_chart;
                break;
            case "indie_breakout":
                titleResId = R.string.home_indie_breakout;
                break;
            case "weekly_discovery":
            default:
                titleResId = R.string.home_weekly_discovery;
                break;
        }
        return getString(titleResId);
    }

    @NonNull
    private String getCardSubtitle(@NonNull String cardId) {
        int subtitleResId;
        switch (cardId) {
            case "modern_jazz":
                subtitleResId = R.string.home_modern_jazz_subtitle;
                break;
            case "late_night_drive":
                subtitleResId = R.string.home_late_night_drive_subtitle;
                break;
            case "festival_radar":
                subtitleResId = R.string.home_festival_radar_subtitle;
                break;
            case "global_chart":
                subtitleResId = R.string.home_global_chart_subtitle;
                break;
            case "indie_breakout":
                subtitleResId = R.string.home_indie_breakout_subtitle;
                break;
            case "weekly_discovery":
            default:
                subtitleResId = R.string.home_weekly_discovery_subtitle;
                break;
        }
        return getString(subtitleResId);
    }

    private void openRecommendCollection(@NonNull String collectionId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openRecommendCollection(collectionId);
        }
    }

    private void openRecommendCategory(@NonNull String categoryId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openRecommendCategory(categoryId);
        }
    }

    private void openRecommendCatalog() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openRecommendCatalog();
        }
    }

    private void openSettings() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSettings();
        }
    }

    private void openSearch() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSearch();
        }
    }
}
