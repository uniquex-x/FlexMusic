#ifndef FLEXMUSIC_THREAD_POOL_H
#define FLEXMUSIC_THREAD_POOL_H

#include <functional>
#include <memory>
#include <vector>
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>

namespace flexmusic {
namespace core {

class ThreadPool {
public:
    using Task = std::function<void()>;

    explicit ThreadPool(size_t threadCount = 4);
    ~ThreadPool();

    // 提交任务
    void submit(Task task);

    // 等待所有任务完成
    void waitForAll();

    // 停止线程池
    void stop();

    size_t getTaskCount() const;

private:
    void workerThread();

    std::vector<std::thread> workers_;
    std::queue<Task> tasks_;
    std::mutex mutex_;
    std::condition_variable condition_;
    bool stop_{false};
    size_t activeTasks_{0};
};

} // namespace core
} // namespace flexmusic

#endif // FLEXMUSIC_THREAD_POOL_H
