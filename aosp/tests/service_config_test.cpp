#include "ServiceConfig.h"
#include <cstdlib>
#include <iostream>
#include <limits>

using namespace boundarylab;
namespace {
void check(bool ok, const std::string& name) {
    std::cout << "{\"case\":\"" << name << "\",\"passed\":" << (ok ? "true" : "false") << "}" << std::endl;
    if (!ok) std::exit(1);
}
void denied(const ServiceConfig& config, const std::string& name) {
    const CallerPolicy policy(config);
    check(!policy.IsValid() && !policy.IsAllowed(0) && !policy.IsAllowed(2000) &&
          !policy.IsAllowed(12345), name);
}
}
int main() {
    const std::string legacy = "dev.boundarylab.platform.IBoundaryLab/default";
    check(IsValidServiceName(legacy), "legacy_service_name_valid");
    check(IsValidServiceName("A_z-0./instance") && IsValidServiceName("x"), "explicit_ascii_names_valid");
    check(IsValidServiceName(std::string(127, 'a')) && !IsValidServiceName(std::string(128, 'a')),
          "name_byte_bound_127");
    check(!IsValidServiceName("") && !IsValidServiceName("a b") && !IsValidServiceName("a\nb") &&
          !IsValidServiceName("a\tb") && !IsValidServiceName("a:b"), "name_invalid_characters_rejected");
    check(!IsValidServiceName(std::string("a\0b", 3)) && !IsValidServiceName(std::string(1, '\x80')),
          "name_nul_nonascii_rejected");
    denied({}, "default_config_denies_all");
    denied({legacy, {}}, "empty_uid_list_denies_all");
    denied({"", {2000}}, "empty_name_invalidates_policy");
    denied({std::string("a\0b", 3), {2000}}, "embedded_nul_invalidates_policy");
    denied({legacy, {2000, 2000}}, "duplicate_uids_invalidates_policy");
    denied({legacy, {2000, std::numeric_limits<uint32_t>::max()}}, "out_of_range_uid_invalidates_policy");
    const CallerPolicy legacyPolicy({legacy, {2000}});
    check(legacyPolicy.IsValid() && legacyPolicy.IsAllowed(2000) && !legacyPolicy.IsAllowed(0) &&
          !legacyPolicy.IsAllowed(9999), "legacy_exact_uid_only");
    const CallerPolicy productionPolicy({"org.example.hash/instance", {12345, 12346}});
    check(productionPolicy.IsValid() && productionPolicy.IsAllowed(12345) && productionPolicy.IsAllowed(12346) &&
          !productionPolicy.IsAllowed(0) && !productionPolicy.IsAllowed(2000), "explicit_uid_list_has_no_implicit_principals");
    const CallerPolicy explicitRoot({legacy, {0}});
    check(explicitRoot.IsValid() && explicitRoot.IsAllowed(0) && !explicitRoot.IsAllowed(2000), "root_requires_explicit_entry");
    const auto maxUid = static_cast<uint32_t>(std::numeric_limits<int32_t>::max());
    const CallerPolicy boundaryUid({legacy, {maxUid}});
    check(boundaryUid.IsValid() && boundaryUid.IsAllowed(maxUid), "signed_binder_uid_upper_bound_allowed");
    denied({legacy, {maxUid + 1}}, "signed_binder_uid_upper_bound_exceeded");
    ServiceConfig bounded{legacy, {}};
    for (uint32_t uid = 10000; uid < 10016; ++uid) bounded.allowedUids.push_back(uid);
    const CallerPolicy full(bounded);
    check(full.IsValid() && full.IsAllowed(10000) && full.IsAllowed(10015) && !full.IsAllowed(10016),
          "uid_list_bound_16");
    bounded.allowedUids.push_back(10016);
    denied(bounded, "uid_list_bound_exceeded");
    ServiceConfig mutableConfig{legacy, {12345}};
    const CallerPolicy snapshot(mutableConfig);
    mutableConfig.allowedUids[0] = 2000;
    mutableConfig.serviceName.clear();
    check(snapshot.IsValid() && snapshot.IsAllowed(12345) && !snapshot.IsAllowed(2000), "policy_snapshots_valid_config");
    ServiceConfig initiallyInvalid{legacy, {}};
    const CallerPolicy invalidSnapshot(initiallyInvalid);
    initiallyInvalid.allowedUids.push_back(12345);
    check(!invalidSnapshot.IsValid() && !invalidSnapshot.IsAllowed(12345), "invalid_policy_stays_denied_after_config_mutation");
}
