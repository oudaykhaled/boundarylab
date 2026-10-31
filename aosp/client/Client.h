#pragma once
#include <dev/boundarylab/platform/IBoundaryLab.h>
#include <binder/Binder.h>
namespace boundarylab {
// Platform-built native facade. Binder discovery is a platform API, never app reflection.
// One client owns one death token/session; no retries or submission replay after transport loss.
class Client {
public:
    Client();
    // Invalid names remain disconnected; no fallback to the legacy service.
    explicit Client(const std::string& serviceName);
    ~Client();
    bool connected() const { return service_ != nullptr; }
    dev::boundarylab::platform::Capabilities capabilities(std::string* error);
    dev::boundarylab::platform::Result open(int major = 1);
    dev::boundarylab::platform::Result submit(const dev::boundarylab::platform::Request& request);
    dev::boundarylab::platform::Result status(const std::string& job);
    dev::boundarylab::platform::Result cancel(const std::string& job);
    dev::boundarylab::platform::Result close();
    // Deliberate stale-handle probe, not an automatic recovery path.
    dev::boundarylab::platform::Result inspect(const std::string& epoch, const std::string& session, const std::string& job);
    android::sp<dev::boundarylab::platform::IBoundaryLab> raw() const { return service_; }
private:
    android::sp<dev::boundarylab::platform::IBoundaryLab> service_;
    android::sp<android::IBinder> token_;
    dev::boundarylab::platform::Result session_;
};
}
