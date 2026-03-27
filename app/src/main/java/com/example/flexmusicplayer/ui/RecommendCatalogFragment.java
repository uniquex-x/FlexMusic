package com.example.flexmusicplayer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.core_recommend.IRecommendRepository;
import com.example.core_recommend.NativeRecommendRepository;
import com.example.core_recommend.RecommendBrowseCategory;
import com.example.flexmusicplayer.MainActivity;
import com.example.flexmusicplayer.R;
import com.example.flexmusicplayer.databinding.FragmentRecommendCatalogBinding;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.util.List;

public final class RecommendCatalogFragment extends Fragment {

    private FragmentRecommendCatalogBinding binding;
    private IRecommendRepository recommendRepository;

    @NonNull
    public static RecommendCatalogFragment newInstance() {
        return new RecommendCatalogFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recommendRepository = new NativeRecommendRepository(requireContext().getApplicationContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRecommendCatalogBinding.inflate(inflater, container, false);
        binding.recommendCatalogBackButton.setOnClickListener(v -> requireActivity().onBackPressed());
        renderCategories();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void renderCategories() {
        if (binding == null) {
            return;
        }
        binding.recommendCatalogContainer.removeAllViews();
        try {
            List<RecommendBrowseCategory> categories = recommendRepository.loadBrowseCategories();
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (RecommendBrowseCategory category : categories) {
                View itemView = inflater.inflate(R.layout.item_recommend_category_card,
                        binding.recommendCatalogContainer,
                        false);
                bindCategoryCard(itemView, category);
                binding.recommendCatalogContainer.addView(itemView);
            }
        } catch (IOException exception) {
            binding.recommendCatalogSubtitle.setText(
                    getString(R.string.recommend_status_error, exception.getMessage()));
        }
    }

    private void bindCategoryCard(@NonNull View itemView, @NonNull RecommendBrowseCategory category) {
        MaterialCardView cardView = itemView.findViewById(R.id.recommend_category_card);
        TextView titleView = itemView.findViewById(R.id.recommend_category_title);
        TextView subtitleView = itemView.findViewById(R.id.recommend_category_subtitle);
        titleView.setText(category.getTitle());
        subtitleView.setText(category.getSubtitle());
        cardView.setCardBackgroundColor(category.getBackgroundColor());
        cardView.setOnClickListener(v -> openCategory(category.getId()));
    }

    private void openCategory(@NonNull String categoryId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openRecommendCategory(categoryId);
        }
    }
}
