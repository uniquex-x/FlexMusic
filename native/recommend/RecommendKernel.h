#ifndef FLEXMUSIC_RECOMMEND_KERNEL_H
#define FLEXMUSIC_RECOMMEND_KERNEL_H

#include <string>

namespace flexmusic {
namespace recommend {

class RecommendKernel final {
public:
    static std::string buildHomeFeedJson();
    static std::string getLastError();
};

} // namespace recommend
} // namespace flexmusic

#endif // FLEXMUSIC_RECOMMEND_KERNEL_H
