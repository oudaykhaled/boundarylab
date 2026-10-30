#include "Client.h"
#include "Engine.h"
#include "../tests/Vectors.h"
#include <binder/Parcel.h>
#include <log/log.h>
#include <selinux/selinux.h>
#include <chrono>
#include <cstdlib>
#include <iostream>
#include <thread>
#include <unistd.h>
#include <vector>
namespace wire = dev::boundarylab::platform;
using boundarylab::Client;
namespace {
void check(bool ok, const std::string& name, const std::string& detail = "") {
    // Details are controlled constants/UUIDs/states, never caller-provided text.
    const auto line = "{\"case\":\"" + name + "\",\"passed\":" + (ok ? "true" : "false") +
        ",\"detail\":\"" + detail + "\"}";
    std::cout << line << std::endl;
    __android_log_print(ANDROID_LOG_INFO, "BoundaryLabProbe", "%s", line.c_str());
    if (!ok) std::exit(1);
}
wire::Request request(int64_t bytes = 1) {
    wire::Request r; r.requestId = boundarylab::Engine::uuid(); r.totalBytes = bytes; return r;
}
wire::Result terminal(Client& client, const std::string& job) {
    for (int i = 0; i < 480; ++i) {
        const auto r = client.status(job);
        if (r.state != "ACCEPTED" && r.state != "RUNNING") return r;
        std::this_thread::sleep_for(std::chrono::milliseconds(250));
    }
    wire::Result timedOut; timedOut.error = "TIMEOUT_UNKNOWN"; return timedOut;
}
bool deniedWithoutPayload(const wire::Result& result) {
    return result.error == "DENIED" && result.epoch.empty() && result.sessionId.empty() &&
        result.jobId.empty() && result.requestId.empty() && result.state.empty() && result.digest.empty() &&
        result.bytesProcessed == 0 && !result.cancelRequested;
}
void sessionIsolation(Client& owner, const wire::Result& session, const wire::Result& completed) {
    // Each Client constructs its own live BBinder token. Both sessions remain open
    // during these calls; the denied UID fixture independently checks the UID gate.
    Client other;
    const auto otherSession = other.open();
    check(otherSession.error.empty() && otherSession.epoch == session.epoch &&
          otherSession.sessionId != session.sessionId, "isolation_two_live_sessions",
          session.sessionId + "/" + otherSession.sessionId);
    const auto deniedRead = other.status(completed.jobId);
    check(deniedWithoutPayload(deniedRead), "foreign_session_read_denied_empty", deniedRead.error);
    const auto deniedCancel = other.cancel(completed.jobId);
    check(deniedWithoutPayload(deniedCancel), "foreign_session_cancel_denied_empty", deniedCancel.error);
    const auto after = owner.status(completed.jobId);
    // Use a completed golden job: any observed difference cannot be natural worker progress.
    check(after.error == completed.error && after.epoch == completed.epoch &&
          after.sessionId == completed.sessionId && after.jobId == completed.jobId &&
          after.requestId == completed.requestId && after.state == completed.state &&
          after.digest == completed.digest && after.bytesProcessed == completed.bytesProcessed &&
          after.cancelRequested == completed.cancelRequested, "isolation_owner_result_unchanged", after.state);
    check(other.close().error.empty(), "isolation_other_session_closed");
}
void resources(const wire::Capabilities& cap) {
    std::cout << "{\"resources\":{\"epoch\":\"" << cap.epoch << "\",\"servicePid\":" << cap.servicePid
              << ",\"liveSessions\":" << cap.liveSessions << ",\"queuedJobs\":" << cap.queuedJobs
              << ",\"runningJobs\":" << cap.runningJobs << ",\"retainedEntries\":" << cap.retainedEntries
              << ",\"retainedBytes\":" << cap.retainedBytes << "}}" << std::endl;
}
void handle(const wire::Result& session, const wire::Result& job) {
    std::cout << "{\"epoch\":\"" << session.epoch << "\",\"sessionId\":\"" << session.sessionId
              << "\",\"jobId\":\"" << job.jobId << "\"}" << std::endl;
}
}
int main(int argc, char** argv) {
    if (argc < 2) return 2;
    const std::string mode = argv[1];
    char* context = nullptr;
    check(getcon(&context) == 0 && context != nullptr, "context_available");
    const std::string label = context; freecon(context);
    check(label == (mode == "denied" ? "u:r:boundarylab_denied:s0" : "u:r:boundarylab_probe:s0"),
          mode == "denied" ? "denied_domain" : "authorized_domain", label);
    check(security_getenforce() == 1, "global_enforcing");
    Client client; check(client.connected(), "service_discovery");
    if (mode == "denied") {
        check(getuid() == 65534, "denied_real_uid", std::to_string(getuid()));
        check(client.open().error == "DENIED", "denied", "DENIED"); return 0;
    }
    check(getuid() == 2000, "authorized_real_uid", std::to_string(getuid()));
    if (mode == "inspect" && argc == 6) {
        auto r = client.inspect(argv[2], argv[3], argv[4]);
        check(r.error == argv[5], "inspect", r.error); return 0;
    }
    std::string error; auto cap = client.capabilities(&error);
    check(error.empty() && cap.protocolMajor == 1 && !cap.filesSupported && cap.operation == "generated-sha256-v1" &&
          cap.maxBytes == 134217728 && cap.workBufferBytes == 65536 && cap.workers == 1 &&
          cap.maxQueuedJobs == 16 && cap.maxSessions == 16 && cap.maxOutstandingPerSession == 4 &&
          cap.maxMetadataBytes == 4096 && cap.maxResponseBytes == 16384 && cap.maxRetainedEntries == 64 &&
          cap.terminalTtlMillis == 60000 && cap.maxRetainedBytes == 1048576 && cap.maxDeadlineMillis == 120000,
          "authorized_capabilities", cap.epoch);
    if (mode == "resources") { resources(cap); return 0; }
    if (mode == "known") {
        const auto session = client.open(); check(session.error.empty(), "known_session_open");
        const auto job = client.submit(request(1)); check(job.error.empty(), "known_job_admitted");
        handle(session, job);
        const auto done = terminal(client, job.jobId);
        // Independent SHA-256 oracle for the single generated byte 0x00.
        check(done.error.empty() && done.state == "SUCCEEDED" && done.bytesProcessed == 1 &&
              done.digest == "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d" &&
              !done.cancelRequested, "known_job_succeeded", done.digest);
        check(client.close().error.empty(), "known_session_closed"); return 0;
    }
    if (mode == "empty") {
        check(cap.liveSessions == 0 && cap.queuedJobs == 0 && cap.runningJobs == 0 && cap.retainedEntries == 0 && cap.retainedBytes == 0,
              "resource_baseline"); return 0;
    }
    check(client.open(2).error == "INCOMPATIBLE", "major_2");
    auto session = client.open(); check(session.error.empty(), "open_session");
    if (mode == "leak" || mode == "snapshot") {
        auto job = client.submit(request(134217728)); check(job.error.empty(), "handle_admitted");
        handle(session, job);
        if (mode == "leak") _exit(0); // intentionally bypass destructor: remote Binder death must revoke
        return 0;
    }
    if (mode != "smoke") return 2;
    for (const auto& vector : boundarylab::kVectors) {
        auto r = request(vector.bytes); r.seed = vector.seed;
        auto job = client.submit(r); check(job.error.empty(), "admit_" + std::to_string(vector.bytes));
        auto done = terminal(client, job.jobId);
        check(done.error.empty() && done.state == "SUCCEEDED" && done.digest == vector.digest && done.bytesProcessed == vector.bytes,
              "golden_" + std::to_string(vector.bytes), done.state);
        if (vector.bytes == 0) sessionIsolation(client, session, done);
        check(client.submit(r).jobId == job.jobId, "dedup_" + std::to_string(vector.bytes));
        ++r.seed; check(client.submit(r).error == "CONFLICT", "conflict_" + std::to_string(vector.bytes));
        check(client.cancel(job.jobId).state == "SUCCEEDED", "success_wins_cancel_" + std::to_string(vector.bytes));
    }
    for (const auto bytes : {-1LL, 134217729LL})
        check(client.submit(request(bytes)).error == "INVALID", "over_cap_" + std::to_string(bytes));
    auto r = request(); r.operation = "regular-file-sha256-v1";
    check(client.submit(r).error == "UNSUPPORTED", "unsupported-file", "UNSUPPORTED");
    r = request(); r.requestId = "malformed";
    check(client.submit(r).error == "INVALID", "malformed_uuid");
    r = request(); r.protocolMajor = 2;
    check(client.submit(r).error == "INCOMPATIBLE", "submit_major_2");
    {
        android::Parcel data, reply;
        data.writeInterfaceToken(wire::IBoundaryLab::getInterfaceDescriptor());
        data.writeFileDescriptor(STDOUT_FILENO);
        const auto status = android::IInterface::asBinder(client.raw())->transact(android::IBinder::FIRST_CALL_TRANSACTION + 2, data, &reply);
        android::binder::Status result;
        const auto parsed = status == android::OK ? result.readFromParcel(reply) : status;
        const bool serviceRejected = status == android::OK && parsed == android::OK &&
            result.exceptionCode() == android::binder::Status::EX_SERVICE_SPECIFIC && result.serviceSpecificErrorCode() == 1;
        const bool kernelDenied = status == android::PERMISSION_DENIED;
        check(serviceRejected || kernelDenied, "raw_fd_rejected", serviceRejected ? "SERVICE_INVALID" : "KERNEL_PERMISSION_DENIED");
    }
    {
        android::Parcel data, reply;
        data.writeInterfaceToken(wire::IBoundaryLab::getInterfaceDescriptor());
        data.writeString16(android::String16(std::string(4096, 'a').c_str()));
        const auto status = android::IInterface::asBinder(client.raw())->transact(android::IBinder::FIRST_CALL_TRANSACTION, data, &reply);
        android::binder::Status result; auto parsed = result.readFromParcel(reply);
        check(status == android::OK && parsed == android::OK && result.serviceSpecificErrorCode() == 1, "raw_metadata_bound");
    }
    r = request(134217728); auto cancelling = client.submit(r);
    check(cancelling.error.empty(), "cancel_admitted");
    auto cancelled = client.cancel(cancelling.jobId);
    if (cancelled.state == "RUNNING" || cancelled.state == "ACCEPTED") cancelled = terminal(client, cancelling.jobId);
    check(cancelled.state == "CANCELLED" || cancelled.state == "SUCCEEDED", "cancel_terminal_order", cancelled.state);
    check(client.cancel(cancelling.jobId).state == cancelled.state, "cancel_terminal_immutable", cancelled.state);
    r = request(134217728); r.deadlineMillis = 1;
    auto deadline = client.submit(r); check(deadline.error.empty(), "deadline_admitted");
    auto expired = terminal(client, deadline.jobId);
    check(expired.state == "FAILED" && expired.error == "DEADLINE_EXCEEDED", "deadline", expired.error);
    check(client.close().error.empty(), "close_session");
    check(client.inspect(session.epoch, session.sessionId, cancelling.jobId).error == "EXPIRED_OR_UNKNOWN", "close_revokes");
    {
        std::vector<std::unique_ptr<Client>> clients;
        for (int i = 0; i < 16; ++i) { auto c = std::make_unique<Client>(); check(c->open().error.empty(), "session_" + std::to_string(i)); clients.push_back(std::move(c)); }
        Client overflow; check(overflow.open().error == "BUSY", "session_bound_16");
    }
    check(client.open().error.empty() && client.close().error.empty(), "session_cleanup_recovers");
    return 0;
}
