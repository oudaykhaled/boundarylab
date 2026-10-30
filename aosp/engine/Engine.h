#pragma once
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

namespace boundarylab {
struct Request {
    std::string requestId;
    std::string operation = "generated-sha256-v1";
    int64_t totalBytes = 0, seed = 0, deadlineMillis = 30000;
    int32_t protocolMajor = 1;
    bool operator==(const Request& rhs) const;
};
struct Result {
    std::string error, epoch, sessionId, jobId, requestId, state, digest;
    int64_t bytesProcessed = 0;
    bool cancelRequested = false;
};
struct Metrics { size_t sessions, queued, running, retained, retainedBytes; };
// Native port of core/CoreEngine.kt for the generated-only platform profile.
// Every mutable policy field is protected by one mutex; hashing never holds it.
class Engine {
public:
    using Clock = std::function<int64_t()>;
    explicit Engine(bool startWorker = true, Clock clock = {}, std::function<void()> beforeChunk = {});
    ~Engine();
    const std::string epoch;
    Result open(int32_t uid);
    Result submit(int32_t uid, const std::string& session, const std::string& epoch,
                  const Request& request);
    Result status(int32_t uid, const std::string& session, const std::string& epoch,
                  const std::string& id, bool byRequest = false);
    Result cancel(int32_t uid, const std::string& session, const std::string& epoch,
                  const std::string& id);
    Result close(int32_t uid, const std::string& session, const std::string& epoch);
    Metrics metrics();
    bool runNext(); // deterministic tests; still excludes concurrent workers
    static std::string uuid();
private:
    struct Job {
        Request request;
        Result result;
        int64_t admittedAt = 0, finishedAt = 0;
        size_t accountedBytes = 0;
    };
    struct Session {
        int32_t uid;
        std::deque<std::shared_ptr<Job>> queue;
        std::map<std::string, std::shared_ptr<Job>> requests;
    };
    Result authorize(int32_t uid, const std::string& session, const std::string& epoch);
    void prune();
    void forget(const std::shared_ptr<Job>& job);
    void commit(const std::shared_ptr<Job>& job, const std::string& state,
                const std::string& error = {}, const std::string& digest = {});
    void revoke(const std::string& session);
    bool expired(const Job& job) const;
    Clock clock_;
    std::function<void()> beforeChunk_;
    std::mutex mutex_;
    std::condition_variable wake_;
    std::map<std::string, Session> sessions_;
    std::map<std::string, std::shared_ptr<Job>> jobs_;
    std::deque<std::string> ready_;
    std::deque<std::shared_ptr<Job>> retained_;
    std::shared_ptr<Job> running_;
    size_t retainedBytes_ = 0;
    bool stopping_ = false;
    std::thread worker_;
};
} // namespace boundarylab
