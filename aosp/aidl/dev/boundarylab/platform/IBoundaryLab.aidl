package dev.boundarylab.platform;
import dev.boundarylab.platform.Capabilities;
import dev.boundarylab.platform.Request;
import dev.boundarylab.platform.Result;
// Internal platform interface; no vendor/VINTF stability promise. No FD/blob/URI API.
interface IBoundaryLab {
    Capabilities capabilities();
    Result openSession(IBinder clientToken, int protocolMajor);
    Result submit(@utf8InCpp String epoch, @utf8InCpp String sessionId, in Request request);
    Result status(@utf8InCpp String epoch, @utf8InCpp String sessionId, @utf8InCpp String jobId);
    Result statusByRequest(@utf8InCpp String epoch, @utf8InCpp String sessionId, @utf8InCpp String requestId);
    Result cancel(@utf8InCpp String epoch, @utf8InCpp String sessionId, @utf8InCpp String jobId);
    Result closeSession(@utf8InCpp String epoch, @utf8InCpp String sessionId);
}
