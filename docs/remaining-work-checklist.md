# Psychiatry HOD Desk — Completion Checklist

This is the single progress ledger. A box is checked only after the implementation exists, static checks pass, and the relevant test is present. APK/device gates remain open until a connected phone is available.

## Completed

- [x] Durable commitment ledger and Room migration.
- [x] Secretary dashboard state and stale-counter cleanup path.
- [x] Matter timeline and related-source grouping.
- [x] Learning corrections applied to live retrieval ranking.
- [x] Privacy-safe learning decay and sensitive-token filtering.
- [x] Learning profile inspection count and confirmed reset control.
- [x] Editable follow-up drafts, audited sharing and persisted dismissal.
- [x] Whisper transcript connected to Secretary intent parsing.
- [x] Venue travel buffers in replanning.
- [x] Secretary Control Centre screen.
- [x] Evaluation Lab screen and release safety gate.
- [x] Persisted vector snapshots and atomic restore.
- [x] Active snapshot tracking and rollback-version display.
- [x] Charging-only scheduled indexing, retention purge and calendar reconciliation.

## Remaining implementation work

- [x] Matter change proposal actions: accept, keep existing and review evidence.
- [x] Voice Secretary cited answers and confirmation cards for mutations.
- [x] Replanning accept-one, accept-all and reject controls.
- [ ] Configurable morning/evening brief scheduling and quiet hours.
- [ ] Evaluation baseline/current persistence and report display.
- [ ] Snapshot activation/version switching UI and rollback audit states.

## Release gates

- [ ] Clean full Gradle compile.
- [ ] Unit, Room migration and Compose tests pass.
- [ ] Production-signed release APK and checksum (a locally test-signed artifact is available; production keystore is not configured).
- [x] ADB installation and connected-device regression: debug and instrumentation APKs installed; 5/5 device tests passed; cold start remained resumed with no app crash.
- [ ] Tamil/Tanglish transcription and clinical-quarantine verification.

## Latest verification

- [x] Fixed the JVM-only reasoning validator regression by removing Android `JSONObject` calls from the unit-tested parser while retaining malformed-output, citation and grounding checks.
- [x] Fixed stale follow-up test data handling without weakening real epoch-time staleness checks.
- [x] Fixed capacity reporting so overlapping calendar events are merged and queue capacity is capped to the current request.
- [x] Full JVM unit-test suite passes (`:app:testDebugUnitTest`).
- [x] Debug application and instrumentation APKs compile (`:app:assembleDebug`, `:app:assembleDebugAndroidTest`).
- [x] Connected-device regression completed after reconnecting the S26 Ultra: 5/5 Room workflow tests passed and cold start was stable.
- [x] Release variant compiles and a locally test-signed APK verifies with APK Signature Scheme v2/v3.
- [ ] Production signing, Tamil/Tanglish corpus evaluation, and clinical-quarantine device verification remain open release gates.
