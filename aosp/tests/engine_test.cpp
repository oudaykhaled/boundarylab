#include "Engine.h"
#include "Vectors.h"
#include <atomic>
#include <cstdlib>
#include <iostream>
#include <vector>
using namespace boundarylab;
namespace {
int cases = 0;
void check(bool ok, const std::string& name) {
    ++cases;
    std::cout << "{\"case\":\"" << name << "\",\"passed\":" << (ok ? "true" : "false") << "}" << std::endl;
    if (!ok) std::exit(1);
}
Request request(int64_t bytes = 1) { Request r; r.requestId = Engine::uuid(); r.totalBytes = bytes; return r; }
struct Barrier {
    std::mutex mutex; std::condition_variable wake; bool entered = false, released = false;
    void block() { std::unique_lock<std::mutex> lock(mutex); entered = true; wake.notify_all(); wake.wait(lock, [&] { return released; }); }
    void wait() { std::unique_lock<std::mutex> lock(mutex); wake.wait(lock, [&] { return entered; }); }
    void release() { std::lock_guard<std::mutex> lock(mutex); released = true; wake.notify_all(); }
};
}
int main() {
    Engine e(false); auto session = e.open(2000);
    for (const auto& vector : kVectors) {
        auto r = request(vector.bytes); r.seed = vector.seed;
        auto admitted = e.submit(2000, session.sessionId, e.epoch, r);
        check(admitted.error.empty() && e.runNext(), "admit_" + std::to_string(vector.bytes));
        auto result = e.status(2000, session.sessionId, e.epoch, admitted.jobId);
        check(result.state == "SUCCEEDED" && result.bytesProcessed == vector.bytes && result.digest == vector.digest,
              "golden_" + std::to_string(vector.bytes));
        check(e.cancel(2000, session.sessionId, e.epoch, admitted.jobId).state == "SUCCEEDED", "success_wins_cancel_" + std::to_string(vector.bytes));
    }
    check(e.submit(3000, session.sessionId, e.epoch, request()).error == "DENIED", "foreign_uid_denied");
    check(e.submit(2000, session.sessionId, Engine::uuid(), request()).error == "STALE_EPOCH", "stale_epoch");
    for (const auto bytes : {-1LL, 134217729LL}) check(e.submit(2000, session.sessionId, e.epoch, request(bytes)).error == "INVALID", "invalid_length_" + std::to_string(bytes));
    auto r = request(); r.protocolMajor = 2;
    check(e.submit(2000, session.sessionId, e.epoch, r).error == "INCOMPATIBLE", "major_2");
    r = request(); r.operation = "regular-file-sha256-v1";
    check(e.submit(2000, session.sessionId, e.epoch, r).error == "UNSUPPORTED", "file_unsupported");
    r = request(); r.requestId = "bad";
    check(e.submit(2000, session.sessionId, e.epoch, r).error == "INVALID", "malformed_uuid");
    for (const auto deadline : {0, 120001}) { r = request(); r.deadlineMillis = deadline;
        check(e.submit(2000, session.sessionId, e.epoch, r).error == "INVALID", "invalid_deadline_" + std::to_string(deadline)); }
    r = request(); auto first = e.submit(2000, session.sessionId, e.epoch, r);
    check(e.submit(2000, session.sessionId, e.epoch, r).jobId == first.jobId, "dedup_no_new_job");
    ++r.seed; check(e.submit(2000, session.sessionId, e.epoch, r).error == "CONFLICT", "dedup_conflict");
    check(e.status(2000, session.sessionId, e.epoch, r.requestId, true).jobId == first.jobId, "lookup_request");
    check(e.cancel(2000, session.sessionId, e.epoch, first.jobId).state == "CANCELLED" && !e.runNext() &&
          e.status(2000, session.sessionId, e.epoch, first.jobId).bytesProcessed == 0, "queued_cancel_zero_work");
    auto s2 = e.open(2000);
    check(e.status(2000, s2.sessionId, e.epoch, first.jobId).error == "DENIED", "foreign_session_denied");
    e.close(2000, session.sessionId, e.epoch);
    check(e.status(2000, session.sessionId, e.epoch, first.jobId).error == "EXPIRED_OR_UNKNOWN", "close_revokes");
    {
        Engine caps(false); std::vector<Result> sessions;
        for (int i = 0; i < 16; ++i) sessions.push_back(caps.open(2000));
        check(caps.metrics().sessions == 16 && caps.open(2000).error == "BUSY", "session_bound_16");
        for (int s = 0; s < 4; ++s) for (int j = 0; j < 4; ++j)
            check(caps.submit(2000, sessions[s].sessionId, caps.epoch, request()).error.empty(), "queue_admit_" + std::to_string(s*4+j));
        check(caps.submit(2000, sessions[0].sessionId, caps.epoch, request()).error == "BUSY", "outstanding_bound_4");
        check(caps.submit(2000, sessions[4].sessionId, caps.epoch, request()).error == "BUSY" && caps.metrics().queued == 16, "queue_bound_16");
        for (const auto& s : sessions) caps.close(2000, s.sessionId, caps.epoch);
        auto m = caps.metrics(); check(m.sessions == 0 && m.queued == 0 && m.running == 0 && m.retained == 0, "cleanup_to_baseline");
    }
    {
        int64_t now = 0; Engine retention(false, [&] { return now; }); auto s = retention.open(2000);
        Result oldest;
        for (int i = 0; i < 65; ++i) { auto j = retention.submit(2000, s.sessionId, retention.epoch, request(0)); if (i == 0) oldest = j; retention.runNext(); }
        auto m = retention.metrics();
        check(m.retained == 64 && m.retainedBytes <= 1048576 && retention.status(2000, s.sessionId, retention.epoch, oldest.jobId).error == "EXPIRED_OR_UNKNOWN", "retention_count_bytes");
        now = 60000; check(retention.metrics().retained == 0, "terminal_ttl_60000");
        auto queued = retention.submit(2000, s.sessionId, retention.epoch, request(1024)); now += 30000; retention.runNext();
        auto expired = retention.status(2000, s.sessionId, retention.epoch, queued.jobId);
        check(expired.error == "DEADLINE_EXCEEDED" && expired.state == "FAILED" && expired.bytesProcessed == 0, "queued_deadline_no_work");
    }
    for (const bool deadline : {false, true}) {
        Barrier barrier; std::atomic<int64_t> now{0};
        Engine racing(false, [&] { return now.load(); }, [&] { barrier.block(); }); auto s = racing.open(2000);
        auto j = racing.submit(2000, s.sessionId, racing.epoch, request(65537));
        std::thread worker([&] { racing.runNext(); }); barrier.wait();
        check(racing.metrics().running == 1 && !racing.runNext(), deadline ? "deadline_retains_worker" : "cancel_retains_worker");
        if (deadline) now = 30000;
        else check(racing.cancel(2000, s.sessionId, racing.epoch, j.jobId).state == "RUNNING", "running_cancel_cooperative");
        barrier.release(); worker.join(); auto result = racing.status(2000, s.sessionId, racing.epoch, j.jobId);
        check(result.state == (deadline ? "FAILED" : "CANCELLED") && result.bytesProcessed == 0 &&
              result.error == (deadline ? "DEADLINE_EXCEEDED" : ""), deadline ? "deadline_wins_success" : "cancel_wins_success");
    }
    std::cout << "{\"summary\":\"PASS\",\"checks\":" << cases << "}" << std::endl;
}
