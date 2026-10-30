package dev.boundarylab.platform;
parcelable Request {
    @utf8InCpp String requestId;
    @utf8InCpp String operation = "generated-sha256-v1";
    long totalBytes = 0;
    long seed = 0;
    long deadlineMillis = 30000;
    int protocolMajor = 1;
}
