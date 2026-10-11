# Psychiatry HOD Desk — Secretary Completion Implementation Plan

> For agentic workers: execute each task with tests and a commit after it passes.

**Goal:** Complete the remaining executive-secretary capabilities while preserving local processing, explicit approval, clinical quarantine and evidence-backed outputs.

**Architecture:** Extend Room and Compose with durable commitment, matter, follow-up, planning and evaluation records. Keep EmbeddingGemma for retrieval/ranking, local Gemma behind strict JSON/citation validation, Whisper/OCR for conversion, and deterministic validators for dates, ownership, conflicts and approval gates.

## Global constraints

- No task, calendar event, delegation, export or outbound share without explicit confirmation.
- Clinical/T3/T4 sources remain outside search, reasoning, learning, tasks and calendar output.
- Every AI-derived field retains exact evidence citations and field-level confidence.
- Embeddings, reasoning records, corrections and audit records remain encrypted and on-device.
- Heavy work respects charging and low-battery policy.
- Learning never rewrites source evidence.

## Task 1 — Durable commitment ledger

Create commitments/CommitmentEntities.kt; modify HodDeskDatabase.kt, HodDeskDao.kt and HodDeskRepository.kt; test commitments/CommitmentLedgerTest.kt. Produce CommitmentEntity with sourceId, taskId, direction, owner, counterparty, summary, dueAt, status, evidence, confidence and timestamps. Produce CommitmentLedger.upsertFromTask, pendingForMe, waitingOnOthers and overdue. Use OWED_BY_ME and OWED_TO_ME.

- [ ] Add Room schema and migration.
- [ ] Test ownership, completion, overdue, waiting and deletion cascades.
- [ ] Synchronize task edits and completion into the ledger.
- [ ] Commit.

## Task 2 — Unified triage and secretary state

Create secretary/SecretaryState.kt; modify TriageScorer.kt and SecretaryIntelligence.kt; test SecretaryStateTest.kt. Produce SecretaryStateSnapshot with action, meeting, deadline, delegation, waiting, duplicate, conflict, sensitive and information-only counts, and SecretaryStateBuilder.build(sources, tasks, events, commitments, now).

- [ ] Test every triage class and exclusion rule.
- [ ] Replace duplicated Today/Review counters with the shared snapshot.
- [ ] Add source-level priority reasons.
- [ ] Verify the brief equals visible actionable records.

## Task 3 — Matter case-file and change workflow

Modify MatterGrouper.kt; create ui/screens/MatterScreen.kt; modify ReviewScreen.kt and MainActivity.kt; test MatterTimelineTest.kt. Produce MatterTimeline and ActionChangeProposal with field changes, old/new evidence and citations. Add Accept change, Keep existing and Review evidence actions.

- [ ] Test supersession ordering, duplicate grouping and conflicts.
- [ ] Build a timeline from relations, tasks, events and commitments.
- [ ] Show cited evidence beside every proposed change.
- [ ] Require confirmation and verify no duplicate task is created.

## Task 4 — Personalised secretary memory

Create learning/SecretaryLearning.kt; modify HodDeskDao.kt, HodDeskRepository.kt and HybridRetriever.kt; test SecretaryLearningTest.kt. Produce LearningProfile with sender, authority, phrase, duration and venue priors, plus SecretaryLearning.profile(now) and rerank(candidate, profile).

- [ ] Test accepted corrections improving equivalent candidates.
- [ ] Test rejected actions reducing false positives.
- [ ] Add bounded decay, minimum samples, inspection and reset controls.
- [ ] Verify patient identifiers never enter the profile.

## Task 5 — Follow-up drafts

Create followup/FollowUpEngine.kt and ui/screens/FollowUpScreen.kt; modify MainActivity.kt; test FollowUpEngineTest.kt. Produce FollowUpCandidate and FollowUpEngine.candidates/draft. Drafts are editable and copy/share only after confirmation; never send automatically.

- [ ] Test elapsed thresholds, newer replies and completion suppression.
- [ ] Generate Tamil, English and Tanglish drafts with citations.
- [ ] Add approve, edit, copy, share and dismiss controls.
- [ ] Audit every export/share action.

## Task 6 — Voice secretary mode

Create secretary/VoiceSecretary.kt and ui/screens/SecretaryScreen.kt; modify MainActivity.kt; test VoiceSecretaryTest.kt. Produce SecretaryIntent(kind, query, parameters, requiresConfirmation), normalizing Tamil, English and Tanglish commands for search, waiting, meetings, planning, moves, creation and updates.

- [ ] Test equivalent multilingual commands.
- [ ] Connect Whisper transcripts to the parser.
- [ ] Show cited answers and proposed changes.
- [ ] Require confirmation for every consequential mutation.

## Task 7 — Replanning and overload

Modify SmartScheduling.kt; create scheduling/ReplanningEngine.kt; modify CalendarScreen.kt; test ReplanningEngineTest.kt. Produce ReplanProposal with changes, displaced tasks, conflicts and rationale. ReplanningEngine.propose returns proposals only and never writes directly.

- [ ] Test overruns, travel gaps, clinical blocks and priority displacement.
- [ ] Add historical effort estimates with a safe default.
- [ ] Add accept-all, accept-one and reject controls.
- [ ] Verify idempotent calendar writes and reconciliation.

## Task 8 — Briefs and secretary control centre

Create ui/screens/SecretaryControlCentre.kt; modify MeetingPreparation.kt, TodayScreen.kt and MainActivity.kt; test SecretaryBriefTest.kt. Produce MorningBrief, EveningClose and SecretaryDashboardState containing priorities, meetings, waiting, conflicts, overdue, changed instructions, confidence warnings, AI health and battery/processing state.

- [ ] Test empty, overloaded, conflict and clinical-only days.
- [ ] Add configurable notification timing and quiet hours.
- [ ] Link every card to evidence and its responsible action.
- [ ] Remove stale duplicated counters.

## Task 9 — Evaluation lab

Create evaluation/EvaluationStore.kt and ui/screens/EvaluationScreen.kt; modify EvaluationMetrics.kt; test EvaluationStoreTest.kt. Produce EvaluationExample and EvaluationRun with baseline/current deltas, false-verified rate and timestamp. Track CER/WER and field accuracy.

- [ ] Test anonymized correction/rejection replay and reports.
- [ ] Add baseline/current comparisons and regression thresholds.
- [ ] Block release when false verified fields exceed the safety threshold.

## Task 10 — Snapshots, rollback, battery and privacy controls

Create semantic/IndexSnapshotManager.kt and privacy/ProcessingPolicy.kt; modify SourceSemanticIndexer.kt and SettingsScreens.kt; test IndexSnapshotManagerTest.kt. Produce IndexSnapshot and createSnapshot, activateSnapshot, rollbackSnapshot and validateSnapshot. Produce ProcessingPolicy.canRun(batteryPercent, charging, workType).

- [ ] Test atomic activation and rollback after validation failure.
- [ ] Validate model/prompt versions, dimensions, checksums and source eligibility.
- [ ] Add charging-only heavy work and pause-below-20-percent settings.
- [ ] Show active snapshot, rollback point, health, retention and clinical boundary.
- [ ] Verify clinical exclusion before every activation.

## Release gates

- [ ] Unit, Room migration and Compose approval tests pass.
- [ ] Tamil/Tanglish evaluation meets agreed CER/WER and false-verification thresholds.
- [ ] Connected-device regression passes.
- [ ] No clinical source appears in search, reasoning, learning, tasks, calendar or evaluation output.
- [ ] Interrupted calendar writes reconcile correctly.
- [ ] Signed APK is built, checksum recorded and installed.
- [ ] Versioning follows patch for fixes/refinements, minor for features and major for architectural/workflow changes.

Recommended order: Tasks 1–4 establish durable state and trustworthy learning; Tasks 5–8 expose secretary behavior; Tasks 9–10 validate the complete pipeline before release.
