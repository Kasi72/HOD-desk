# Psychiatry HOD Desk

Psychiatry HOD Desk is a privacy-first Android workspace for a government psychiatry department head. It turns scattered official work into a reviewable, evidence-backed workflow:

```text
Capture or import
  → OCR, decode or transcribe on device
  → Extract action candidates
  → Classify sensitivity and urgency
  → Index with EmbeddingGemma
  → Ground dates, times, people and locations in source evidence
  → Review and approve one action
  → Create a task
  → Confirm calendar scheduling
```

The application is designed for Samsung Galaxy Ultra-class phones and works offline for its core capture, transcription, indexing and task-review flows.

## What the app handles

Psychiatry HOD Desk brings together the material that normally arrives through separate channels:

- WhatsApp exported chats and copied official messages
- Call recordings and audio transcripts, including Tamil-first speech
- Screenshots and camera photographs of documents or slides
- PDFs, Word files and plain-text official orders
- Gmail attachments shared into the Android app
- Meetings, appointments, teaching schedules, duty rosters and follow-ups

Every imported source remains linked to its original evidence. The app does not silently create tasks from a semantic match or an unverified model output.

## Main UI

The app uses an iOS-style design language (grouped inset lists, squircle controls, a floating tab bar) with five tabs and a separate Capture button:

| Tab | Purpose |
|---|---|
| **Today** | Morning brief: what must be done today, today's schedule including fixed clinical time, review and waiting counts |
| **Review** | Evidence-first decision queue: each field is checked against the source, then Approve, Edit, Delegate or Reject |
| **Tasks** | Mine / Delegated / Done, grouped by Overdue, Today, This Week, Later; full-screen task detail with history |
| **Calendar** | Week strip and hour timeline; Plan My Day and Protect Time propose blocks, show clashes, and write only after confirmation |
| **Library** | Hybrid Tamil/English search with "why matched" reasons and multi-select |

Settings opens from the avatar on Today. Deletes can be undone for 6 seconds. UI code lives in `app/src/main/java/com/hoddesk/app/ui/` (`theme/` tokens and `SquircleShape`, `components/`, `screens/`).

## Intelligence pipeline

### Source processing

Each source passes through the appropriate local adapter:

- **Images:** on-device OCR with the original URI retained.
- **Audio:** M4A/AAC decoding to 16 kHz PCM WAV, followed by offline Tamil-first transcription.
- **Documents:** PDF, Word and text extraction with source and section context.
- **WhatsApp:** plain-text and ZIP export parsing with message context.

### Action extraction

The extractor looks for official-work cues such as:

- attend, join, submit, send, review, approve, collect and report
- meeting, appointment, conference, Zoom, Teams and ward duties
- dates, times, venues, officials and document numbers
- deadlines, follow-ups, delegation and pending responses

Candidates retain their original evidence and are consolidated when the same action appears across multiple sources.

### EmbeddingGemma

EmbeddingGemma is used for on-device semantic indexing and retrieval. The current implementation includes:

- Versioned task-steered prompt registry
- Source and chunk embeddings
- Vector dimension, norm, NaN and collapse validation
- Model and prompt fingerprints
- Index-health inspection for stale or invalid vectors
- Semantic search across source chunks
- Hybrid retrieval combining semantic similarity with:
  - exact phrases
  - document numbers
  - OCR-tolerant matching
  - Tamil character n-grams
  - normalized Tanglish-friendly tokens
- Duplicate, related and explicit supersession relationships

The search layer is retrieval and ranking infrastructure. It does not independently authorize tasks or calendar writes.

### Local instruction reasoning

When an approved LiteRT-LM instruction model is installed, the app can reason over retrieved evidence and return structured fields:

```json
{
  "action": "Attend DME review",
  "category": "meeting",
  "date": "19 September 2026",
  "time": "2 PM",
  "location": "Erode Collectorate",
  "people": ["DME"],
  "confidence": 0.91,
  "ambiguities": [],
  "citations": ["Attend DME review at 2 PM in Erode Collectorate"]
}
```

The validator rejects malformed JSON, missing citations, unsupported confidence values and fields that cannot be grounded in the cited evidence. Unsupported fields are cleared and returned as clarification requirements.

## Trust and safety controls

### Human approval

The workflow is intentionally review-first:

1. Source appears in Inbox.
2. The user opens Review insight.
3. Evidence, extracted fields, confidence and ambiguity are inspected.
4. One action is approved.
5. Only then is a task created.

### Calendar safety

Calendar writes are proposals first. Existing overlapping events are detected and shown before explicit confirmation. The app never silently overwrites a provider event.

### Clinical privacy

The privacy layer supports:

- Global consent before sensitive imports
- Separate consent for calls, messages, screenshots/camera and documents/Gmail
- Phone and email redaction before storage and indexing
- Sensitivity tiers, with clinical/special content excluded from ordinary semantic search
- Keystore-backed encrypted database access
- Retention policy configuration
- Append-only audit entries for important workflow transitions

Clinical deployment still requires institutional security, ethics and legal review before connecting sensitive production sources.

## Settings surface

The Settings experience includes:

- **Privacy & Data:** consent, redaction, source locks and retention
- **On-Device AI:** semantic search, Tamil speech, image reading, local reasoning model and technical details
- **Activity Log:** encrypted audit history with export to a user-chosen file
- **How to Import:** WhatsApp, screenshots, camera, calls, documents and Gmail guidance
- **Department Profile:** fixed clinical time (OPD, ward rounds, ECT, teaching), team, venues and travel time, authority order
- **Calendar:** destination calendar, working hours and the share of the day kept free
- **Clinical Vault:** clinical and special-category items, opened with the device screen lock
- **Planned:** notification scheduling (morning brief, interruptions, meeting prep) and battery-aware background processing

## Project structure

```text
app/src/main/java/com/hoddesk/app/
├── calendar/       Calendar provider scheduling and conflict checks
├── data/           Room entities, DAO, migrations and encrypted database
├── evaluation/     CER, WER, Recall@K and evaluation helpers
├── imports/        OCR, document, WhatsApp and audio adapters
├── privacy/        Consent, redaction, sensitivity and database key handling
├── reasoning/      Local instruction-model storage, prompts and grounding
├── repository/     Workflow persistence and action consolidation
├── scheduling/     Smart time-block proposals
├── semantic/       EmbeddingGemma runtime, prompts, integrity and retrieval
├── triage/         Official-work relevance and urgency scoring
├── viewmodel/      Compose-facing state and operations
└── workflow/       Reducer and workflow state transitions
```

## Build requirements

- Windows, macOS or Linux
- Android Studio or the bundled Gradle wrapper
- JDK compatible with Gradle 8.14.3
- Android SDK with platform tools and API 35 installed
- A connected Android device for device validation

The repository is:

```text
https://github.com/Kasi72/HOD-desk
```

Build the debug APK from the project root:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:packageDebug --offline
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If the build machine has limited C: drive space, place Gradle temporary files on another drive:

```powershell
$env:TEMP = "D:\gradle-tmp"
$env:TMP = "D:\gradle-tmp"
./gradlew.bat --no-daemon `
  "-Djava.io.tmpdir=D:\gradle-tmp" `
  "-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC" `
  "-Dorg.gradle.workers.max=1" `
  :app:testDebugUnitTest :app:packageDebug --offline
```

## Testing

The unit suite covers:

- Ingest → review → approve → schedule → complete reducer flow
- Tamil and temporal cue parsing
- Sensitivity classification and clinical filtering
- Action extraction and consolidation
- Field-level citation grounding
- Embedding vector integrity and index health
- Hybrid semantic/lexical retrieval
- Duplicate, related and superseding-source detection
- Evaluation metrics

Run the tests with:

```powershell
./gradlew.bat :app:testDebugUnitTest --offline
```

Before a clinical pilot, add Compose UI tests and a real Samsung device pass covering:

- Offline Tamil M4A transcription
- Screenshot and camera OCR
- WhatsApp ZIP import
- Calendar provider conflicts
- Reboot recovery of encrypted Room storage
- Battery impact during transcription and indexing
- Permission denial and privacy-scope changes

## Installing on a connected phone

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The device must show as `device`, not `unauthorized`. Unlock the phone, enable USB debugging and approve the RSA prompt when requested.

## Model packaging

The application keeps large model files outside the APK where possible. The production deployment should package or provision:

- The approved EmbeddingGemma mobile runtime and tokenizer/tensor contract
- The verified EmbeddingGemma model export
- The approved instruction-tuned LiteRT-LM model
- Model checksums and a deployment manifest

Model files must be obtained under their applicable licenses and approved for the intended government/clinical deployment. Do not commit private tokens, gated model files, database keys or user source data to Git.

## Versioning

The project uses:

- **Patch:** bug fixes, tests and visual refinements
- **Minor:** backward-compatible feature additions or workflow improvements
- **Major:** architectural, schema or workflow changes that require migration or deployment review

The current source line contains the implemented workflow, privacy, semantic retrieval, grounding, consolidation and Settings layers. Check the APK filename and `app/build.gradle.kts` for the exact version.

## Deployment principles

Psychiatry HOD Desk is an administrative workflow assistant. It should not be used as an autonomous clinical decision-maker. Human review remains required for official actions, schedules, patient-related content and any information that could affect care, staffing or government reporting.

Before production deployment:

1. Complete institutional legal, ethics and information-security review.
2. Define retention and backup policy.
3. Approve the model files and license terms.
4. Run the evaluation set for Tamil, Tanglish, dates, numbers, names and locations.
5. Validate the device build on the target Samsung fleet.
6. Document rollback, key recovery and incident procedures.

## Related documentation

- [Complete UI walkthrough](Psychiatry-HOD-Desk-UI-Walkthrough.md)
- `app/src/main/java/com/hoddesk/app/semantic/` — embedding and retrieval implementation
- `app/src/main/java/com/hoddesk/app/reasoning/` — local reasoning and evidence grounding
- `app/src/main/java/com/hoddesk/app/privacy/` — consent, redaction and sensitivity controls
