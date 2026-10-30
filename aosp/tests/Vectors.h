#pragma once
#include <cstdint>
#include <limits>
namespace boundarylab {
struct Vector { int64_t bytes, seed; const char* digest; };
// Independently computed using Python hashlib with byte[i] = (seed+i)&255.
inline constexpr Vector kVectors[] = {
    {0, 0, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"},
    {1, 0, "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d"},
    {65535, -1, "fab3cf143e3434805b9494b6b5147f28297ae4b274e45c4d1b108d9daffc594d"},
    {65536, 0, "7daca2095d0438260fa849183dfc67faa459fdf4936e1bc91eec6b281b27e4c2"},
    {65537, std::numeric_limits<int64_t>::max(), "a06f90a8ad95b4a8528baffc69f68729e941decadb1c352fcedf3942e0bf7278"},
    {134217728, std::numeric_limits<int64_t>::min(), "a626d17da2e502f5b4b8e3ebd23f0bf9daef6255688d8e0bb482b3ae3794a682"},
};
}
