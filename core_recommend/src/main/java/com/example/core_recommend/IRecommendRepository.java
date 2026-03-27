package com.example.core_recommend;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Repository contract for homepage recommendation data.
 *
 * Implementations are responsible for returning the structured recommendation
 * feed consumed by the home screen, including tab sections and browse
 * categories. Implementations may load from native kernels, caches, or
 * network-backed sources as long as they preserve the same output contract.
 */
public interface IRecommendRepository {

    /**
     * Loads the current homepage recommendation feed.
     *
     * @return structured home recommendation data for the landing page.
     * @throws IOException when the underlying source cannot provide a valid
     *                     recommendation payload.
     */
    @NonNull
    RecommendHomeFeed loadHomeFeed() throws IOException;

    /**
     * Loads a playable recommend collection page for a specific homepage card.
     *
     * @param collectionId Stable collection identifier exposed by the homepage feed.
     * @return collection metadata and playable tracks for the destination page.
     * @throws IOException when the collection cannot be assembled from native or remote sources.
     */
    @NonNull
    RecommendCollectionPage loadCollectionPage(@NonNull String collectionId) throws IOException;

    /**
     * Loads the first playable batch for a homepage card so the destination page
     * can render quickly before the full list finishes assembling.
     *
     * @param collectionId Stable collection identifier exposed by the homepage feed.
     * @return partial or complete collection data suitable for first paint.
     * @throws IOException when even the initial batch cannot be assembled.
     */
    @NonNull
    RecommendCollectionPage loadCollectionPreviewPage(@NonNull String collectionId) throws IOException;

    /**
     * Loads a playable recommend page for a browse category.
     *
     * @param categoryId Stable browse-category identifier exposed by the homepage feed.
     * @return category metadata and playable tracks for the destination page.
     * @throws IOException when the category cannot be assembled from native or remote sources.
     */
    @NonNull
    RecommendCollectionPage loadCategoryPage(@NonNull String categoryId) throws IOException;

    /**
     * Loads the first playable batch for a browse category so the destination
     * page can render quickly before the full list finishes assembling.
     *
     * @param categoryId Stable browse-category identifier exposed by the homepage feed.
     * @return partial or complete category data suitable for first paint.
     * @throws IOException when even the initial batch cannot be assembled.
     */
    @NonNull
    RecommendCollectionPage loadCategoryPreviewPage(@NonNull String categoryId) throws IOException;

    /**
     * Loads the browse-category catalog used by the "View All" entry.
     *
     * @return category cards enriched with localized titles and descriptions.
     * @throws IOException when the homepage feed cannot be loaded.
     */
    @NonNull
    java.util.List<RecommendBrowseCategory> loadBrowseCategories() throws IOException;
}
