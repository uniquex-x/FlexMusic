#ifndef FLEXMUSIC_BLOCKING_QUEUE_H
#define FLEXMUSIC_BLOCKING_QUEUE_H

#include <condition_variable>
#include <cstddef>
#include <chrono>
#include <deque>
#include <mutex>
#include <utility>

namespace flexmusic {
namespace core {

template <typename T>
class BlockingQueue {
public:
    explicit BlockingQueue(std::size_t maxSize)
        : maxSize_(maxSize) {
    }

    bool push(T value) {
        std::unique_lock<std::mutex> lock(mutex_);
        notFullCondition_.wait(lock, [this]() {
            return closed_ || queue_.size() < maxSize_;
        });
        if (closed_) {
            return false;
        }
        queue_.push_back(std::move(value));
        notEmptyCondition_.notify_one();
        return true;
    }

    bool pop(T* value) {
        std::unique_lock<std::mutex> lock(mutex_);
        notEmptyCondition_.wait(lock, [this]() {
            return closed_ || !queue_.empty();
        });
        if (queue_.empty()) {
            return false;
        }
        *value = std::move(queue_.front());
        queue_.pop_front();
        notFullCondition_.notify_one();
        return true;
    }

    template <typename Rep, typename Period>
    bool popFor(T* value, const std::chrono::duration<Rep, Period>& timeout) {
        std::unique_lock<std::mutex> lock(mutex_);
        bool ready = notEmptyCondition_.wait_for(lock, timeout, [this]() {
            return closed_ || !queue_.empty();
        });
        if (!ready || queue_.empty()) {
            return false;
        }
        *value = std::move(queue_.front());
        queue_.pop_front();
        notFullCondition_.notify_one();
        return true;
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        queue_.clear();
        notFullCondition_.notify_all();
    }

    void close() {
        std::lock_guard<std::mutex> lock(mutex_);
        closed_ = true;
        queue_.clear();
        notEmptyCondition_.notify_all();
        notFullCondition_.notify_all();
    }

    void reset() {
        std::lock_guard<std::mutex> lock(mutex_);
        closed_ = false;
        queue_.clear();
        notEmptyCondition_.notify_all();
        notFullCondition_.notify_all();
    }

    bool empty() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return queue_.empty();
    }

private:
    const std::size_t maxSize_;
    mutable std::mutex mutex_;
    std::condition_variable notEmptyCondition_;
    std::condition_variable notFullCondition_;
    std::deque<T> queue_;
    bool closed_ = false;
};

} // namespace core
} // namespace flexmusic

#endif // FLEXMUSIC_BLOCKING_QUEUE_H
