# Verification and known limits

Status: public preview 0.3.1, 2026-10-18. Build success is not a claim of universal server compatibility or app-store approval.

## Verified

- Debug APK, signed Release APK and signed Release AAB generated using the Gradle wrapper, AGP 8.11.1, Kotlin 2.1.20 and JDK 21. Final build command: `assembleRelease bundleRelease assembleDebug testDebugUnitTest lintDebug`.
- JVM suite: 34 tests, 30 passed, 4 skipped because optional SMB integration server/environment settings were absent; zero failures. Lint: zero errors, 22 warnings.
- Vivo Android 16: Debug installation and real Windows share discovery, authenticated share enumeration and navigation. System Back and edge Back return one folder level, then shares, then Home. Directory favorite creation, direct entry and removal were checked.
- Real Android instrumentation over TCP/SMB2 via an independent loopback fixture: JPEG and MP4 decode, media cache hits, image and video folder covers, empty/nested-only folder fallback. This is a protocol test fixture, not proof of Samba/NAS interoperability.
- Favorite persistence and identity rules were tested on Android with synthetic address-metadata changes. This does not count as a real DHCP lease-change test.
- Earlier fixture tests covered actual stream transfer and SHA-256 comparison; the full per-run logs are kept locally to avoid publishing private network and device information.

## Still unverified / not implemented

- Release-signed package installation and a complete fresh-install privacy-consent flow on physical devices remain to be tested. Existing user test installs use a different Debug signature and are preserved.
- No full matrix of Linux Samba, NAS, SMB3.1.1, mandatory encryption, domain accounts, DFS or ACL configurations. No claim that every share returned by enumeration is accessible; access errors are handled when opening it.
- Real files above 4 GiB, storage exhaustion, all cancellation/disconnect races, extended screen-off transfer, Android dataSync timeout and all vendor background restrictions.
- Actual DHCP reassignment, server restart identity changes and multi-network migration end to end. Server GUID is not a cryptographic certificate.
- Broad Android 10–17, large fonts, TalkBack, rotation, all image/video formats and sustained long-list performance.
- No pause/resume or offset-based continuation. Retry starts again. A lost rename response may leave final server state uncertain; inspect the remote folder before retrying.
- Folder covers are bounded samples, not full media indexing. No preview is guaranteed for every folder or video.
- Current target SDK is 36. Target 37 local-network permission changes require implementation and verification before raising target SDK.
- Store listing, regulatory applicability and production reviewer testing are not yet completed.

## Reproduce

Run `./gradlew testDebugUnitTest lintDebug assembleDebug`. Optional integration tests use the isolated test server under `tools/smb_test_server.py`; inspect each test for its environment variables. Do not use production credentials in committed files. Instrumentation test APKs are developer tools and are not distributed as the app.

## Linux CI

GitHub-hosted Ubuntu completed `testDebugUnitTest lintDebug assembleDebug` successfully: [verified workflow run](https://github.com/Cand1ePower/ezSMB/actions/runs/39106378062). This checks a clean Linux build; it is not a physical-device test or an EndeavourOS-specific validation.
