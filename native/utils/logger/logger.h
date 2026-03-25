#ifndef FLEXMUSIC_LOGGER_H
#define FLEXMUSIC_LOGGER_H

#include <cstdarg>
#include <string>

namespace flexmusic {
namespace utils {

// 日志级别
enum class LogLevel {
    VERBOSE = 2,
    DEBUG = 3,
    INFO = 4,
    WARN = 5,
    ERROR = 6,
    FATAL = 7
};

class Logger {
public:
    static Logger& getInstance();

    void setLogLevel(LogLevel level);
    void log(LogLevel level, const char* tag, const char* format, ...);
    void logv(LogLevel level, const char* tag, const char* format, va_list args);

    // 便捷方法
    void v(const char* tag, const char* format, ...);
    void d(const char* tag, const char* format, ...);
    void i(const char* tag, const char* format, ...);
    void w(const char* tag, const char* format, ...);
    void e(const char* tag, const char* format, ...);

private:
    Logger() = default;
    ~Logger() = default;
    Logger(const Logger&) = delete;
    Logger& operator=(const Logger&) = delete;

    LogLevel logLevel_{LogLevel::INFO};
};

class LevelLog final {
public:
    explicit constexpr LevelLog(const char* tag) : tag_(tag) {
    }

    void v(const char* format, ...) const;
    void d(const char* format, ...) const;
    void i(const char* format, ...) const;
    void w(const char* format, ...) const;
    void e(const char* format, ...) const;

private:
    const char* tag_;
};

inline LevelLog levelLog(const char* tag) {
    return LevelLog(tag);
}

// 宏定义
#define LOGV(tag, ...) flexmusic::utils::Logger::getInstance().v(tag, __VA_ARGS__)
#define LOGD(tag, ...) flexmusic::utils::Logger::getInstance().d(tag, __VA_ARGS__)
#define LOGI(tag, ...) flexmusic::utils::Logger::getInstance().i(tag, __VA_ARGS__)
#define LOGW(tag, ...) flexmusic::utils::Logger::getInstance().w(tag, __VA_ARGS__)
#define LOGE(tag, ...) flexmusic::utils::Logger::getInstance().e(tag, __VA_ARGS__)

} // namespace core
} // namespace flexmusic

#endif // FLEXMUSIC_LOGGER_H
