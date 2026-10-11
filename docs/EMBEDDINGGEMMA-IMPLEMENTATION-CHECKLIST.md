# EmbeddingGemma implementation checklist

The checklist is updated only after the associated code and tests pass.

## Stage 0 — Evaluation baseline

- [x] Privacy-safe fixed corpus covering English, Tamil, Tanglish, OCR, audio, PDF and WhatsApp.
- [x] Action recall, structured field accuracy and citation coverage metrics.
- [x] Release gate rejects unsafe or uncited output.
- [x] Evaluation Lab displays baseline and latest-run status.
- [x] Unit tests pass.

## Stage 1 — Index integrity and rollback

- [x] Snapshot metadata includes model, prompt, source and chunk identity.
- [x] Complete vector tables are persisted and restorable.
- [x] Corrupt or truncated snapshots are rejected before replacement.
- [x] Snapshot publication is atomic through a temporary file and rename.
- [x] Restore activates only after the vector transaction succeeds.
- [x] Unit tests pass.

## Stage 2 — Direct multimodal indexing

- [x] Original camera and screenshot pixels are embedded when the URI is readable.
- [x] Original 16 kHz mono WAV audio is embedded independently of the transcript.
- [x] OCR/transcript text is embedded separately into passage chunks.
- [x] Extracted action candidates receive dedicated vectors.
- [x] Original modality vectors retain a source citation and modality label.
- [x] Unreadable media falls back to text indexing without crashing ingestion.
- [ ] Device verification with a real Tamil screenshot and WAV recording.

## Stage 3 — Document chunk intelligence

- [ ] Page/paragraph/timestamp chunk metadata is complete for every importer.
- [ ] Long WhatsApp conversations retain reply context windows.
- [ ] Exact evidence passages are displayed for every retrieved action.

## Stage 4 — Hybrid retrieval

- [ ] Whole-word, substring, Tamil-normalized and Tanglish keyword retrieval.
- [ ] Semantic and lexical reasons are shown in result details.
- [ ] Duplicate and supersession ranking is evaluated against the fixed corpus.

## Stage 5 — Grounded action extraction

- [ ] Action fields are extracted only from the cited local passage.
- [ ] Dates, times, places and people are independently validated.
- [ ] Conflicting or missing fields remain editable and unverified.

## Stage 6 — Local Gemma reasoning

- [ ] Structured JSON output is schema-validated and citation-required.
- [ ] Malformed or uncited output is rejected into manual review.
- [ ] Model/version and reasoning evidence are recorded in the audit log.

## Release verification

- [ ] Full unit and instrumentation tests pass.
- [ ] Debug APK installed and smoke-tested on the connected device.
- [ ] Release gate report is attached to the build artifact.
