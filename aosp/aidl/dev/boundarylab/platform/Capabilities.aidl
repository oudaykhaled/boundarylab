package dev.boundarylab.platform;
parcelable Capabilities {
    @utf8InCpp String epoch;
    @utf8InCpp String operation = "generated-sha256-v1";
    int protocolMajor = 1;
    int protocolMinor = 0;
    long maxBytes = 134217728;
    int workBufferBytes = 65536;
    int workers = 1;
    int maxQueuedJobs = 16;
    int maxSessions = 16;
    int maxOutstandingPerSession = 4;
    int maxMetadataBytes = 4096;
    int maxResponseBytes = 16384;
    int maxRetainedEntries = 64;
    long terminalTtlMillis = 60000;
    long maxRetainedBytes = 1048576;
    long maxDeadlineMillis = 120000;
    long minPollMillis = 250;
    boolean filesSupported = false;
    int servicePid = 0;
    int liveSessions = 0;
    int queuedJobs = 0;
    int runningJobs = 0;
    int retainedEntries = 0;
    long retainedBytes = 0;
}
