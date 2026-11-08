# APK distribution

APKs go to the `Timebox` folder in the owner's Google Drive (My Drive). Use these
folder IDs directly instead of searching:

| Folder             | ID                                  | Holds                                                   |
| ------------------ | ----------------------------------- | ------------------------------------------------------- |
| `Timebox/`         | `1cQeovW79hud_Zi4SwjveJbIrnnnTtbES` | Only the three folders below                            |
| `Timebox/Current/` | `1HjLDPMum6Nury-3wDD8_YiE9hRSL-YYc` | Exactly one APK: the latest `master` build              |
| `Timebox/Archive/` | `1_Fsux69P_BclgXT3e-3tGSR7rmJ7K5Gl` | Every earlier `master` build                            |
| `Timebox/Testing/` | `1uX9vHwels1SebK5T8pkpK5g4DvWS3_ei` | One subfolder per issue: review and prototype builds    |

There are two kinds of build:

- A **release build** is the ordinary debug APK from `master`, for daily use. "Upload
  the latest APK" means this one. It goes to `Current/`.
- A **testing build** is an APK of a branch, for the owner to try: an autonomous
  agent's review build or a prototype option. It goes to `Testing/#<n> <short title>/`,
  creating that subfolder if missing, and installs beside the daily app.

An explicit source or destination in the request overrides these defaults.

## Build

1. Use a clean checkout of the requested source; preserve unrelated work by using a
   separate checkout. Record the full commit with `git rev-parse HEAD` and its
   eight-character prefix.
2. From the checkout's root, run the unit tests and the build:

   - Windows: `.\scripts\android-gradle.ps1 testDebugUnitTest assembleDebug <flags>`
   - macOS: `cd android && ./gradlew testDebugUnitTest assembleDebug <flags>`, with
     `JAVA_HOME` pointing at JDK 17 or newer.

   Require a successful exit for both. Build from source rather than picking up an
   existing APK by modification time.

   - **Release build:** no flags. Prototype flags and review application-ID or API
     overrides stay absent, including any inherited from user Gradle properties or
     the environment. Defaults are in `android/app/build.gradle.kts`.
   - **Testing build:** `-PreviewApplicationIdSuffix=.issue<n>`, so it installs as
     `com.timebox.android.issue<n>` beside the daily app and never replaces it or its
     data. Add any prototype flags the issue needs.
3. Use `android/app/build/outputs/apk/debug/app-debug.apk`. Record its byte size and
   SHA-256. Keep the verified artifact unchanged through upload; copy it outside the
   shared build output if another build could replace it.

Release builds must be signed with the shared debug key. An APK signed with a
different key cannot update the installed app, and the owner would have to uninstall
it and lose its local data. Before uploading a release build, check the fingerprint:

```sh
keytool -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey | grep SHA256
```

It must be `72:07:42:75:62:C2:1B:84:0B:75:71:E5:05:9F:E2:C7:7E:FA:43:07:47:4E:20:6D:97:D8:D0:CD:45:BA:2C:E9`.
On a mismatch, stop and report it instead of uploading.

## Upload and verify

4. Copy the APK into the `Timebox` folder that Google Drive for desktop syncs, and
   let it upload. An APK is too large to pass through the Google Drive connector,
   which only accepts file contents inline. On the Mac the folder is
   `~/Library/CloudStorage/GoogleDrive-*/My Drive/Timebox/`. Name the file with the
   upload date in `Asia/Singapore`:
   - **Release build:** first move the APK currently in `Current/` to `Archive/`,
     then copy in `Timebox-master-YYYY-MM-DD-<commit8>.apk` to `Current/`.
     `Current/` then holds exactly one file.
   - **Testing build:** copy `Timebox-#<n>-<label>-YYYY-MM-DD-<commit8>.apk` to the
     issue's `Testing/` subfolder, where `<label>` names the option (`option-a`) or
     is `review`.
5. Sync takes about a minute. Find the file through the Google Drive connector by
   its exact name, retrying for a few minutes, and use the file ID and link it
   returns. Retrieve metadata by that file ID. Confirm the exact filename, MIME type,
   parent folder and byte size match the local artifact. When Drive exposes a
   checksum, compare it with a local hash using the same algorithm. A mismatch is an
   unsuccessful verification; report it instead of calling the upload verified. If
   checksums are unavailable, state that verification covered metadata and size.
6. Return the Drive link, source commit, filename, build/test result and verification
   result. Report any Git push status separately from the upload status.

When an issue closes, delete its `Testing/` subfolder; the approved change ships in
the next release build.

Uploading an APK does not establish that a particular feature works against the
user's running backend. Report feature availability only to the extent separately
verified.
