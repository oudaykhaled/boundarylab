#include "Client.h"
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
namespace wire = dev::boundarylab::platform;
namespace boundarylab {
namespace {
wire::Result checked(const android::binder::Status& status, wire::Result value) {
    if (status.isOk()) return value;
    value = {};
    if (status.exceptionCode() == android::binder::Status::EX_SECURITY) value.error = "DENIED";
    else if (status.exceptionCode() == android::binder::Status::EX_SERVICE_SPECIFIC && status.serviceSpecificErrorCode() == 1) value.error = "INVALID";
    else value.error = "LOST_OR_UNKNOWN";
    return value;
}
wire::Result lost() { wire::Result r; r.error = "LOST_OR_UNKNOWN"; return r; }
}
Client::Client() : token_(android::sp<android::BBinder>::make()) {
    android::ProcessState::self()->startThreadPool();
    service_ = android::interface_cast<wire::IBoundaryLab>(android::defaultServiceManager()->checkService(
        android::String16("dev.boundarylab.platform.IBoundaryLab/default")));
}
Client::~Client() { if (!session_.sessionId.empty()) close(); }
wire::Capabilities Client::capabilities(std::string* error) {
    wire::Capabilities out;
    if (!service_) { *error = "LOST_OR_UNKNOWN"; return out; }
    const auto status = service_->capabilities(&out);
    *error = checked(status, {}).error; return out;
}
wire::Result Client::open(int major) {
    if (!service_) return lost();
    if (!session_.sessionId.empty()) { wire::Result r; r.error = "INVALID"; return r; }
    wire::Result result; auto status = service_->openSession(token_, major, &result);
    result = checked(status, result); if (result.error.empty()) session_ = result; return result;
}
wire::Result Client::submit(const wire::Request& request) {
    if (!service_) return lost();
    wire::Result result; auto status = service_->submit(session_.epoch, session_.sessionId, request, &result);
    return checked(status, result);
}
wire::Result Client::status(const std::string& job) { return inspect(session_.epoch, session_.sessionId, job); }
wire::Result Client::inspect(const std::string& epoch, const std::string& session, const std::string& job) {
    if (!service_) return lost();
    wire::Result result; auto status = service_->status(epoch, session, job, &result); return checked(status, result);
}
wire::Result Client::cancel(const std::string& job) {
    if (!service_) return lost();
    wire::Result result; auto status = service_->cancel(session_.epoch, session_.sessionId, job, &result); return checked(status, result);
}
wire::Result Client::close() {
    if (!service_) return lost();
    wire::Result result; auto status = service_->closeSession(session_.epoch, session_.sessionId, &result);
    session_ = {}; return checked(status, result);
}
}
