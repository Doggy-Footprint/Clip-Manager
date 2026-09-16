#pragma once

#include <atomic>
#include <chrono>
#include <mutex>

namespace clip {

// Audio-clock-master wall clock. The audio thread calls set() with each
// buffer's presentation time; the video thread reads get() to schedule
// frames. When no audio stream exists it falls back to a free-running
// monotonic clock started at reset().
class Clock {
public:
    void reset(double ptsSeconds) {
        std::lock_guard<std::mutex> lock(mutex_);
        ptsBase_ = ptsSeconds;
        anchor_ = std::chrono::steady_clock::now();
        valid_ = true;
    }

    void set(double ptsSeconds) {
        std::lock_guard<std::mutex> lock(mutex_);
        ptsBase_ = ptsSeconds;
        anchor_ = std::chrono::steady_clock::now();
        valid_ = true;
    }

    double get() const {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!valid_) return 0.0;
        double elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now() - anchor_).count();
        return ptsBase_ + elapsed;
    }

private:
    mutable std::mutex mutex_;
    double ptsBase_ = 0.0;
    std::chrono::steady_clock::time_point anchor_{};
    bool valid_ = false;
};

} // namespace clip
