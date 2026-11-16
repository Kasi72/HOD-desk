# Psychiatry HOD Desk — World-Class Secretary Audit

Date: 13 October 2026

This is an evidence ledger, not a feature claim. A green item means the code path and its focused regression exist. Device and language-quality claims remain amber or red until the relevant corpus and connected-phone workflow pass.

## Executive assessment

The app has the right product shape: private local capture, linked evidence, a review gate, a commitment ledger, explicit calendar confirmation, hybrid retrieval and a calm five-tab UI. Its main weakness was trust at the boundaries. Plausible text could be treated as an action, a partial citation could be treated as grounding, multi-step approvals could split, native models could load unverified files, and several background/media paths had unsafe resource assumptions.

This hardening pass moves the app from a convincing prototype toward a controlled pilot. It does not yet justify a general-production or “world class” label. That label requires measured Tamil/Tanglish/OCR/audio accuracy and a repeatable connected-device release gate.

## The supplied e-Nalam notice

The correct representation is:

- Action: **Instruct the listed officials to attend the scheduled e-Nalam demo meeting.**
- People: HMIS Nodal Officer from the Directorate; HMIS Nodal Officer from the pilot location; IT Coordinators from the Directorate.
- Meeting topic: Drug Condemnation and Drug Return in HMIS 3.0.
- Venue: Google Meet, with the cited joining link retained as evidence.
- Conflict: the body says **13 October 2026 at 3:00 PM**, while the invitation says **3:30–4:30 PM**.
- Due date: unresolved. The meeting time is context for the instruction; it is not silently converted into the HOD’s task deadline.
- Not supported: “HOD must attend,” six separate tasks, a title/header task, or a verified 3:00 PM deadline.

The extractor and grounding layer now encode that conservative interpretation and expose the time conflict for confirmation.

## Traffic-light review

### Green — implemented with focused regression coverage

- [x] Strict single-object JSON schema; duplicate keys, coercion, trailing content and malformed output are rejected.
- [x] Every model quote must exist in its claimed source; generated review cues cannot serve as citations.
- [x] Date, time, location and people are revalidated from cited evidence before approval.
- [x] Passive meeting announcements no longer invent HOD attendance.
- [x] Extraction is sentence/local-section first and does not borrow an unrelated document date.
- [x] Conflicting dates/times remain unresolved instead of being silently scheduled.
- [x] Multiple source actions remain separate and diagnostic/error text cannot become a task.
- [x] Approval, edited fields, source review state, commitment, learning and audit are one Room transaction.
- [x] Repeated approval is idempotent and does not duplicate a task or commitment.
- [x] Calendar provider writes carry a durable local marker and reconcile after interrupted inserts.
- [x] Same-title events in another calendar are not mistaken for app-owned writes.
- [x] Index rebuilds use physical staging tables and activate only after source/vector integrity checks.
- [x] Corrupt, wrong-dimension, stale-model and stale-prompt vectors fall back to keyword search.
- [x] Edited or rejected action vectors are excluded immediately, before the next rebuild.
- [x] EmbeddingGemma and Whisper downloads are pinned to exact revisions, sizes and SHA-256 digests.
- [x] Image, PDF and waveform processing have explicit decode/page/window/byte ceilings.
- [x] Whisper input conversion is streaming and preserves resampling phase across decoder buffers.
- [x] Snapshot restore validates counts, dimensions and content checksums before atomic replacement.
- [x] The Review UI exposes separate editable fields, source warnings and explicit confirmation.
- [x] Voice mutations require a confirmation card, one exact record, a current-state recheck and an atomic repository write.
- [x] Evaluation scoring penalizes missing predictions, invented fields and uncited accepted actions.

### Amber — safe behavior exists, but capability/quality is incomplete or unmeasured

- [ ] Native Tamil month/time phrases, arbitrary Tamil names and Tanglish relative dates need corpus-backed parsing.
- [ ] Tamil speech quality must be measured from real phone recordings; installing Whisper medium is not an accuracy result.
- [ ] Tamil screenshot OCR and layout recovery need a labeled, device-captured evaluation set.
- [ ] Clinical quarantine is conservative keyword/identifier logic; OCR-garbled and unfamiliar identifiers can evade it.
- [ ] Gemma instruction quality and latency need an on-device labeled evaluation. Embeddings improve retrieval, not factual authority or clock correctness.
- [ ] External calendar rescheduling currently fails closed when provider and local state cannot be changed together; the complete provider-confirmation UI path needs device validation.
- [ ] Large PDFs and very long audio intentionally keep text retrieval while skipping bounded original-media vectors.
- [ ] Dependency edges are explainable same-subject proposals, not proven causal workflow relationships.
- [ ] Pronoun/coreference resolution is intentionally limited; related context cannot silently populate structured fields.
- [ ] Snapshot binary files are app-private and checksum-validated, but are not separately encrypted like the SQLCipher database.

### Red — release gates not yet satisfied

- [x] Full JVM unit suite passes on the final source tree (`:app:testDebugUnitTest`).
- [x] Debug APK and instrumentation APK build from the final source tree (`:app:assembleDebug`, `:app:assembleDebugAndroidTest`).
- [x] Connected SQLite transaction/integrity tests pass on the target phone (5/5 instrumentation tests).
- [x] Cold start reaches `MainActivity` without an HOD Desk crash; full five-tab/capture fixture traversal remains a manual release gate.
- [ ] Real Tamil audio, Tamil screenshot, PDF and Word fixtures meet explicit field-level accuracy thresholds.
- [ ] Production-signed release APK, checksum, upgrade install and migration test pass without clearing user data. A locally test-signed APK was verified separately.
- [ ] A versioned real-world evaluation corpus covers English, Tamil, Tanglish, OCR damage, audio noise and contradiction/supersession.

## Architecture judgment

EmbeddingGemma should remain the retrieval and relationship engine. It is well suited to finding related screenshots, passages, transcripts and matters. It must not decide obligation, authority, deadlines or calendar writes by similarity alone. Those decisions now pass through deterministic extraction, field-local evidence, conflict detection, human confirmation and transactional execution.

The local instruction model should remain optional and review-only. Its output is useful when it can explain a candidate using exact source IDs and quotes. If the model is missing, malformed, uncited or unsupported by deterministic field checks, the app should show the source and manual editor rather than synthesize a confident task.

## Production acceptance target

Before describing the app as production-grade, require all of the following from the same release candidate:

- crash-free connected workflow across every tab and capture type;
- zero silent task/calendar writes from malformed, uncited or ambiguous evidence;
- zero duplicate task creation under repeated taps, process interruption and worker retry;
- field-level precision reported separately for action, actor, date, time, venue and people;
- false-verified-field rate at or below 2% on the locked evaluation corpus;
- exact rollback equivalence for the active semantic index;
- keyword-only operation when every AI runtime is unavailable;
- clinical fixtures excluded from ordinary search and learning exports;
- upgrade/migration succeeds with existing encrypted user data intact.

## Current release decision

**Controlled pilot only** until every red gate is checked and the amber language/privacy risks have measured evidence. The core trust architecture is now much stronger; the remaining work is validation and a small number of explicit boundary workflows, not additional autonomous behavior.
