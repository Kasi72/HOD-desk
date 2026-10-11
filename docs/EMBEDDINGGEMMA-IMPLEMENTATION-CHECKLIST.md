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
- [x] Page markers and audio timestamp ranges are retained when present in imported text.
- [ ] Long WhatsApp conversations retain reply context windows.
- [ ] Exact evidence passages are displayed for every retrieved action.

## Stage 4 — Hybrid retrieval

- [x] Query planning detects references, dates, times, places, officials, departments, Tamil and Tanglish cues before ranking.
- [x] Whole-word, substring, character-ngram, Tamil-normalized and Tanglish retrieval are combined.
- [x] EmbeddingGemma semantic search covers source, chunk, action, image and audio vectors.
- [x] Semantic, lexical and exact evidence scores use configurable fusion weights.
- [x] Exact references, dates, times, named officials, places and department terms receive deterministic boosts.
- [x] Clinical T3/T4 sources are rejected at the retriever boundary; optional source-type filters are supported.
- [x] Every match carries reasons and, when available, the strongest chunk citation and modality.
- [x] Exact-reference ranking, Tamil suffix/OCR recovery and clinical exclusion are covered by deterministic regression tests.

## Stage 5 — Grounded action extraction

- [x] Action candidates are sentence-local; adjacent paragraphs are never merged. Abbreviated references such as `GO No. 214/2026` remain intact.
- [x] Action verbs and obligation language are detected for English and common Tamil cues.
- [x] Dates, times, locations and people are extracted for supported English formats, with sentence-local evidence.
- [x] Owner, reference number and recurrence fields are extracted and persisted through the Room migration.
- [x] Related chunks can be supplied explicitly and are consulted only for pronouns and follow-up wording.
- [x] Government-order and delegation candidates have separate categories and semantic ranking intents.
- [x] Ambiguous candidates are persisted with `needs_confirmation` and remain in Review until confirmed.
- [x] OCR/transcription failure messages are rejected before action extraction and import persistence.
- [x] Multiple independent units generally produce separate candidates.
- [x] Multiple independent actions within one sentence are split at explicit conjunctions or clause boundaries; non-action context remains attached.

## Stage 6 — Local Gemma reasoning

- [x] Full required schema is implemented (`source_quotes`, `source_ids`, `owner`, and `conflicts`).
- [x] A local LiteRT-LM Gemma instruction model is available and invoked from Review when installed.
- [x] Malformed or uncited output is rejected before the reasoning callback.
- [x] Field-level grounding blanks unsupported date, time, location, and people values.
- [x] Only bounded top action-relevant chunks and modality evidence are sent to the model.
- [x] Deterministic date/time/location re-extraction and weekday consistency checks clear failing fields.
- [x] Newer-source conflicts are attached as deterministic review conflicts and shown in Review.
- [x] Conflict state is visible before approval; conflicts remain in the cited review record.
- [x] Model/version and reasoning evidence are recorded in the audit log.

## Stage 7 — Matter graph and duplicate linking

- [x] Related, duplicate and superseding sources are grouped into one matter.
- [x] Latest instruction and superseded source IDs are surfaced.
- [x] Matter timelines connect sources, tasks, events and unresolved questions.
- [x] Exact source fingerprints prevent repeat imports from creating duplicate source/task work.
- [x] Multi-signal linking uses semantic similarity plus reference numbers, dates and venue/location cues.
- [x] Supersession detects explicit revisions, rescheduling, ownership/venue changes and cancellations.
- [x] Matter review presents Create new, Update existing and Keep separate decisions with audit records.
- [x] Strongest current evidence is ordered before superseded evidence in the matter timeline.
- [x] Unit tests pass.

## Stage 8 — Personalised learning

- [x] User edits are stored as field-level corrections.
- [x] Repeated corrections influence live semantic retrieval ranking.
- [x] Corrections decay over time and are capped to a bounded boost.
- [x] Sensitive and clinical identifiers are excluded from the learning profile.
- [x] Reset control removes learned preferences and records an audit entry.
- [x] Unit tests pass.

## Release verification

- [ ] Full unit and instrumentation tests pass.
- [ ] Debug APK installed and smoke-tested on the connected device.
- [ ] Release gate report is attached to the build artifact.
