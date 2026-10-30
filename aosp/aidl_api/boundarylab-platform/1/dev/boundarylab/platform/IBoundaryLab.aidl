///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package dev.boundarylab.platform;
interface IBoundaryLab {
  dev.boundarylab.platform.Capabilities capabilities();
  dev.boundarylab.platform.Result openSession(IBinder clientToken, int protocolMajor);
  dev.boundarylab.platform.Result submit(@utf8InCpp String epoch, @utf8InCpp String sessionId, in dev.boundarylab.platform.Request request);
  dev.boundarylab.platform.Result status(@utf8InCpp String epoch, @utf8InCpp String sessionId, @utf8InCpp String jobId);
  dev.boundarylab.platform.Result statusByRequest(@utf8InCpp String epoch, @utf8InCpp String sessionId, @utf8InCpp String requestId);
  dev.boundarylab.platform.Result cancel(@utf8InCpp String epoch, @utf8InCpp String sessionId, @utf8InCpp String jobId);
  dev.boundarylab.platform.Result closeSession(@utf8InCpp String epoch, @utf8InCpp String sessionId);
}
