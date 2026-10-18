# Architecture

ezSMB is a single-activity native Android application (Kotlin, Compose Material 3, Navigation Compose). Minimum Android 10, compile/target SDK 36. UI state uses ViewModel, StateFlow and coroutines; blocking network, storage and cryptography run off the main thread.

## Discovery and identity

`Discovery` combines saved device probes, Android NSD `_smb._tcp.` discovery and bounded TCP 445 probes on a selected private IPv4 LAN. SMB negotiation verifies candidates. VPN interfaces are excluded from automatic LAN scanning. No public subnet sweep is performed.

Server GUID, port and network context distinguish saved identities. Names are display hints, not authentication. A server restart can change GUID: directory favorites offer explicit reassociation and require successful authentication and directory access. Credentials do not migrate automatically across networks by hostname.

## SMB engine

SMBJ 0.14.0 provides SMB2/SMB3 transport, authentication and file operations. Rapid7 DCE/RPC 0.12.13 supplies SRVSVC request/response types through an SMBJ `IPC$/srvsvc` pipe transport opened with FILE_OPEN. This is actual server-level share enumeration, not listing a preselected share. One SMB transport engine is used.

Authenticated connections require signing; authentication failures do not silently fall back to guest. An explicitly selected anonymous connection is separate and available only after probing a readable directory. A required-signing guest session is rejected rather than weakening security. SMB1 is not supported. Compatibility with all SMB3 encryption policies is not yet established.

## Storage and transfer

Android Keystore AES-GCM protects remembered credentials. DataStore holds local device and favorite metadata. Android backup is disabled. User-selected uploads use ContentResolver streams; downloads use MediaStore with pending state until finalized, under `Download/ezSMB`.

Transfers run one at a time with 64-bit byte counters. Uploads use per-task temporary names followed by rename. Existing files are preserved unless an explicit supported overwrite is confirmed. Failures never become successful completion. An interrupted process restores tasks as interrupted, requiring a new transfer; no resumable-transfer claim is made.

The dataSync foreground service handles Android timeout boundaries. It does not promise survival after force-stop or indefinite background execution.

## Previews

Image/video preview reads are bounded and cached only in memory. Folder covers inspect up to 200 direct children and attempt at most two media candidates; there is no recursive indexing. Folder listing concurrency is one, media concurrency is two. Failed or oversized previews retain ordinary file/folder icons. Refresh invalidates preview state.

## Privacy boundary

The first-use privacy gate is shown before creating the main ViewModel and triggering discovery. No cloud file relay, advertising, analytics or crash-upload SDK is included. Source paths: `app/src/main/java/com/ezsmb/app/`, with `core`, `data`, and `transfer` packages.
