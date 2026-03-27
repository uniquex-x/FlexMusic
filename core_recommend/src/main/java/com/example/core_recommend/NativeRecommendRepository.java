package com.example.core_recommend;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.core_data.search.OnlineSearchRepository;
import com.example.core_domain.search.SearchFilter;
import com.example.core_domain.search.SearchQuery;
import com.example.core_domain.search.SearchResultPage;
import com.example.core_domain.search.SearchScope;
import com.example.core_domain.search.SearchTrack;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorCompletionService;

public final class NativeRecommendRepository implements IRecommendRepository {

    private static final String TAG = "RecommendRepository";
    private static final int DEFAULT_SURFACE_COLOR = 0xFFF3F1F8;
    private static final int DEFAULT_TITLE_COLOR = 0xFF1F1A33;
    private static final int DEFAULT_SUBTITLE_COLOR = 0xFF8B88A1;
    private static final int QUERY_PAGE_SIZE = 6;
    private static final int PREVIEW_TRACK_COUNT = 6;
    private static final int MAX_TRACKS_PER_PAGE = 18;
    private static final long PAGE_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final OnlineSearchRepository SHARED_SEARCH_REPOSITORY = new OnlineSearchRepository(false);
    private static final Map<String, CachedCollectionPage> PAGE_CACHE = new LinkedHashMap<>();
    private static final ExecutorService RECOMMEND_QUERY_EXECUTOR = Executors.newFixedThreadPool(3);

    private final Context appContext;

    public NativeRecommendRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public RecommendHomeFeed loadHomeFeed() throws IOException {
        try {
            return parseHomeFeed(RecommendNativeBridge.getHomeFeedJson(appContext));
        } catch (JSONException exception) {
            throw new IOException("Parse recommend payload failed", exception);
        }
    }

    @NonNull
    @Override
    public RecommendCollectionPage loadCollectionPage(@NonNull String collectionId) throws IOException {
        RecommendPageDefinition definition = requireCollectionDefinition(collectionId);
        return loadPage("collection:" + collectionId, definition, MAX_TRACKS_PER_PAGE, true);
    }

    @NonNull
    @Override
    public RecommendCollectionPage loadCollectionPreviewPage(@NonNull String collectionId) throws IOException {
        RecommendPageDefinition definition = requireCollectionDefinition(collectionId);
        return loadPage("collection:" + collectionId, definition, PREVIEW_TRACK_COUNT, false);
    }

    @NonNull
    @Override
    public RecommendCollectionPage loadCategoryPage(@NonNull String categoryId) throws IOException {
        RecommendPageDefinition definition = requireCategoryDefinition(categoryId);
        return loadPage("category:" + categoryId, definition, MAX_TRACKS_PER_PAGE, true);
    }

    @NonNull
    @Override
    public RecommendCollectionPage loadCategoryPreviewPage(@NonNull String categoryId) throws IOException {
        RecommendPageDefinition definition = requireCategoryDefinition(categoryId);
        return loadPage("category:" + categoryId, definition, PREVIEW_TRACK_COUNT, false);
    }

    @NonNull
    @Override
    public List<RecommendBrowseCategory> loadBrowseCategories() throws IOException {
        return loadHomeFeed().getBrowseCategories();
    }

    @NonNull
    private RecommendCollectionPage loadPage(@NonNull String cacheKey,
                                             @NonNull RecommendPageDefinition definition,
                                             int targetTrackCount,
                                             boolean requireCompletePage) throws IOException {
        RecommendCollectionPage cachedPage;
        if (requireCompletePage) {
            cachedPage = getCachedPage(buildCacheKey(cacheKey, MAX_TRACKS_PER_PAGE));
        } else {
            cachedPage = getCachedPage(buildCacheKey(cacheKey, MAX_TRACKS_PER_PAGE));
            if (cachedPage == null) {
                cachedPage = getCachedPage(buildCacheKey(cacheKey, targetTrackCount));
            }
        }
        if (cachedPage != null) {
            Log.d(TAG, "loadPage cache hit key=" + cacheKey + " trackCount=" + cachedPage.getTracks().size());
            if (!requireCompletePage && cachedPage.isComplete()) {
                return cachedPage;
            }
            return trimPage(cachedPage, targetTrackCount, requireCompletePage || cachedPage.isComplete());
        }

        LinkedHashMap<String, SearchTrack> deduplicatedTracks = new LinkedHashMap<>();
        Set<String> attemptedQueries = new LinkedHashSet<>(definition.seedQueries);
        IOException lastException = null;
        CompletionService<SeedQueryResult> completionService =
                new ExecutorCompletionService<>(RECOMMEND_QUERY_EXECUTOR);
        List<Future<SeedQueryResult>> futures = new ArrayList<>();
        for (String seedQuery : attemptedQueries) {
            futures.add(completionService.submit(() -> executeSeedQuery(seedQuery)));
        }

        int completedCount = 0;
        boolean reachedTarget = false;
        while (completedCount < futures.size()) {
            Future<SeedQueryResult> completedFuture;
            try {
                completedFuture = completionService.take();
            } catch (InterruptedException interruptedException) {
                cancelPendingFutures(futures);
                Thread.currentThread().interrupt();
                throw new IOException("Recommend query interrupted", interruptedException);
            }
            completedCount++;
            try {
                SeedQueryResult seedResult = completedFuture.get();
                for (SearchTrack track : seedResult.resultPage.getTracks()) {
                    deduplicatedTracks.put(buildTrackKey(track), track);
                    if (deduplicatedTracks.size() >= targetTrackCount) {
                        break;
                    }
                }
                if (!requireCompletePage && deduplicatedTracks.size() >= targetTrackCount) {
                    reachedTarget = true;
                    break;
                }
            } catch (InterruptedException interruptedException) {
                cancelPendingFutures(futures);
                Thread.currentThread().interrupt();
                throw new IOException("Recommend query interrupted", interruptedException);
            } catch (ExecutionException executionException) {
                Throwable cause = executionException.getCause();
                if (cause instanceof IOException) {
                    lastException = (IOException) cause;
                    Log.w(TAG, "loadPage seed failed key=" + cacheKey, lastException);
                } else {
                    throw new IOException("Recommend query execution failed", executionException);
                }
            }
        }
        if (reachedTarget) {
            cancelPendingFutures(futures);
        }

        if (deduplicatedTracks.isEmpty() && lastException != null) {
            throw lastException;
        }

        boolean pageComplete = requireCompletePage || completedCount >= futures.size();
        RecommendCollectionPage page = new RecommendCollectionPage(
                definition.id,
                appContext.getString(definition.titleResId),
                appContext.getString(definition.subtitleResId),
                pageComplete,
                new ArrayList<>(deduplicatedTracks.values()));
        cachePage(buildCacheKey(cacheKey, targetTrackCount), page);
        if (pageComplete) {
            cachePage(buildCacheKey(cacheKey, MAX_TRACKS_PER_PAGE), page);
        }
        Log.d(TAG, "loadPage assembled key=" + cacheKey
                + " seedCount=" + definition.seedQueries.size()
                + " completedCount=" + completedCount
                + " trackCount=" + page.getTracks().size()
                + " complete=" + page.isComplete());
        return page;
    }

    @NonNull
    private SeedQueryResult executeSeedQuery(@NonNull String seedQuery) throws IOException {
        SearchResultPage resultPage = SHARED_SEARCH_REPOSITORY.search(new SearchQuery(
                seedQuery,
                new SearchFilter(SearchScope.TRACKS, 1, QUERY_PAGE_SIZE)));
        return new SeedQueryResult(resultPage);
    }

    @NonNull
    private RecommendHomeFeed parseHomeFeed(@NonNull String rawJson) throws JSONException {
        JSONObject rootObject = new JSONObject(rawJson);
        RecommendCategory defaultCategory = RecommendCategory.fromId(rootObject.optString("defaultSection"));
        List<RecommendSection> sections = parseSections(rootObject.optJSONArray("sections"));
        List<RecommendBrowseCategory> browseCategories = parseBrowseCategories(
                rootObject.optJSONArray("browseCategories"));
        return new RecommendHomeFeed(defaultCategory, sections, browseCategories);
    }

    @NonNull
    private List<RecommendSection> parseSections(JSONArray sectionArray) throws JSONException {
        List<RecommendSection> sections = new ArrayList<>();
        if (sectionArray == null) {
            return sections;
        }
        for (int index = 0; index < sectionArray.length(); index++) {
            JSONObject sectionObject = sectionArray.getJSONObject(index);
            RecommendCategory category = RecommendCategory.fromId(sectionObject.optString("id"));
            List<RecommendCard> cards = parseCards(sectionObject.optJSONArray("cards"));
            sections.add(new RecommendSection(category, cards));
        }
        return sections;
    }

    @NonNull
    private List<RecommendCard> parseCards(JSONArray cardArray) throws JSONException {
        List<RecommendCard> cards = new ArrayList<>();
        if (cardArray == null) {
            return cards;
        }
        for (int index = 0; index < cardArray.length(); index++) {
            JSONObject cardObject = cardArray.getJSONObject(index);
            cards.add(new RecommendCard(
                    cardObject.optString("id"),
                    parseColor(cardObject.optString("backgroundColor"), DEFAULT_SURFACE_COLOR),
                    parseColor(cardObject.optString("titleColor"), DEFAULT_TITLE_COLOR),
                    parseColor(cardObject.optString("subtitleColor"), DEFAULT_SUBTITLE_COLOR)));
        }
        return cards;
    }

    @NonNull
    private List<RecommendBrowseCategory> parseBrowseCategories(JSONArray categoryArray) throws JSONException {
        List<RecommendBrowseCategory> browseCategories = new ArrayList<>();
        if (categoryArray == null) {
            return browseCategories;
        }
        for (int index = 0; index < categoryArray.length(); index++) {
            JSONObject categoryObject = categoryArray.getJSONObject(index);
            String categoryId = categoryObject.optString("id");
            RecommendPageDefinition definition = resolveCategoryDefinition(categoryId);
            browseCategories.add(new RecommendBrowseCategory(
                    categoryId,
                    appContext.getString(definition.titleResId),
                    appContext.getString(definition.subtitleResId),
                    parseColor(categoryObject.optString("backgroundColor"), DEFAULT_SURFACE_COLOR)));
        }
        return browseCategories;
    }

    @ColorInt
    private int parseColor(@NonNull String rawColor, @ColorInt int fallbackColor) {
        try {
            return Color.parseColor(rawColor);
        } catch (IllegalArgumentException exception) {
            return fallbackColor;
        }
    }

    @NonNull
    private String buildTrackKey(@NonNull SearchTrack track) {
        String providerTrackKey = track.getProviderId() + "|" + track.getTrackId();
        if (track.getTrackId().trim().length() > 0) {
            return providerTrackKey;
        }
        return (track.getTitle() + "|" + track.getSubtitle()).toLowerCase(Locale.ROOT);
    }

    @NonNull
    private RecommendCollectionPage trimPage(@NonNull RecommendCollectionPage page,
                                             int targetTrackCount,
                                             boolean completeOverride) {
        List<SearchTrack> tracks = page.getTracks();
        if (tracks.size() <= targetTrackCount && page.isComplete() == completeOverride) {
            return page;
        }
        int safeCount = Math.min(targetTrackCount, tracks.size());
        return new RecommendCollectionPage(
                page.getId(),
                page.getTitle(),
                page.getSubtitle(),
                completeOverride,
                new ArrayList<>(tracks.subList(0, safeCount)));
    }

    @NonNull
    private String buildCacheKey(@NonNull String cacheKey, int trackCount) {
        return cacheKey + "|count=" + trackCount;
    }

    private synchronized void cachePage(@NonNull String key, @NonNull RecommendCollectionPage page) {
        PAGE_CACHE.put(key, new CachedCollectionPage(page, System.currentTimeMillis() + PAGE_CACHE_TTL_MS));
    }

    @Nullable
    private synchronized RecommendCollectionPage getCachedPage(@NonNull String key) {
        CachedCollectionPage cachedPage = PAGE_CACHE.get(key);
        if (cachedPage == null) {
            return null;
        }
        if (cachedPage.expiresAtMs <= System.currentTimeMillis()) {
            PAGE_CACHE.remove(key);
            return null;
        }
        return cachedPage.page;
    }

    private void cancelPendingFutures(@NonNull List<Future<SeedQueryResult>> futures) {
        for (Future<SeedQueryResult> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    @NonNull
    private RecommendPageDefinition requireCollectionDefinition(@NonNull String collectionId) throws IOException {
        RecommendPageDefinition definition = resolveCollectionDefinition(collectionId);
        if (definition == null) {
            throw new IOException("Unknown recommend collection: " + collectionId);
        }
        return definition;
    }

    @NonNull
    private RecommendPageDefinition requireCategoryDefinition(@NonNull String categoryId) throws IOException {
        RecommendPageDefinition definition = resolveCategoryDefinition(categoryId);
        if (definition == null) {
            throw new IOException("Unknown recommend category: " + categoryId);
        }
        return definition;
    }

    private RecommendPageDefinition resolveCollectionDefinition(@NonNull String collectionId) {
        switch (collectionId) {
            case "weekly_discovery":
                return new RecommendPageDefinition(
                        collectionId,
                        R.string.recommend_collection_weekly_discovery_title,
                        R.string.recommend_collection_weekly_discovery_subtitle,
                        "indie pop",
                        "dream pop",
                        "bedroom pop");
            case "modern_jazz":
                return new RecommendPageDefinition(
                        collectionId,
                        R.string.recommend_collection_modern_jazz_title,
                        R.string.recommend_collection_modern_jazz_subtitle,
                        "modern jazz",
                        "nu jazz",
                        "smooth jazz");
            case "late_night_drive":
                return new RecommendPageDefinition(
                        collectionId,
                        R.string.recommend_collection_late_night_drive_title,
                        R.string.recommend_collection_late_night_drive_subtitle,
                        "synthwave",
                        "night drive",
                        "retrowave");
            case "festival_radar":
                return new RecommendPageDefinition(
                        collectionId,
                        R.string.recommend_collection_festival_radar_title,
                        R.string.recommend_collection_festival_radar_subtitle,
                        "festival edm",
                        "dance electronic",
                        "big room");
            case "global_chart":
                return new RecommendPageDefinition(
                        collectionId,
                        R.string.recommend_collection_global_chart_title,
                        R.string.recommend_collection_global_chart_subtitle,
                        "top hits",
                        "popular music",
                        "trending songs");
            case "indie_breakout":
                return new RecommendPageDefinition(
                        collectionId,
                        R.string.recommend_collection_indie_breakout_title,
                        R.string.recommend_collection_indie_breakout_subtitle,
                        "indie rock",
                        "indie pop",
                        "indie folk");
            default:
                return null;
        }
    }

    @NonNull
    private RecommendPageDefinition resolveCategoryDefinition(@NonNull String categoryId) {
        switch (categoryId) {
            case "pop":
                return new RecommendPageDefinition(
                        categoryId,
                        R.string.recommend_category_pop_title,
                        R.string.recommend_category_pop_subtitle,
                        "pop",
                        "dance pop",
                        "electropop");
            case "rock":
                return new RecommendPageDefinition(
                        categoryId,
                        R.string.recommend_category_rock_title,
                        R.string.recommend_category_rock_subtitle,
                        "rock",
                        "alternative rock",
                        "indie rock");
            case "electronic":
                return new RecommendPageDefinition(
                        categoryId,
                        R.string.recommend_category_electronic_title,
                        R.string.recommend_category_electronic_subtitle,
                        "electronic",
                        "house",
                        "edm");
            case "global_hits":
                return new RecommendPageDefinition(
                        categoryId,
                        R.string.recommend_category_global_hits_title,
                        R.string.recommend_category_global_hits_subtitle,
                        "global hits",
                        "trending songs",
                        "popular music");
            default:
                return new RecommendPageDefinition(
                        categoryId,
                        R.string.recommend_category_global_hits_title,
                        R.string.recommend_category_global_hits_subtitle,
                        "popular music",
                        "trending songs");
        }
    }

    private static final class RecommendPageDefinition {
        private final String id;
        @StringRes
        private final int titleResId;
        @StringRes
        private final int subtitleResId;
        private final List<String> seedQueries;

        private RecommendPageDefinition(@NonNull String id,
                                        @StringRes int titleResId,
                                        @StringRes int subtitleResId,
                                        @NonNull String... seedQueries) {
            this.id = id;
            this.titleResId = titleResId;
            this.subtitleResId = subtitleResId;
            List<String> queries = new ArrayList<>();
            Collections.addAll(queries, seedQueries);
            this.seedQueries = Collections.unmodifiableList(queries);
        }
    }

    private static final class CachedCollectionPage {
        private final RecommendCollectionPage page;
        private final long expiresAtMs;

        private CachedCollectionPage(@NonNull RecommendCollectionPage page, long expiresAtMs) {
            this.page = page;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class SeedQueryResult {
        private final SearchResultPage resultPage;

        private SeedQueryResult(@NonNull SearchResultPage resultPage) {
            this.resultPage = resultPage;
        }
    }
}
