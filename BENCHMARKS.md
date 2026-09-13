# Benchmarks

There is no runtime benchmark harness yet. This visual-system change does not claim a performance improvement.

`bash scripts/benchmark.sh` records the debug APK size as a deterministic packaging metric. It does not use a device, network data, timing threshold, or random input.

Correctness/UI/performance pass (2026-09-13), using JDK 17 and Android SDK Platform 36:

| Measurement | Before | After |
| --- | ---: | ---: |
| Debug APK package size | 25,505,875 bytes | 25,505,875 bytes |

The search path now reuses its compiled regular expressions, but this package-size metric does not measure runtime CPU, allocation, memory, or rendering time. No runtime performance improvement is claimed.

App-icon and cleanup pass (2026-09-13), using the same JDK 17 and Android SDK Platform 36 setup:

| Measurement | Before | After |
| --- | ---: | ---: |
| Debug APK package size | 25,505,875 bytes | 25,505,875 bytes |

The package-size measurement is unchanged. It does not measure UI rendering, launcher-icon decoding, database I/O, or memory use, so this pass makes no quantified runtime-performance claim.
