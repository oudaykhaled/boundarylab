#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace boundarylab {
// These are library bounds, not Android's general service-name/UID limits.
inline constexpr std::size_t kMaxServiceNameBytes = 127;
inline constexpr std::size_t kMaxAllowedUids = 16;

struct ServiceConfig {
    std::string serviceName;
    std::vector<uint32_t> allowedUids; // Exact image-owned Binder UIDs; empty denies all.
};

bool IsValidServiceName(const std::string& name);

// Snapshot the complete validated config. An invalid config always denies every UID.
class CallerPolicy {
public:
    explicit CallerPolicy(const ServiceConfig& config = {});
    bool IsValid() const { return valid_; }
    bool IsAllowed(uint32_t uid) const;
private:
    const bool valid_;
    const std::vector<uint32_t> allowedUids_;
};
}
