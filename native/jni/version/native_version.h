#ifndef FLEXMUSIC_NATIVE_VERSION_H
#define FLEXMUSIC_NATIVE_VERSION_H

#include <cstdint>

namespace flexmusic {
namespace jni {

// Native 版本信息
struct NativeVersion {
    static constexpr uint32_t MAJOR = 1;
    static constexpr uint32_t MINOR = 0;
    static constexpr uint32_t PATCH = 0;

    static constexpr uint32_t VERSION_CODE = (MAJOR << 16) | (MINOR << 8) | PATCH;
    static constexpr const char* VERSION_STRING = "1.0.0";

    // 版本兼容性检查
    static bool isCompatible(uint32_t javaVersionCode);
};

} // namespace jni
} // namespace flexmusic

#endif // FLEXMUSIC_NATIVE_VERSION_H
