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

- [x] Whole-word, substring, Tamil-normalized and Tanglish keyword retrieval.
- [x] Semantic and lexical reasons are shown in result details.
- [x] Duplicate and supersession ranking is evaluated by deterministic regression tests.

## Stage 5 — Grounded action extraction

- [ ] Action candidates are sentence-local; the current extractor adds adjacent units and can attach a neighbouring date/time.
- [x] Action verbs and obligation language are detected for English and common Tamil cues.
- [x] Dates, times, locations and people are extracted for supported English formats.
- [ ] Owner, reference number and recurrence extraction are not implemented.
- [ ] Related chunks are not yet used specifically for pronoun or follow-up resolution.
- [ ] Government-order and delegation intents are not separate ranking intents.
- [ ] Ambiguous candidates are not persisted with a dedicated `needs_confirmation` status.
- [ ] OCR/transcription failure messages can still contain words such as “review” or “required” and must be blocked before extraction.
- [x] Multiple independent units generally produce separate candidates.
- [ ] Multiple actions within one sentence are not yet split reliably.

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
