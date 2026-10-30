#include "Engine.h"
#include <algorithm>
#include <array>
#include <chrono>
#include <cstdlib>
#include <openssl/evp.h>
#include <openssl/rand.h>

namespace boundarylab {
namespace {
Result failure(const char* error) { Result r; r.error = error; return r; }
bool canonical(const std::string& s) {
    if (s.size() != 36) return false;
    for (size_t i = 0; i < s.size(); ++i) {
        if (i == 8 || i == 13 || i == 18 || i == 23) { if (s[i] != '-') return false; }
        else if (!((s[i] >= '0' && s[i] <= '9') || (s[i] >= 'a' && s[i] <= 'f'))) return false;
    }
    return true;
}
bool terminal(const Result& r) {
    return r.state == "SUCCEEDED" || r.state == "FAILED" || r.state == "CANCELLED";
}
std::string hex(const unsigned char* data, size_t length) {
    constexpr char digits[] = "0123456789abcdef";
    std::string out; out.reserve(length * 2);
    for (size_t i = 0; i < length; ++i) { out += digits[data[i] >> 4]; out += digits[data[i] & 15]; }
    return out;
}
size_t descriptorBytes(const Request& r) {
    // Identical compact metadata envelope to the JVM generated profile, including null alias.
    return ("{\"requestId\":\"" + r.requestId + "\",\"operation\":\"" + r.operation +
        "\",\"totalBytes\":" + std::to_string(r.totalBytes) + ",\"seed\":" + std::to_string(r.seed) +
        ",\"deadlineMillis\":" + std::to_string(r.deadlineMillis) +
        ",\"alias\":null,\"protocolMajor\":" + std::to_string(r.protocolMajor) + "}").size();
}
}
bool Request::operator==(const Request& r) const {
    return requestId == r.requestId && operation == r.operation && totalBytes == r.totalBytes &&
        seed == r.seed && deadlineMillis == r.deadlineMillis && protocolMajor == r.protocolMajor;
}
std::string Engine::uuid() {
    std::array<unsigned char, 16> bytes{};
    // No predictable epoch fallback: process startup fails if secure randomness fails.
    if (RAND_bytes(bytes.data(), bytes.size()) != 1) std::abort();
    bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
    const auto s = hex(bytes.data(), bytes.size());
    return s.substr(0,8) + "-" + s.substr(8,4) + "-" + s.substr(12,4) + "-" + s.substr(16,4) + "-" + s.substr(20);
}
Engine::Engine(bool startWorker, Clock clock, std::function<void()> beforeChunk)
    : epoch(uuid()), clock_(std::move(clock)), beforeChunk_(std::move(beforeChunk)) {
    if (!clock_) clock_ = [] { return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count(); };
    if (startWorker) worker_ = std::thread([this] {
        for (;;) {
            if (runNext()) continue;
            std::unique_lock<std::mutex> lock(mutex_);
            wake_.wait(lock, [this] { return stopping_ || !ready_.empty(); });
            if (stopping_) return;
        }
    });
}
Engine::~Engine() {
    { std::lock_guard<std::mutex> lock(mutex_); stopping_ = true;
      while (!sessions_.empty()) revoke(sessions_.begin()->first); }
    wake_.notify_all();
    if (worker_.joinable()) worker_.join();
}
Result Engine::open(int32_t uid) {
    std::lock_guard<std::mutex> lock(mutex_); prune();
    if (stopping_) return failure("EXPIRED_OR_UNKNOWN");
    if (sessions_.size() >= 16) return failure("BUSY");
    Result r; r.epoch = epoch; r.sessionId = uuid();
    sessions_.emplace(r.sessionId, Session{uid, {}, {}}); return r;
}
Result Engine::authorize(int32_t uid, const std::string& session, const std::string& wantedEpoch) {
    if (wantedEpoch != epoch) return failure("STALE_EPOCH");
    if (!canonical(session)) return failure("INVALID");
    prune();
    auto it = sessions_.find(session);
    if (stopping_ || it == sessions_.end()) return failure("EXPIRED_OR_UNKNOWN");
    if (it->second.uid != uid) return failure("DENIED");
    return {};
}
Result Engine::submit(int32_t uid, const std::string& session, const std::string& wantedEpoch, const Request& r) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto denied = authorize(uid, session, wantedEpoch); if (!denied.error.empty()) return denied;
    if (r.protocolMajor != 1) return failure("INCOMPATIBLE");
    if (!canonical(r.requestId) || r.totalBytes < 0 || r.totalBytes > 134217728 ||
        r.deadlineMillis < 1 || r.deadlineMillis > 120000) return failure("INVALID");
    if (r.operation != "generated-sha256-v1") return failure("UNSUPPORTED");
    if (descriptorBytes(r) > 4096) return failure("INVALID");
    auto& s = sessions_.at(session);
    auto duplicate = s.requests.find(r.requestId);
    if (duplicate != s.requests.end()) return duplicate->second->request == r ? duplicate->second->result : failure("CONFLICT");
    size_t queued = 0, outstanding = 0;
    for (const auto& pair : sessions_) queued += pair.second.queue.size();
    for (const auto& pair : s.requests) if (!terminal(pair.second->result)) ++outstanding;
    if (queued >= 16 || outstanding >= 4) return failure("BUSY");
    auto job = std::make_shared<Job>(); job->request = r; job->admittedAt = clock_();
    job->accountedBytes = descriptorBytes(r) + 768;
    job->result.epoch = epoch; job->result.sessionId = session; job->result.jobId = uuid();
    job->result.requestId = r.requestId; job->result.state = "ACCEPTED";
    jobs_[job->result.jobId] = job; s.requests[r.requestId] = job;
    if (s.queue.empty()) ready_.push_back(session);
    s.queue.push_back(job); wake_.notify_one(); return job->result;
}
Result Engine::status(int32_t uid, const std::string& session, const std::string& wantedEpoch,
                      const std::string& id, bool byRequest) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto denied = authorize(uid, session, wantedEpoch); if (!denied.error.empty()) return denied;
    if (!canonical(id)) return failure("INVALID");
    if (byRequest) {
        const auto& requests = sessions_.at(session).requests;
        auto it = requests.find(id); return it == requests.end() ? failure("EXPIRED_OR_UNKNOWN") : it->second->result;
    }
    auto it = jobs_.find(id);
    if (it == jobs_.end()) return failure("EXPIRED_OR_UNKNOWN");
    if (it->second->result.sessionId != session) return failure("DENIED");
    return it->second->result;
}
Result Engine::cancel(int32_t uid, const std::string& session, const std::string& wantedEpoch, const std::string& id) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto denied = authorize(uid, session, wantedEpoch); if (!denied.error.empty()) return denied;
    if (!canonical(id)) return failure("INVALID");
    auto it = jobs_.find(id);
    if (it == jobs_.end()) return failure("EXPIRED_OR_UNKNOWN");
    auto job = it->second;
    if (job->result.sessionId != session) return failure("DENIED");
    if (!terminal(job->result)) {
        job->result.cancelRequested = true;
        if (job != running_) {
            auto& queue = sessions_.at(session).queue;
            queue.erase(std::remove(queue.begin(), queue.end(), job), queue.end());
            if (queue.empty()) ready_.erase(std::remove(ready_.begin(), ready_.end(), session), ready_.end());
            commit(job, "CANCELLED");
        }
    }
    return job->result;
}
Result Engine::close(int32_t uid, const std::string& session, const std::string& wantedEpoch) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto denied = authorize(uid, session, wantedEpoch); if (!denied.error.empty()) return denied;
    revoke(session); return {};
}
void Engine::revoke(const std::string& sessionRef) {
    const std::string session = sessionRef; // caller may refer to the map key being erased
    auto requests = sessions_.at(session).requests;
    sessions_.erase(session);
    ready_.erase(std::remove(ready_.begin(), ready_.end(), session), ready_.end());
    for (const auto& pair : requests) {
        auto job = pair.second;
        if (!terminal(job->result)) job->result.cancelRequested = true;
        if (job != running_) { if (!terminal(job->result)) commit(job, "CANCELLED"); forget(job); }
    }
}
bool Engine::expired(const Job& job) const { return clock_() - job.admittedAt >= job.request.deadlineMillis; }
void Engine::forget(const std::shared_ptr<Job>& job) {
    auto retained = std::find(retained_.begin(), retained_.end(), job);
    if (retained != retained_.end()) { retainedBytes_ -= job->accountedBytes; retained_.erase(retained); }
    jobs_.erase(job->result.jobId);
    auto session = sessions_.find(job->result.sessionId);
    if (session != sessions_.end()) session->second.requests.erase(job->request.requestId);
}
void Engine::prune() {
    while (!retained_.empty() && clock_() - retained_.front()->finishedAt >= 60000) {
        auto job = retained_.front(); forget(job);
    }
}
void Engine::commit(const std::shared_ptr<Job>& job, const std::string& state,
                    const std::string& error, const std::string& digest) {
    if (terminal(job->result)) return;
    job->result.state = state; job->result.error = error; job->result.digest = digest; job->finishedAt = clock_();
    if (!sessions_.count(job->result.sessionId)) { jobs_.erase(job->result.jobId); return; }
    prune();
    while (!retained_.empty() && (retained_.size() >= 64 || retainedBytes_ > 1048576 - job->accountedBytes)) {
        auto old = retained_.front(); forget(old);
    }
    retained_.push_back(job); retainedBytes_ += job->accountedBytes;
}
bool Engine::runNext() {
    std::shared_ptr<Job> job;
    { std::lock_guard<std::mutex> lock(mutex_); prune();
      if (stopping_ || running_ || ready_.empty()) return false;
      const auto session = ready_.front(); ready_.pop_front(); auto& s = sessions_.at(session);
      job = s.queue.front(); s.queue.pop_front(); if (!s.queue.empty()) ready_.push_back(session);
      running_ = job; if (!expired(*job)) job->result.state = "RUNNING"; }
    std::array<unsigned char, 65536> buffer{};
    std::array<unsigned char, EVP_MAX_MD_SIZE> digest{}; unsigned int digestLength = 0;
    std::unique_ptr<EVP_MD_CTX, decltype(&EVP_MD_CTX_free)> hash(EVP_MD_CTX_new(), EVP_MD_CTX_free);
    bool ok = hash && EVP_DigestInit_ex(hash.get(), EVP_sha256(), nullptr) == 1;
    int64_t position = 0;
    while (ok && position < job->request.totalBytes) {
        if (beforeChunk_) beforeChunk_();
        { std::lock_guard<std::mutex> lock(mutex_); if (job->result.cancelRequested || expired(*job)) break; }
        const size_t count = static_cast<size_t>(std::min<int64_t>(buffer.size(), job->request.totalBytes - position));
        // Unsigned arithmetic specifies wrapping for every signed seed, including INT64_MAX.
        for (size_t i = 0; i < count; ++i) buffer[i] = static_cast<uint64_t>(job->request.seed) + static_cast<uint64_t>(position) + i;
        ok = EVP_DigestUpdate(hash.get(), buffer.data(), count) == 1;
        position += count;
        { std::lock_guard<std::mutex> lock(mutex_); job->result.bytesProcessed = position; }
    }
    if (ok) ok = EVP_DigestFinal_ex(hash.get(), digest.data(), &digestLength) == 1;
    { std::lock_guard<std::mutex> lock(mutex_);
      if (job->result.cancelRequested) commit(job, "CANCELLED");
      else if (expired(*job)) commit(job, "FAILED", "DEADLINE_EXCEEDED");
      else if (!ok || digestLength != 32) commit(job, "FAILED", "IO_ERROR");
      else commit(job, "SUCCEEDED", {}, hex(digest.data(), digestLength));
      running_.reset(); wake_.notify_one(); }
    return true;
}
Metrics Engine::metrics() {
    std::lock_guard<std::mutex> lock(mutex_); prune(); size_t queued = 0;
    for (const auto& pair : sessions_) queued += pair.second.queue.size();
    return {sessions_.size(), queued, running_ ? 1u : 0u, retained_.size(), retainedBytes_};
}
} // namespace boundarylab
