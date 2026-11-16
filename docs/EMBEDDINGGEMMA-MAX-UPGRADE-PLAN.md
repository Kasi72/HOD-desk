# Psychiatry HOD Desk — EmbeddingGemma Maximum-Use Upgrade Plan

This plan upgrades the existing app in controlled stages. It preserves the current repositories, approval workflow, privacy boundary and calendar confirmation flow. Each stage ends with a measurable acceptance gate before the next stage begins.

EmbeddingGemma 2 is used as the multimodal memory, retrieval and ranking engine. A separate constrained local Gemma instruction model performs structured synthesis. Embeddings never create tasks or calendar events by themselves.

## Release rules

- **Patch:** bug fixes, index safety, visual refinement and performance improvements.
- **Minor:** a completed capability such as multimodal search, matter linking or briefs.
- **Major:** changes to the approval, calendar, privacy or data architecture.
- Every stage must leave the app launchable and able to fall back to keyword search.
- No stage may bypass explicit approval before task creation or calendar writing.

## Stage 0 — Baseline and release gate

**Goal:** establish proof that later AI changes improve the app.

### Work

1. Create an anonymised evaluation set of 100–300 examples:
   - Tamil audio;
   - Tanglish messages;
   - English official letters;
   - screenshots and camera photographs;
   - PDFs, Word files and plain text;
   - dates, times, places, officials and reference numbers.
2. Label action, date, time, location, people, owner and citation span.
3. Exclude patient identifiers and clinical material from the set.
4. Record baseline search recall@10, action recall, field accuracy, Tamil CER/WER, duplicate rate and false-verified-field rate.
5. Add a release-gate report that compares baseline and current results.

### Acceptance gate

- A fixed test run produces the same results twice.
- Every proposed action has a labelled evidence span.
- The release gate blocks a change that increases false verified fields above the agreed threshold.

## Stage 1 — Index integrity and safe rebuilds

**Goal:** make every future multimodal rebuild safe and reversible.

### Work

1. Add source fingerprints from URI, content hash, media metadata and extractor version.
2. Add model version, prompt version, modality, chunk number and embedding dimension to each vector row.
3. Mark vectors stale when any fingerprint, model or prompt version changes.
4. Build a new index beside the active index.
5. Run self-check vectors containing English, Tamil and Tanglish phrases.
6. Activate the new index only after integrity and evaluation gates pass.
7. Persist snapshots containing model version, prompt version, source IDs, chunk count and checksums.
8. Restore vectors atomically and verify counts/checksums before activation.

### Acceptance gate

- A forced model-version change rebuilds only stale records.
- An interrupted rebuild leaves the active index usable.
- Rollback restores the previous search results exactly.
- Invalid dimensions or checksums force keyword fallback.

## Stage 2 — Direct multimodal indexing

**Goal:** use EmbeddingGemma 2 with original image and audio data, not text alone.

### Work

1. Extend the semantic index schema with `modality` and `evidence_locator`.
2. Embed:
   - original screenshot/camera image;
   - OCR text;
   - audio waveform segments;
   - transcript segments;
   - document page previews;
   - extracted action candidates.
3. Store image regions, document pages and audio timestamps beside each vector.
4. Use the Universal Embedder path for text, image and audio inputs. Google documents that EmbeddingGemma 2 projects these modalities into one shared vector space. [Universal Embedder Android guide](https://developers.google.com/edge/mediapipe/solutions/retrieval/universal_embedder/android)
5. Queue heavy media work in resumable background jobs.

### Acceptance gate

- A text query retrieves a relevant screenshot and audio recording.
- An image query retrieves related text sources.
- An audio segment retrieves a related document.
- Every result opens the original evidence location.

## Stage 3 — Document and conversation chunking

**Goal:** retrieve the exact passage instead of one vector for an entire source.

### Work

1. Split PDFs and Word files by page, heading and paragraph.
2. Split long transcripts into timestamped speech windows.
3. Split WhatsApp exports into short conversation windows that retain the preceding message when a reply is ambiguous.
4. Generate one source summary vector, passage vectors and action-candidate vectors.
5. Preserve page, paragraph, message index and timestamp citations.
6. Use overlap between adjacent chunks so dates and follow-up replies are not separated.

### Acceptance gate

- Search opens the exact supporting page or timestamp.
- `Ok sir, will do` remains linked to the preceding instruction.
- A long document returns the correct passage rather than an unrelated page.

## Stage 4 — Hybrid retrieval and query planning

**Goal:** combine exact official-work matching with semantic meaning.

### Work

1. Detect query entities before search:
   - GO/reference numbers;
   - names and officials;
   - dates and times;
   - places;
   - department terms;
   - Tamil/Tanglish language.
2. Run whole-word search, character-sequence search and transliteration search.
3. Run EmbeddingGemma semantic search over source, chunk, image and audio vectors.
4. Fuse scores with configurable weights.
5. Apply exact boosts for document numbers, dates and named officials.
6. Apply source-type filters and clinical exclusion before ranking.
7. Show match reasons on every result.

### Acceptance gate

- Exact GO-number searches outrank broad semantic matches.
- Tamil suffix and OCR variations remain searchable.
- A query such as “DME meeting screenshot” returns the correct image, message and related document.
- Clinical sources never appear in ordinary results.

## Stage 5 — Action candidate and field extraction

**Goal:** extract complete, conservative action candidates from every modality.

### Work

1. Detect action verbs and obligation language.
2. Extract date, time, location, people, owner, reference number and recurrence.
3. Keep extraction sentence-local before considering document-level context.
4. Use related chunks only to resolve pronouns or follow-up replies.
5. Create candidates for meetings, appointments, deadlines, teaching duties, resident duties, government orders and follow-ups.
6. Rank candidates against separate official-work intents.
7. Mark ambiguous candidates as `needs_confirmation`, never as verified.

### Acceptance gate

- A date from one paragraph cannot attach to an unrelated action in another paragraph.
- Multiple actions in one source remain separate.
- No action is produced from a transcription or OCR error message.

## Stage 6 — Grounded local reasoning

**Goal:** turn retrieved evidence into strict, reviewable structured output.

### Work

1. Add a local Gemma instruction model through LiteRT-LM.
2. Pass only the top cited chunks and modality evidence to the model.
3. Require strict JSON:

```json
{
  "action": "",
  "date": "",
  "time": "",
  "location": "",
  "people": [],
  "owner": "",
  "source_quotes": [],
  "source_ids": [],
  "confidence": 0.0,
  "ambiguities": [],
  "conflicts": []
}
```

4. Reject malformed or uncited output.
5. Re-run deterministic date/time/location extraction on every cited quote.
6. Check weekday/date consistency and newer-source conflicts.
7. Leave failing fields blank in the approval form.

### Acceptance gate

- Malformed output cannot reach the task-creation callback.
- Every verified field exists in its cited quote.
- Conflicts produce a visible review state.

## Stage 7 — Matter graph and duplicate prevention

**Goal:** consolidate repeated references to one official matter.

### Work

1. Link sources using embedding similarity, entities, dates, locations and reference numbers.
2. Build a matter timeline containing sources, actions, tasks, changes and events.
3. Detect duplicate imports by fingerprint and semantic similarity.
4. Detect supersession: new date, changed venue, changed owner or cancellation.
5. Present `Create new`, `Update existing` and `Keep separate` proposals.

### Acceptance gate

- Re-importing the same file does not create a duplicate task.
- A changed meeting time proposes an update to the existing task.
- Every matter page shows the strongest evidence first.

## Stage 8 — Secretary ranking and personalised learning

**Goal:** make retrieval increasingly useful for this HOD without hidden automation.

### Work

1. Record local corrections: edited wording, confirmed fields, rejected candidates and delegation choices.
2. Learn preferred abbreviations, officials, venues, Tamil spellings and task phrasing.
3. Add confidence decay so old corrections do not dominate forever.
4. Apply the learning profile to retrieval and candidate ranking.
5. Add controls to inspect, reset and export the learning profile.
6. Keep clinical identifiers outside the profile.

### Acceptance gate

- A correction improves the next ranking for similar work.
- Reset returns ranking to baseline.
- No patient name or clinical identifier enters learned preferences.

## Stage 9 — Secretary briefs and follow-up intelligence

**Goal:** provide a cited executive-secretary view without autonomous sending.

### Work

1. Generate morning, meeting-preparation, midday-risk and evening-close briefs.
2. Include only active, non-clinical, evidence-backed items.
3. Add deadline proximity, authority, unresolved status and conflict signals.
4. Detect commitments owed by the HOD and promises owed to the HOD.
5. Generate editable follow-up drafts in English, Tamil or Tanglish.
6. Require review before copy, share or send.
7. Audit every draft, copy, share and dismissal action.

### Acceptance gate

- Brief counts match Today, Review and Tasks.
- Deleted/completed sources never appear in the brief.
- No message is sent automatically.

## Stage 10 — Travel-aware planning and replanning

**Goal:** turn approved tasks into realistic daily proposals.

### Work

1. Store venue travel times and working-hour constraints.
2. Preserve fixed OPD, ward, ECT and teaching blocks.
3. Estimate effort from task type and completed-task history.
4. Detect overload before proposing a schedule.
5. Propose travel buffers and protected focus time.
6. Replan when a meeting moves, a task is completed or a block overruns.
7. Show a before/after schedule diff.
8. Require Accept one, Accept all or Reject before calendar writing.

### Acceptance gate

- Fixed clinical blocks are never overwritten.
- Travel time is visible in the proposal.
- Overload produces a warning and alternatives.
- Calendar writes remain explicit and idempotent.

## Stage 11 — Voice secretary with constrained tools

**Goal:** connect Whisper/transcription to real secretary actions safely.

### Work

1. Transcribe the voice request locally.
2. Retrieve supporting sources with EmbeddingGemma.
3. Parse the request into a constrained intent.
4. Permit read-only commands immediately: search, show meetings, show waiting items.
5. Turn mutating commands into confirmation cards: create, update, move, delegate or schedule.
6. Execute only approved tool calls.
7. Show citations and the affected record after execution.

### Acceptance gate

- “Show my meetings tomorrow” never writes data.
- “Move the DME meeting to 3 PM” produces a reviewable proposal.
- Every executed command is audited.

## Stage 12 — Performance, observability and device validation

**Goal:** make the system dependable on the actual phone.

### Work

1. Use hardware acceleration where supported; LiteRT-LM provides GPU/NPU paths and multimodal model support. [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview)
2. Cache fingerprints, OCR, waveform features and embeddings.
3. Batch text chunks and process heavy media while charging when configured.
4. Add resumable WorkManager jobs with progress states.
5. Record latency, memory, battery and queue-failure metrics locally.
6. Add startup self-test and weekly integrity check.
7. Run regression on the connected S26 Ultra/target device:
   - cold start;
   - all five tabs;
   - every capture type;
   - Tamil audio;
   - Tamil screenshot;
   - PDF/Word import;
   - review approval/edit/delegate/reject;
   - calendar proposal/confirmation;
   - delete/Undo;
   - clinical quarantine;
   - index rebuild/rollback.

### Acceptance gate

- No crash when switching tabs or opening any card.
- Interrupted jobs resume without duplicates.
- Search remains usable when AI is unavailable.
- Full evaluation and connected-device suite passes before release.

## Delivery sequence

1. Stages 0–1: Patch release — baseline and index safety.
2. Stages 2–4: Minor release — multimodal memory and hybrid retrieval.
3. Stages 5–7: Minor release — grounded extraction and matter consolidation.
4. Stages 8–9: Minor release — learning and secretary briefs.
5. Stages 10–11: Major release — scheduling and tool-using voice secretary.
6. Stage 12: Required for every release candidate.

## Definition of done

The upgrade is complete only when:

- every source modality has an original and text/vector representation;
- every AI suggestion has exact citations;
- every date, time, location and person is field-validated;
- duplicate and superseded work is detected;
- clinical content is quarantined;
- corrections improve future ranking locally;
- secretary actions require confirmation;
- calendar writes are explicit, reconciled and reversible;
- evaluation gates pass on Tamil, Tanglish and English cases;
- the connected-device regression suite passes.
