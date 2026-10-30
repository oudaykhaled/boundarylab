#include "Engine.h"
#include <dev/boundarylab/platform/BnBoundaryLab.h>
#include <binder/Binder.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <binder/ProcessState.h>
#include <unistd.h>

namespace wire = dev::boundarylab::platform;
using android::sp;
using android::binder::Status;
namespace {
constexpr const char* kService = "dev.boundarylab.platform.IBoundaryLab/default";
constexpr uid_t kAllowedUid = 2000; // dedicated userdebug probe domain AND exact shell UID
wire::Result encode(const boundarylab::Result& value) {
    wire::Result out; out.error = value.error; out.epoch = value.epoch; out.sessionId = value.sessionId;
    out.jobId = value.jobId; out.requestId = value.requestId; out.state = value.state;
    out.digest = value.digest; out.bytesProcessed = value.bytesProcessed;
    out.cancelRequested = value.cancelRequested; return out;
}
class Service;
class ClientDeath final : public android::IBinder::DeathRecipient {
public:
    ClientDeath(Service* service, std::string session) : service_(service), session_(std::move(session)) {}
    void binderDied(const android::wp<android::IBinder>&) override;
private:
    Service* service_;
    std::string session_;
};
class Service final : public wire::BnBoundaryLab {
    struct Lease { int32_t uid; sp<android::IBinder> token; sp<ClientDeath> death; };
    boundarylab::Engine engine_;
    std::mutex leasesMutex_;
    std::map<std::string, Lease> leases_;
    static int32_t caller() { return android::IPCThreadState::self()->getCallingUid(); }
public:
    // Gate before generated parcel unmarshalling; Binder itself supplies caller UID.
    android::status_t onTransact(uint32_t code, const android::Parcel& data,
            android::Parcel* reply, uint32_t flags) override {
        if (flags & android::IBinder::FLAG_ONEWAY) return android::BAD_VALUE;
        if (code >= android::IBinder::FIRST_CALL_TRANSACTION && code <= android::IBinder::LAST_CALL_TRANSACTION) {
            if (caller() != kAllowedUid) return Status::fromExceptionCode(Status::EX_SECURITY, "DENIED").writeToParcel(reply);
            if (data.dataSize() > 4096 || data.hasFileDescriptors())
                return Status::fromServiceSpecificError(1, "INVALID").writeToParcel(reply);
        }
        return wire::BnBoundaryLab::onTransact(code, data, reply, flags);
    }
    Status capabilities(wire::Capabilities* out) override {
        out->epoch = engine_.epoch; out->servicePid = getpid();
        const auto m = engine_.metrics();
        out->liveSessions = m.sessions; out->queuedJobs = m.queued; out->runningJobs = m.running;
        out->retainedEntries = m.retained; out->retainedBytes = m.retainedBytes;
        return Status::ok();
    }
    Status openSession(const sp<android::IBinder>& token, int32_t major, wire::Result* out) override {
        if (major != 1) { out->error = "INCOMPATIBLE"; return Status::ok(); }
        if (!token || !token->remoteBinder()) { out->error = "INVALID"; return Status::ok(); }
        const int32_t uid = caller();
        std::lock_guard<std::mutex> lock(leasesMutex_);
        const auto session = engine_.open(uid); *out = encode(session);
        if (!out->error.empty()) return Status::ok();
        auto death = sp<ClientDeath>::make(this, session.sessionId);
        if (token->linkToDeath(death) != android::OK || !token->isBinderAlive()) {
            token->unlinkToDeath(death); engine_.close(uid, session.sessionId, engine_.epoch);
            out->error = "EXPIRED_OR_UNKNOWN"; return Status::ok();
        }
        leases_.emplace(session.sessionId, Lease{uid, token, death}); return Status::ok();
    }
    Status submit(const std::string& epoch, const std::string& session, const wire::Request& request, wire::Result* out) override {
        boundarylab::Request r{request.requestId, request.operation, request.totalBytes, request.seed,
                               request.deadlineMillis, request.protocolMajor};
        *out = encode(engine_.submit(caller(), session, epoch, r)); return Status::ok();
    }
    Status status(const std::string& epoch, const std::string& session, const std::string& job, wire::Result* out) override {
        *out = encode(engine_.status(caller(), session, epoch, job)); return Status::ok();
    }
    Status statusByRequest(const std::string& epoch, const std::string& session, const std::string& request, wire::Result* out) override {
        *out = encode(engine_.status(caller(), session, epoch, request, true)); return Status::ok();
    }
    Status cancel(const std::string& epoch, const std::string& session, const std::string& job, wire::Result* out) override {
        *out = encode(engine_.cancel(caller(), session, epoch, job)); return Status::ok();
    }
    Status closeSession(const std::string& epoch, const std::string& session, wire::Result* out) override {
        std::lock_guard<std::mutex> lock(leasesMutex_);
        *out = encode(engine_.close(caller(), session, epoch));
        if (out->error.empty()) {
            auto it = leases_.find(session);
            if (it != leases_.end()) { it->second.token->unlinkToDeath(it->second.death); leases_.erase(it); }
        }
        return Status::ok();
    }
    void died(const std::string& session, ClientDeath* expected) {
        std::lock_guard<std::mutex> lock(leasesMutex_);
        auto it = leases_.find(session);
        if (it == leases_.end() || it->second.death.get() != expected) return;
        engine_.close(it->second.uid, session, engine_.epoch); leases_.erase(it);
    }
};
void ClientDeath::binderDied(const android::wp<android::IBinder>&) { service_->died(session_, this); }
}
int main() {
    android::ProcessState::self()->setThreadPoolMaxThreadCount(2);
    auto service = sp<Service>::make();
    if (android::defaultServiceManager()->addService(android::String16(kService), service) != android::OK) return 1;
    android::ProcessState::self()->startThreadPool();
    android::IPCThreadState::self()->joinThreadPool();
    return 1;
}
