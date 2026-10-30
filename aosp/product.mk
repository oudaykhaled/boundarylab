# Include only from the selected custom Cuttlefish userdebug product.
ifneq ($(TARGET_BUILD_VARIANT),userdebug)
$(error BoundaryLab platform proof supports userdebug only)
endif
PRODUCT_PACKAGES += boundarylab_service boundarylab_probe boundarylab_denied_probe boundarylab_engine_test
PRODUCT_SYSTEM_EXT_PROPERTIES += ro.boundarylab.profile=generated-v1
