#include "ServiceHost.h"

int main() {
    boundarylab::ServiceConfig config;
    config.serviceName = "org.example.generated_hash/default";
    // Image owner: populate with the exact UID(s) assigned to your native caller(s).
    // Empty deliberately rejects startup (exit 2); root/shell are never implicit.
    config.allowedUids = {};
    return boundarylab::RunService(config);
}
