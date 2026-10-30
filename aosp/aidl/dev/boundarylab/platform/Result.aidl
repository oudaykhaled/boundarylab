package dev.boundarylab.platform;
parcelable Result {
    @utf8InCpp String error;
    @utf8InCpp String epoch;
    @utf8InCpp String sessionId;
    @utf8InCpp String jobId;
    @utf8InCpp String requestId;
    @utf8InCpp String state;
    @utf8InCpp String digest;
    long bytesProcessed = 0;
    boolean cancelRequested = false;
}
