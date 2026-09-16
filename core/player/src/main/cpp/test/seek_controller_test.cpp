// Host-only test binary for clip::SeekController. Plain assert-based, no
// test framework: compiled and run by scripts/test-native.sh, not part of
// the Android CMake build (app/src/main/cpp/test is not listed as a
// CMakeLists.txt source).
#include "SeekController.h"

#include <atomic>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <optional>
#include <thread>
#include <vector>

using clip::SeekController;
using clip::SeekRequest;

namespace {

void test_C12_normal_singleRequestIsTakenExactlyOnce() {
    SeekController controller;

    uint64_t g = controller.request(1000, -1);
    assert(g == controller.generation());

    std::optional<SeekRequest> req = controller.take();
    assert(req.has_value());
    assert(req->positionMs == 1000);
    assert(req->mode == -1);
    assert(req->generation == g);

    assert(!controller.take().has_value());
}

void test_C13_normal_onlyLatestRequestSurvivesMerge() {
    SeekController controller;

    uint64_t g1 = controller.request(1000, -1);
    uint64_t g2 = controller.request(2000, -1);
    uint64_t g3 = controller.request(3000, 0);
    assert(g1 < g2);
    assert(g2 < g3);

    std::optional<SeekRequest> req = controller.take();
    assert(req.has_value());
    assert(req->positionMs == 3000);
    assert(req->mode == 0);
    assert(req->generation == g3);

    assert(!controller.take().has_value());
}

void test_C14_normal_isStaleReflectsWhetherGenerationIsLatest() {
    SeekController controller;

    uint64_t g1 = controller.request(1000, -1);
    uint64_t g2 = controller.request(2000, -1);

    assert(controller.isStale(g1));
    assert(!controller.isStale(g2));
}

void test_C15_boundary_freshControllerHasZeroGenerationAndNoRequest() {
    SeekController controller;

    assert(!controller.take().has_value());
    assert(controller.generation() == 0);
    assert(!controller.isStale(0));
}

void test_C16_edge_concurrentRequestsLeaveConsistentFinalState() {
    SeekController controller;
    const int kThreads = 8;
    const int kRequestsPerThread = 1000;

    std::atomic<bool> stopConsumer{false};
    std::thread consumer([&]() {
        while (!stopConsumer.load()) {
            controller.take();
        }
    });

    std::vector<std::thread> producers;
    producers.reserve(kThreads);
    for (int t = 0; t < kThreads; ++t) {
        producers.emplace_back([&controller, t, kRequestsPerThread]() {
            for (int i = 0; i < kRequestsPerThread; ++i) {
                controller.request(static_cast<int64_t>(t * kRequestsPerThread + i), -1);
            }
        });
    }
    for (auto& th : producers) {
        th.join();
    }

    stopConsumer.store(true);
    consumer.join();

    const uint64_t expectedGeneration = static_cast<uint64_t>(kThreads * kRequestsPerThread);
    assert(controller.generation() == expectedGeneration);

    std::optional<SeekRequest> last = controller.take();
    if (last.has_value()) {
        assert(last->generation == expectedGeneration);
    }
}

}  // namespace

int main() {
    test_C12_normal_singleRequestIsTakenExactlyOnce();
    test_C13_normal_onlyLatestRequestSurvivesMerge();
    test_C14_normal_isStaleReflectsWhetherGenerationIsLatest();
    test_C15_boundary_freshControllerHasZeroGenerationAndNoRequest();
    test_C16_edge_concurrentRequestsLeaveConsistentFinalState();

    std::printf("SeekController tests: all passed\n");
    return 0;
}
