#pragma once
#include "ServiceConfig.h"

namespace boundarylab {
// Owns the generated-V1 Binder service, Engine and Binder thread-pool lifecycle.
// Blocks while serving. Returns 2 for invalid config before Binder setup/registration;
// returns 1 for registration failure or an unexpected thread-pool exit.
int RunService(const ServiceConfig& config);
}
