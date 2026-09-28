# AppMapper pairing and sync protocol (v2)

Transport: TLS 1.2/1.3 over TCP (default port 8765). The computer presents a self-signed certificate backed by its persistent P-256 identity key. Remembered enrollment and resume pin the SHA-256 fingerprint of the certificate's SubjectPublicKeyInfo. Temporary manual connections accept the certificate without a trusted fingerprint; they are encrypted but do not authenticate the computer. The payload is UTF-8 JSON Lines: one JSON object per line.

Version 1 clients receive `upgrade_required`. The rotating six-digit manual code can only start a temporary session. A separate random 128-bit enrollment token appears only in the QR code and can register a remembered phone. Both credentials rotate every 60 seconds or after either is consumed. Neither credential is stored for reconnect or logged.

## Temporary manual connection

The user enters the computer's IP, port, and displayed six-digit code. The phone sends:

`{"type":"hello","protocolVersion":2,"mode":"pair_temporary","deviceId":"android-8f3a2c1b","deviceName":"Pixel 8","pairingCode":"123456"}`

The computer validates and consumes the code and replies with `hello_ack`. It does not save a phone key or remove an existing pairing. A temporary session ends when its connection closes and cannot send `forget` to revoke a remembered pairing. The phone retains any previously remembered computer but waits for a new user action after a temporary connection ends. Because the phone cannot verify the computer's identity in this mode, an on-path LAN attacker can impersonate it.

## Remembered enrollment

QR format: `appmapper://connect?host=192.168.1.10&port=8765&serverId=<stable-id>&fingerprint=<64-hex-sha256-spki>&enrollToken=<32-hex-random-token>`.

After verifying the TLS public key against the fingerprint, the phone generates an Android Keystore P-256 key pair and sends:

`{"type":"hello","protocolVersion":2,"mode":"pair_remember","deviceId":"android-8f3a2c1b","deviceName":"Pixel 8","enrollToken":"0123456789abcdef0123456789abcdef","publicKeySpki":"<base64-DER-P256-SPKI>"}`

The computer validates and consumes the enrollment token, saves the phone public key, and answers:

`{"type":"hello_ack","accepted":true,"serverId":"<stable-id>","serverName":"<computer-name>"}`

The server decides whether to save a key from the validated connection mode and credential, never from a client-supplied `remember` flag. A six-digit manual code cannot authorize enrollment. The QR fingerprint authenticates the computer to the phone; the enrollment token authorizes the phone to register with the computer.

## Resume

The phone uses its saved address and fingerprint to establish pinned TLS, then sends:

`{"type":"hello","protocolVersion":2,"mode":"resume","deviceId":"android-8f3a2c1b","deviceName":"Pixel 8"}`

The computer returns `{"type":"challenge","nonce":"<base64-32-random-bytes>"}`. The phone signs the UTF-8 bytes `appmapper-v2|<serverId>|<deviceId>|<nonce>` using its Android Keystore P-256 key and sends `{"type":"proof","signature":"<base64-DER-ECDSA-signature>"}`. The computer verifies the registered public key and returns `hello_ack`. An unknown or removed device gets `pairing_required`; an invalid proof gets `authentication_failed`.

Accepted temporary and remembered sessions may send `active_app`, `idle`, and `heartbeat`. Only remembered sessions may send `forget`, which removes the sender's computer-side pairing and closes its session. The Windows pairing page can also remove a phone and disconnect it. Existing active/idle payloads remain in `shared/examples/`.

## Address discovery

After a saved address fails, the phone broadcasts ASCII `appmapper-discover-v2|<serverId>` over UDP port 8766 on local IPv4 interfaces. The computer replies directly with `appmapper-discovery-v2|<serverId>|<tcp-port>`. This response is an address hint, never authentication: the phone still pins the original TLS public key and checks the server ID before saving the new address. A forged or incorrect hint does not clear the saved pairing; the phone continues retrying. Broadcast needs the same IPv4 broadcast domain and a firewall rule allowing UDP 8766.

## Storage

Windows: `<app directory>/config/pairing.dat`, protected using DPAPI CurrentUser. Android: `<app private no-backup directory>/config/paired-computer.json` stores the non-secret public fingerprint, server ID, device ID and Android Keystore key alias; the private key remains in Android Keystore. Forgetting a device removes its local record and key. If the remembered computer is connected, `forget` also removes its computer-side record. Offline local removal may leave the computer-side record, which can be removed on the Windows pairing page.
