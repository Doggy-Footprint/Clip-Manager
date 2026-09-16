#pragma once

#include <atomic>
#include <cstdint>
#include <mutex>
#include <optional>

namespace clip {

struct SeekRequest {
    int64_t positionMs;
    int mode;
    uint64_t generation;
};

// Coalesces concurrent seek requests into the single most recent one: request()
// is called from UI/scrub threads, take() is polled once per demux loop
// iteration. Only the latest request survives; isStale() lets in-flight decode
// work discard results superseded by a newer seek.
class SeekController {
public:
    uint64_t request(int64_t positionMs, int mode) {
        // Generation assignment and the pending_ write must be a single
        // critical section: otherwise two concurrent requests can interleave
        // so the lower generation is stored last, making take() yield a
        // stale request while the true latest one is silently dropped.
        std::lock_guard<std::mutex> lock(mutex_);
        uint64_t generation = generation_.fetch_add(1, std::memory_order_relaxed) + 1;
        pending_ = SeekRequest{positionMs, mode, generation};
        return generation;
    }

    std::optional<SeekRequest> take() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!pending_.has_value()) {
            return std::nullopt;
        }
        SeekRequest result = *pending_;
        pending_.reset();
        return result;
    }

    uint64_t generation() const {
        return generation_.load(std::memory_order_relaxed);
    }

    bool isStale(uint64_t generation) const {
        return generation < generation_.load(std::memory_order_relaxed);
    }

private:
    std::atomic<uint64_t> generation_{0};
    std::mutex mutex_;
    std::optional<SeekRequest> pending_;
};

} // namespace clip
