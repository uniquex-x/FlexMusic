#include "RecommendKernel.h"

#include <mutex>
#include <sstream>

namespace flexmusic {
namespace recommend {

namespace {

std::mutex g_last_error_mutex;
std::string g_last_error;

void setLastError(const std::string& error) {
    std::lock_guard<std::mutex> lock(g_last_error_mutex);
    g_last_error = error;
}

void appendCard(std::ostringstream& stream,
                const char* id,
                const char* backgroundColor,
                const char* titleColor,
                const char* subtitleColor,
                bool appendComma) {
    stream << "{"
           << "\"id\":\"" << id << "\","
           << "\"backgroundColor\":\"" << backgroundColor << "\","
           << "\"titleColor\":\"" << titleColor << "\","
           << "\"subtitleColor\":\"" << subtitleColor << "\""
           << "}";
    if (appendComma) {
        stream << ",";
    }
}

void appendSection(std::ostringstream& stream,
                   const char* id,
                   const char* firstCardId,
                   const char* firstBackgroundColor,
                   const char* firstTitleColor,
                   const char* firstSubtitleColor,
                   const char* secondCardId,
                   const char* secondBackgroundColor,
                   const char* secondTitleColor,
                   const char* secondSubtitleColor,
                   bool appendComma) {
    stream << "{"
           << "\"id\":\"" << id << "\","
           << "\"cards\":[";
    appendCard(stream, firstCardId, firstBackgroundColor, firstTitleColor, firstSubtitleColor, true);
    appendCard(stream, secondCardId, secondBackgroundColor, secondTitleColor, secondSubtitleColor, false);
    stream << "]"
           << "}";
    if (appendComma) {
        stream << ",";
    }
}

void appendBrowseCategory(std::ostringstream& stream,
                          const char* id,
                          const char* backgroundColor,
                          bool appendComma) {
    stream << "{"
           << "\"id\":\"" << id << "\","
           << "\"backgroundColor\":\"" << backgroundColor << "\""
           << "}";
    if (appendComma) {
        stream << ",";
    }
}

} // namespace

std::string RecommendKernel::buildHomeFeedJson() {
    setLastError("");

    std::ostringstream stream;
    stream << "{"
           << "\"defaultSection\":\"daily_recommend\","
           << "\"sections\":[";
    appendSection(
            stream,
            "daily_recommend",
            "weekly_discovery",
            "#F3F1F8",
            "#1F1A33",
            "#8B88A1",
            "modern_jazz",
            "#A36A34",
            "#FFFFFF",
            "#F6E4D0",
            true);
    appendSection(
            stream,
            "trending",
            "late_night_drive",
            "#1E293B",
            "#FFFFFF",
            "#B7C6E0",
            "festival_radar",
            "#FF7849",
            "#FFFFFF",
            "#FFE0D4",
            true);
    appendSection(
            stream,
            "top_list",
            "global_chart",
            "#164E63",
            "#FFFFFF",
            "#BEE3F8",
            "indie_breakout",
            "#2F3C7E",
            "#FFFFFF",
            "#CAD2FF",
            false);
    stream << "],"
           << "\"browseCategories\":[";
    appendBrowseCategory(stream, "pop", "#7F44EC", true);
    appendBrowseCategory(stream, "rock", "#FF5A2E", true);
    appendBrowseCategory(stream, "electronic", "#2B8FFF", true);
    appendBrowseCategory(stream, "global_hits", "#14B38D", false);
    stream << "]"
           << "}";
    return stream.str();
}

std::string RecommendKernel::getLastError() {
    std::lock_guard<std::mutex> lock(g_last_error_mutex);
    return g_last_error;
}

} // namespace recommend
} // namespace flexmusic
