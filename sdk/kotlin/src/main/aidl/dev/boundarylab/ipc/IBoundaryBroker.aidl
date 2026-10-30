package dev.boundarylab.ipc;
import android.os.ParcelFileDescriptor;
interface IBoundaryBroker {
    String capabilities();
    String openSession(IBinder clientToken, int protocolMajor);
    String submit(String epoch, String sessionId, String requestId, String operation, long totalBytes, long seed, long deadlineMillis, String alias, in ParcelFileDescriptor input);
    String status(String epoch, String sessionId, String jobId);
    String statusByRequest(String epoch, String sessionId, String requestId);
    String cancel(String epoch, String sessionId, String jobId);
    String closeSession(String epoch, String sessionId);
}
