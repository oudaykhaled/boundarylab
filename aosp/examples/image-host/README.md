# Image-owned service host example

This source template reuses `libboundarylab_service` (including the existing bounded
Engine) and frozen `boundarylab-platform-V1-cpp`. It serves the existing generated
SHA-256 operation; it does not introduce a workload plugin or new protocol.

`main.cpp` intentionally leaves the exact UID list empty. The executable would return
2 before Binder setup/registration. There is no implicit root or shell authorization.
Choose and populate the image-owned native caller UID(s), and bind those identities to
narrow SELinux caller domains in your own product. This is an image-specific UID policy,
not a general production Android application signing, permission or caller scheme.

To adopt in an authorized image checkout:

1. Put this example in your image source tree, rename `Android.bp.example` to
   `Android.bp` and the `.rc.example` to `.rc`, and fill the explicit UID list.
2. Include `product.mk.example` from the selected product after adjusting its path/name.
   Add your reviewed policy directory to that image's BoardConfig. Copy/adapt the
   three policy templates into that directory; replace `example_image_native_caller`
   with the real existing native caller domain. Neither UID nor SELinux grants replace
   the other. The default BoundaryLab policy directory is unchanged.
3. Build against the pinned AOSP checkout and validate actual service discovery,
   authorization, death/restart and Enforcing behavior on the resulting image.
4. Link an image-owned caller against `libboundarylab_platform_client` and the frozen
   V1 CPP dependencies. Construct
   `boundarylab::Client client("org.example.generated_hash/default");` off the UI thread.
   It performs an exact lookup, with no fallback, retry or replay. Protocol/session and
   transport-error semantics remain those of the original client.

All integration files have `.example` suffixes. The checked-in example has no active
Soong module or product include and is not built/installed by the default overlay.
The new extraction and these templates have host helper coverage only; they have not
been built by Soong or executed on an Android image. Historical image evidence applies
to its recorded earlier source revision, not to this example or this extraction.
