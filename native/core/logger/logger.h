#ifndef FLEXMUSIC_LOGGER_H
#define FLEXMUSIC_LOGGER_H

#include <string>

namespace flexmusic {
namespace core {

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

// 宏定义
#define LOGV(tag, ...) flexmusic::core::Logger::getInstance().v(tag, __VA_ARGS__)
#define LOGD(tag, ...) flexmusic::core::Logger::getInstance().d(tag, __VA_ARGS__)
#define LOGI(tag, ...) flexmusic::core::Logger::getInstance().i(tag, __VA_ARGS__)
#define LOGW(tag, ...) flexmusic::core::Logger::getInstance().w(tag, __VA_ARGS__)
#define LOGE(tag, ...) flexmusic::core::Logger::getInstance().e(tag, __VA_ARGS__)

} // namespace core
} // namespace flexmusic

#endif // FLEXMUSIC_LOGGER_H
