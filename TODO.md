1. Persist selected TV identity/last host; ADB trust keys already persist.
2. Handle pairing revocation by detecting auth failure and requesting re-pair.
3. Cancellation and cleanup during download/ADB transfer.
4. Map download, discovery, pairing, connection, and package-manager failures into useful result types.
5. Test bad URL, interrupted transfer, revoked pairing, incompatible APK, and multiple discovered devices.

Then final UI: device selection, code/URL input, progress, retry/forget actions, and removal of the spike dialog.
