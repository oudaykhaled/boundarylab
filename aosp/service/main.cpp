#include "ServiceHost.h"

int main() {
    // Preserve the dedicated userdebug probe identity; no implicit root authorization.
    return boundarylab::RunService({"dev.boundarylab.platform.IBoundaryLab/default", {2000}});
}
