#include "logger.h"

#include <android/log.h>

namespace flexmusic {
namespace utils {

namespace {

int toAndroidPriority(LogLevel level) {
    return static_cast<int>(level);
}

} // namespace

Logger& Logger::getInstance() {
    static Logger instance;
    return instance;
}

void Logger::setLogLevel(LogLevel level) {
    logLevel_ = level;
}

void Logger::log(LogLevel level, const char* tag, const char* format, ...) {
    va_list args;
    va_start(args, format);
    logv(level, tag, format, args);
    va_end(args);
}

void Logger::logv(LogLevel level, const char* tag, const char* format, va_list args) {
    if (static_cast<int>(level) < static_cast<int>(logLevel_)) {
        return;
    }
    __android_log_vprint(toAndroidPriority(level), tag != nullptr ? tag : "FlexMusic", format, args);
}

void Logger::v(const char* tag, const char* format, ...) {
    va_list args;
    va_start(args, format);
    logv(LogLevel::VERBOSE, tag, format, args);
    va_end(args);
}

void Logger::d(const char* tag, const char* format, ...) {
    va_list args;
    va_start(args, format);
    logv(LogLevel::DEBUG, tag, format, args);
    va_end(args);
}

void Logger::i(const char* tag, const char* format, ...) {
    va_list args;
    va_start(args, format);
    logv(LogLevel::INFO, tag, format, args);
    va_end(args);
}

void Logger::w(const char* tag, const char* format, ...) {
    va_list args;
    va_start(args, format);
    logv(LogLevel::WARN, tag, format, args);
    va_end(args);
}

void Logger::e(const char* tag, const char* format, ...) {
    va_list args;
    va_start(args, format);
    logv(LogLevel::ERROR, tag, format, args);
    va_end(args);
}

void LevelLog::v(const char* format, ...) const {
    va_list args;
    va_start(args, format);
    Logger::getInstance().logv(LogLevel::VERBOSE, tag_, format, args);
    va_end(args);
}

void LevelLog::d(const char* format, ...) const {
    va_list args;
    va_start(args, format);
    Logger::getInstance().logv(LogLevel::DEBUG, tag_, format, args);
    va_end(args);
}

void LevelLog::i(const char* format, ...) const {
    va_list args;
    va_start(args, format);
    Logger::getInstance().logv(LogLevel::INFO, tag_, format, args);
    va_end(args);
}

void LevelLog::w(const char* format, ...) const {
    va_list args;
    va_start(args, format);
    Logger::getInstance().logv(LogLevel::WARN, tag_, format, args);
    va_end(args);
}

void LevelLog::e(const char* format, ...) const {
    va_list args;
    va_start(args, format);
    Logger::getInstance().logv(LogLevel::ERROR, tag_, format, args);
    va_end(args);
}

} // namespace core
} // namespace flexmusic
