#include "ServiceConfig.h"
#include <algorithm>
#include <limits>

namespace boundarylab {
namespace {
bool validUids(const std::vector<uint32_t>& uids) {
    if (uids.empty() || uids.size() > kMaxAllowedUids) return false;
    for (size_t i = 0; i < uids.size(); ++i) {
        if (uids[i] > static_cast<uint32_t>(std::numeric_limits<int32_t>::max())) return false;
        if (std::find(uids.begin(), uids.begin() + i, uids[i]) != uids.begin() + i) return false;
    }
    return true;
}
}

bool IsValidServiceName(const std::string& name) {
    if (name.empty() || name.size() > kMaxServiceNameBytes) return false;
    for (const unsigned char c : name) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' || c == '/') continue;
        return false;
    }
    return true;
}

CallerPolicy::CallerPolicy(const ServiceConfig& config)
    : valid_(IsValidServiceName(config.serviceName) && validUids(config.allowedUids)),
      allowedUids_(valid_ ? config.allowedUids : std::vector<uint32_t>{}) {}

bool CallerPolicy::IsAllowed(uint32_t uid) const {
    return valid_ && std::find(allowedUids_.begin(), allowedUids_.end(), uid) != allowedUids_.end();
}
}
