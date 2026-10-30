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
