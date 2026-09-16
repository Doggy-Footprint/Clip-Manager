#pragma once

#include <condition_variable>
#include <cstdint>
#include <deque>
#include <mutex>

extern "C" {
#include <libavcodec/packet.h>
}

namespace clip {

// A packet tagged with the seek generation active when it was read from the
// demuxer. Consumers compare this against SeekController::isStale() to drop
// output that belongs to a seek superseded by a newer one.
struct TaggedPacket {
    AVPacket *packet;
    uint64_t generation;
};

// Bounded-by-caller producer/consumer queue shared between the demux thread
// and a decode thread. abort() unblocks any waiting pop() so threads can exit.
class PacketQueue {
public:
    ~PacketQueue() { clear(); }

    void push(AVPacket *packet, uint64_t generation) {
        std::lock_guard<std::mutex> lock(mutex_);
        queue_.push_back({packet, generation});
        cond_.notify_one();
    }

    // Discards all queued packets, e.g. right after a seek is executed.
    void flush() {
        std::lock_guard<std::mutex> lock(mutex_);
        for (auto &item : queue_) {
            av_packet_free(&item.packet);
        }
        queue_.clear();
    }

    bool pop(TaggedPacket *out) {
        std::unique_lock<std::mutex> lock(mutex_);
        cond_.wait(lock, [this] { return aborted_ || !queue_.empty(); });
        if (aborted_ && queue_.empty()) return false;
        *out = queue_.front();
        queue_.pop_front();
        return true;
    }

    void abort() {
        std::lock_guard<std::mutex> lock(mutex_);
        aborted_ = true;
        cond_.notify_all();
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        for (auto &item : queue_) {
            av_packet_free(&item.packet);
        }
        queue_.clear();
    }

    size_t size() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return queue_.size();
    }

private:
    mutable std::mutex mutex_;
    std::condition_variable cond_;
    std::deque<TaggedPacket> queue_;
    bool aborted_ = false;
};

} // namespace clip
