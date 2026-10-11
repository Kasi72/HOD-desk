# Psychiatry HOD Desk — Complete UI Walkthrough

**Audience:** product, UX, UI architecture, accessibility and QA teams  
**Product:** Psychiatry HOD Desk  
**Platform:** Android, Compose UI  
**Design direction:** iOS-inspired, calm, professional, evidence-first executive-secretary workspace  
**Document purpose:** describe every current user-facing surface, navigation path, state and interaction so the UI team can redesign the product without missing a workflow.

## 1. Product model

Psychiatry HOD Desk receives official work from several channels, keeps it encrypted on the device, extracts possible action points, and asks the HOD to approve before creating tasks or calendar events. The UI must always make the following distinction visible:

1. **Captured source:** the original letter, message, recording, image, note or document.
2. **AI interpretation:** extracted action, date, time, place, people, confidence and related evidence.
3. **Approved work:** a task, delegated commitment or calendar event created by the user.

No semantic match or model suggestion should look like an approved instruction. Every AI-derived value needs an evidence trail and an explicit human action before it changes the task list or calendar.

## 2. Global shell and navigation

### 2.1 App launch

On launch the app:

- opens the encrypted local database;
- schedules best-effort index maintenance;
- purges items according to the configured retention policy;
- removes stale audio placeholders;
- backfills missing task calendar events;
- checks the local EmbeddingGemma index health;
- exposes the main Today surface even if AI maintenance fails.

The launch experience must never be blocked by a damaged model, revoked permission, missing calendar, failed maintenance job or malformed legacy row. Show a non-blocking status message and keep keyword/basic workflows usable.

### 2.2 Primary tab bar

The persistent bottom bar appears on all primary tabs except the full-screen Review flow. It contains five tabs:

| Tab | Icon role | Purpose | Badge/state |
|---|---|---|---|
| Today | sun/day | daily command centre | none |
| Review | inbox | unresolved imported sources | badge with review queue count |
| Tasks | check circle | owned, delegated and completed work | selected icon changes to filled check |
| Calendar | calendar | events, fixed clinical time and proposed plans | none |
| Library | stacked documents | searchable source archive | none |

The floating **+** capture control sits beside the bar. It is available from the primary tabs and opens the Capture sheet.

### 2.3 Universal visual language

- Background uses a grouped, lightly tinted surface; cards use a raised surface.
- Cards, sheets, segmented controls and buttons use consistent iOS-style squircles.
- Primary actions use the product tint; destructive actions use red; warnings use amber; protected/healthy states use green.
- Typography is centralized in `Hod.type`; font padding is disabled so headings and body copy align predictably.
- Every scrollable page reserves bottom space for the navigation bar, keyboard and fixed action bars.
- Touch targets should remain at least 44dp high and should expose semantic labels for screen readers.
- Text must be readable in light and dark system themes.

## 3. Today — daily command centre

### 3.1 Header

At the top of Today:

- uppercase current date, for example `SUNDAY, 11 OCTOBER`;
- large `Today` title;
- circular profile/settings button at top right. Tapping it opens Settings.

### 3.2 Search shortcut

A full-width search field-like row says **Search letters, people, GO numbers**. It does not accept text directly on Today; tapping it opens Library with focus on search.

### 3.3 On-device status line

A lock icon and status copy communicate local processing, such as:

- `On this phone · Search ready`;
- `On this phone · Preparing search on this phone`;
- `On this phone · Smart search paused · keyword search still works`.

If AI health is degraded, use amber and explain the fallback without hiding the data.

### 3.4 Secretary brief banner

The banner is the high-level daily summary. It contains:

- title: **Secretary brief**;
- a concise attention count;
- urgent/due count when present;
- warning tone if urgent items or conflicts exist;
- informational tone when there is no immediate risk.

The count must be derived from the same filtered data used by Today, Review and Tasks. Deleted, completed, archived and clinical-quarantined records must not inflate the count.

### 3.5 Meeting preparation card

When a future event is available, Today shows the next meeting’s preparation card:

- `Meeting preparation` label;
- meeting title;
- linked agenda items;
- evidence-ready note, or risks such as blocked preparation or unresolved clarification.

This card is informational and links to Calendar for the full itinerary.

### 3.6 Must Act section

Must Act combines:

- open tasks due or overdue today;
- unreviewed, non-clinical sources triaged as action-worthy.

Each card contains icon, urgency label, title, project/source context and chevron. Tapping a task opens Task Detail. Tapping a source opens Review for that source.

Empty state: **Nothing needs you right now**, with an explanation that new letters, messages and calls will appear when they need action. If review items exist outside the top list, mention how many are waiting in Review.

### 3.7 Schedule section

Shows the selected day’s fixed clinical blocks and calendar events in chronological order. Each row has time and title. Fixed blocks are visually muted. A `Calendar` link opens Calendar on today.

Empty state: `No events today.`

### 3.8 Counts

Two tappable tiles:

- **To review** → Review queue;
- **Waiting on others** → Tasks with Delegated segment selected.

## 4. Capture sheet

The floating **+** opens a bottom sheet with a drag handle, Cancel and Capture title. The sheet is scrollable and navigation-bar/keyboard safe.

### 4.1 Voice capture

Large primary card:

- idle: **Speak a Note**, `Tamil or English · stays on this phone`;
- recording: elapsed timer and `Tap to stop and transcribe`;
- disabled: lock icon and privacy explanation;
- recording state uses red; idle uses product tint.

Stopping records an audio source and queues on-device transcription. Processing appears in the same sheet.

### 4.2 Six capture tiles

Two rows of three tiles:

1. **Camera** — photograph a letter, notice or document.
2. **Screenshot** — choose an existing image.
3. **Document** — choose PDF, Word or text file.
4. **WhatsApp** — choose exported text/ZIP or shared chat export.
5. **Call** — choose M4A, AAC or WAV recording.
6. **Note** — type or paste a note.

When a source permission is off, replace the icon with a lock and route the tap to Privacy & Data.

### 4.3 Processing section

Each active import appears with title, status and optional progress bar. Typical states:

- Reading the photo;
- Transcribing Tamil and English on this phone;
- saved for manual review;
- transcription unavailable.

Processing errors must preserve the source as a reviewable item and explain what failed.

### 4.4 Note dialog

The Note dialog contains:

- New Note title;
- multiline text field;
- Paste copied WhatsApp message shortcut;
- Cancel and Save actions.

Blank notes cannot be saved. The first non-blank line becomes the title unless the pasted content is classified as WhatsApp.

### 4.5 Capture privacy footer

The sheet states that data stays encrypted on the phone, phone numbers/emails are hidden and patient details stay out of search. A Privacy Settings link opens Privacy & Data.

## 5. Review — evidence and approval workflow

Review is a full-screen queue rather than a tab-bar surface.

### 5.1 Review header

- back label `Today`;
- centered `Review` title;
- queue position, for example `1 of 4`;
- `Later` action when more than one source is queued.

### 5.2 Source review card

The source header shows source type, date and an icon. The card then shows one action candidate at a time. The primary editable field is **Action wording**. It must never be a clipped or silently truncated model output.

Under the action:

- `Action n of m from this item`;
- explanation such as addressed to HOD, action language, deadline cue or semantic evidence;
- optional model/reasoning status.

### 5.3 Structured details

Show separate rows for:

- **Due** — date/time or `Not stated`;
- **Where** — location or `Not stated`;
- **People** — owner/official/person or `Not stated`.

Each field has one of three states:

- green check: grounded in the cited evidence;
- question mark: missing or needs confirmation;
- amber confirmation state: extracted but not exact enough.

If any field is uncertain, display a concise clarification message. Do not invent a date, time, location or person from unrelated document text.

### 5.4 Evidence block

The source excerpt appears under `From the source`, with the exact supporting passage highlighted when possible. An `Open` action opens the original source or a larger evidence view. Audio evidence should include a timestamp; documents should include page/paragraph context where available.

### 5.5 Related evidence

Related messages, screenshots, calls and documents may be shown as linked evidence. Each relation should explain why it is related and preserve the original source citation.

### 5.6 Review actions

Bottom actions must remain visible above navigation bars and keyboards:

- **Approve** — create a task with the current action and optional owner;
- **Edit** — switch action wording/field values into editable mode;
- **Delegate** — create the task with a selected team member or manually entered owner;
- **Reject** — choose a dismissal reason;
- **Approve Edited Action** — shown while editing;
- **Cancel Edit** — discard unsaved edits;
- **Later/Keep in inbox** — leave the source available for future review.

Approval is explicit. It creates a task and audit record atomically. Calendar writing is a separate confirmation step.

### 5.7 Delegate sheet

The sheet explains that the app does not message the delegate. It offers `Me`, configured team members and `Someone else…`. Choosing the latter opens a name prompt.

### 5.8 Reject/dismiss sheet

Offer clear reasons such as no action needed or not mine. The reason is recorded in the audit trail.

### 5.9 Review empty state

**All clear** with text explaining that new imports will appear when they need review, plus Back to Today.

## 6. Tasks

### 6.1 Task list

Header: `Tasks`.

Segmented control:

- **Mine** — open tasks without an assignee;
- **Delegated** — open tasks assigned to someone else;
- **Done** — completed tasks, newest completion first.

Tasks are grouped as Overdue, Today, This Week, Later, No Date and Done. A task row contains completion circle, title, due/owner/matter metadata, blocker/clarification pill when relevant and chevron.

Empty states explain how Review approval or delegation creates tasks.

### 6.2 Task completion

Tapping the check circle toggles done/reopened without opening detail. The title receives line-through styling when complete.

### 6.3 Task Detail

The detail page has a Tasks back bar and More menu.

Top content:

- state pill: Open, Scheduled, Overdue, Delegated, Blocked, Clarification or Done;
- due-today pill when relevant;
- full task title.

Metadata group:

- Due;
- Owner;
- Matter;
- Source.

Next Step card explains the practical next action and may offer Clear Blocker, Mark Answered or Pick a Time.

Workflow group contains Blocker and Clarification rows. Tapping either opens a multiline edit prompt. Owner opens the delegation sheet. Due opens date/time picker.

Source evidence card opens the source sheet. History is an audit timeline with event name and timestamp.

Fixed bottom actions:

- Reschedule;
- Mark Done, or Reopen for completed tasks.

More menu contains Delete Task. Delete uses the global six-second Undo snackbar.

## 7. Calendar

### 7.1 Calendar header and date strip

Calendar shows month/year, week context and a horizontally selectable day strip. The selected day is highlighted. Selecting a different day reloads events, fixed blocks and proposals for that day.

Primary actions:

- **Add block** — add a protected focus block;
- **Plan My Day** — generate proposed task blocks within work hours while respecting fixed clinical time and free-time percentage.

### 7.2 Itinerary

The selected date displays chronological event cards with:

- time range;
- title;
- location;
- duration/context;
- fixed/proposed/conflict styling.

Tapping an existing HOD Desk event opens an event sheet. Removing it from HOD Desk does not delete the Android Calendar event.

### 7.3 Proposed schedule panel

Before writing anything to Android Calendar, show:

- number of proposed blocks;
- clash count and names;
- explanation that nothing is written until confirmation;
- **Discard**;
- **Add N** for all non-clashing proposals;
- **Add only: …** for individual proposals.

After confirmation, calendar writes must be reconciled with local events and remain idempotent.

### 7.4 Calendar permissions and target calendar

If calendar permission is missing, request it. Settings → Write Events To presents writable calendars. If none are available, explain how to grant permission or add a calendar.

## 8. Library

### 8.1 Library header and filters

Header contains `Library` and Select. Search supports Tamil, English, GO numbers, names and natural-language descriptions. Scope segmented control:

- All;
- Letters;
- WhatsApp;
- Calls.

The status line reports item count and whether Tamil/English semantic search is ready or keyword fallback is active.

### 8.2 Source rows

Each row shows source icon, title, captured date, source type/review state, evidence preview and semantic/keyword match reasons. Clinical sources are excluded from ordinary Library search and appear in Clinical Vault.

### 8.3 Multi-select

Select switches the header to selected count and reveals Select All/Clear All and Delete N. Selection is scoped to the active filter/results. Delete immediately hides selected rows and displays a six-second Undo snackbar before committing deletion.

### 8.4 Source detail sheet

Tapping a source opens a scrollable sheet with:

- title and source/date metadata;
- segmented tabs: **Actions**, **Evidence**, **Original**;
- Actions tab: Open matter file, Review and extract actions, Edit and save in Review, Send to Inbox, Delete;
- Evidence tab: full cleaned source text;
- Original tab: Open Original when the source URI is available.

The sheet must always be scrollable and navigation-bar safe so every action remains reachable on small screens.

## 9. Settings

Settings is opened from the Today profile button. It is a pushed, scrollable hierarchy with iOS grouped sections.

### 9.1 Settings home

Header: Settings with back to Today.

Top Department Profile card shows fixed clinical time and team count. A `Finish setup · n of 4` card appears until clinical blocks, team, venues and target calendar are configured.

Groups:

**Work**

- Fixed Clinical Time;
- Team;
- Who Outranks Whom.

**Calendar**

- Write Events To;
- Working Hours;
- Keep Free.

**Privacy & Security**

- Privacy & Data;
- Clinical Vault;
- Activity Log.

**Intelligence**

- On-Device AI;
- Follow-ups;
- Secretary;
- Secretary Control Centre;
- Evaluation Lab;
- How to Import.

Footer shows product version and `Nothing leaves this phone.`

### 9.2 Department Profile

Configure clinical blocks, ward/OPD/teaching time, team members, venues/travel information and authority order. These values constrain planning and delegation.

### 9.3 Privacy & Data

Hero card explains encrypted on-device storage and no cloud AI. Import switches:

- Allow Sensitive Imports;
- Call Recordings & Voice Notes;
- WhatsApp & Messages;
- Screenshots & Camera;
- Documents & Gmail.

Protection:

- Hide Phone Numbers & Emails;
- Keep Patient Details Out of Search (always on).

Retention presets: 30 days, 6 months, 1 year, Custom (1–3650 days). Approved tasks retain history. Activity Log & Export opens the audit page. Delete All Data requires typing `DELETE` and clearly states that Android Calendar events already written remain.

### 9.4 On-Device AI

Health card shows Everything is working or Smart search needs attention. Capabilities:

- Smart Search status and indexed count;
- Speech to Text status, Tamil-first/English mixed;
- Read Text from Images status;
- Deeper Understanding model install/Get state and progress.

Maintenance actions:

- Run Self-Check Now;
- Rebuild Search Index;
- Save index snapshot;
- Restore previous snapshot;
- Reset learned preferences;
- inspect clinical boundary.

Technical Details exposes model version, prompt registry, indexed/eligible counts, chunks, stale/invalid vectors, reasoning model and app version.

### 9.5 Activity Log

Append-only grouped timeline of imports, approvals, task changes, scheduling, completion and deletion. Export creates a user-selected copy for review.

### 9.6 How to Import

Guides for WhatsApp export, Gmail attachments, screenshots/photos, calls/voice notes and on-device Tamil/English transcription.

### 9.7 Clinical Vault

Clinical/special-category content is separated from normal search, reasoning, task and calendar output. Items are listed with source type/date and can be expanded to inspect evidence. Empty state explains that future clinical imports will be kept there.

## 10. Secretary tools

### 10.1 Secretary page

Accessible through Settings → Secretary. It accepts typed/pasted transcript or voice input. The page shows parsed intent and whether the request is read-only or requires confirmation.

Read-only intents can run directly. Mutating intents show Review request followed by Confirm and run. The UI must clearly state that task/schedule changes are proposals until confirmed.

Supported intent destinations include waiting tasks, meetings, Library search and Plan My Day.

### 10.2 Secretary Control Centre

Dashboard groups:

**Today:** Priority actions, Meetings, Due within 48 hours.  
**Risks:** Waiting on others, Overdue commitments, Schedule conflicts, Sensitive items protected.  
**AI status:** On-device intelligence and Information only.

Clickable counts deep-link to Review, Calendar or Delegated Tasks.

### 10.3 Follow-ups

Settings → Follow-ups lists waiting commitments. Each candidate contains reason, editable draft message and evidence. Actions:

- Copy draft;
- Share after review;
- Dismiss.

Nothing is sent automatically. Copy/share/dismiss events are audited.

## 11. Matter file

Opened from a Library source sheet. It groups linked evidence, commitments and schedule for one matter.

Sections:

- Latest instruction;
- Next action;
- Needs clarification;
- Sources;
- Proposed change.

Proposed task changes offer Review evidence, Accept change and Keep existing. This is the safe path when a newer message appears to update an existing task.

## 12. Evaluation Lab

Settings → Evaluation Lab is a privacy-preserving quality gate. It displays imported examples, redaction count and clinical exclusions. The safety gate explains that a model/prompt change is acceptable only when action recall improves without increasing false verified fields.

The last run shows Passed/Blocked, action recall and false-verified rate. The UI should support a future baseline/current comparison without exposing patient text.

## 13. Global overlays and transient states

### 13.1 Status snackbar

Appears above the bottom navigation with message and OK. Automatically dismisses after eight seconds.

### 13.2 Delete snackbar

Shows `Deleted …` and Undo for six seconds. Items are hidden immediately but committed only after the window closes.

### 13.3 Date/time picker

Used by Task Detail, Calendar and settings working hours. It must support clearing a due date and display local timezone consistently.

### 13.4 Action sheets

Use bottom sheets for owner selection, calendar target, delete confirmation, source actions and proposal actions. Sheets must be scrollable, rounded at the top and safe above navigation bars.

### 13.5 Permission/blocked states

When microphone, camera, document or calendar permission is unavailable, explain the blocked capability and provide a direct path to the relevant settings page. Never silently discard a capture.

## 14. End-to-end journeys

### 14.1 Official WhatsApp message → approved task

Capture → WhatsApp export → consent check → redaction → action extraction → source saved → Review queue → inspect action/evidence/fields → edit if needed → approve or delegate → task appears in Tasks → optionally schedule in Calendar after confirmation → audit entry recorded.

### 14.2 Tamil call recording → task

Capture → Call → audio processing card → on-device Tamil/English transcription → source/evidence saved → Review shows transcript and timestamp evidence → verify fields → approve/edit/reject → task and audit trail.

### 14.3 Camera photograph → review

Capture → Camera → camera permission → photo OCR → source retains original URI → action candidates shown in Review → Open Original for visual verification → edit/approve/reject.

### 14.4 PDF/Word/Gmail attachment → calendar event

Share attachment to app or Capture → Document → parse pages/paragraphs → extract meeting/date/time/location → Review with citations → approve task → Calendar Plan My Day or explicit Add block → review clashes → confirm target calendar → write and reconcile.

### 14.5 Search and matter consolidation

Today search shortcut → Library query in Tamil, English, Tanglish or GO number → keyword plus EmbeddingGemma results → inspect match reasons → open source → open matter file → review related source/task changes.

### 14.6 Secretary voice command

Settings → Secretary → Speak to secretary → transcript parsed → intent and confirmation state shown → review mutating proposal → confirm → deep-link to affected workflow or show result.

## 15. State and content rules for the redesign

- Do not count deleted, completed, archived or clinical items in attention banners.
- Never show clinical content in normal Library, Review, Tasks or Calendar output.
- Never allow ungrounded date/time/place/person fields to look verified.
- Preserve original source access wherever a URI exists.
- Keep evidence visible close to every AI-generated action.
- Keep approval, calendar confirmation and external sharing separate.
- Make all destructive actions reversible where possible and explain irreversible actions.
- Maintain scroll and bottom safe areas for every sheet, dialog and fixed action bar.
- Empty, loading, permission-denied, processing, failed, degraded-AI and offline states are first-class designs.

## 16. UI architecture checklist

### Information architecture

- [ ] Five-tab shell is consistent across all primary screens.
- [ ] Today → Review → Tasks → Calendar → Library transitions are discoverable.
- [ ] Settings hierarchy contains all secondary capabilities.
- [ ] Back behavior is deterministic for pushed screens, sheets and dialogs.

### Visual system

- [ ] One type scale, one spacing scale and one icon sizing scale.
- [ ] Squircles have consistent radius and border treatment.
- [ ] Primary, tinted, gray and destructive buttons have predictable semantics.
- [ ] Light/dark colors meet contrast targets.
- [ ] Long Tamil, Tanglish and English strings wrap without clipping.

### Interaction and safety

- [ ] Every capture has a visible processing result.
- [ ] Every suggestion has source evidence.
- [ ] Every task/calendar mutation has explicit confirmation.
- [ ] Every delete has Undo or an explicit irreversible confirmation.
- [ ] Clinical and privacy boundaries are visible but not alarmist.

### Responsive and accessibility

- [ ] Test 320dp width, large fonts, display scaling and landscape.
- [ ] Test gesture navigation and three-button navigation.
- [ ] Test keyboard-open states for all editable fields.
- [ ] Test screen reader labels for icons, tabs, toggles and action cards.
- [ ] Test Tamil script, mixed Tamil-English and long official names.
- [ ] Verify every bottom action remains reachable above the navigation inset.

### QA walkthrough

- [ ] Fresh install with no permissions.
- [ ] Existing database with sources, tasks, events and clinical items.
- [ ] Empty database.
- [ ] Failed OCR, failed transcription and missing original file.
- [ ] AI index healthy, stale, invalid and unavailable.
- [ ] Calendar permission denied, no writable calendar and calendar write retry.
- [ ] Delete/Undo on one and many Library items.
- [ ] Approve, edit, delegate, reject and later flows.
- [ ] Process rotation/backgrounding while a sheet or transcription is active.
- [ ] Verify no spontaneous crash when switching tabs or tapping any card.

## 17. Suggested redesign deliverables

The UI architecture team should produce:

1. A token sheet for colors, type, spacing, radii, elevation and iconography.
2. A navigation map covering every route and overlay in this document.
3. Figma components for cards, grouped rows, sheets, segmented controls, banners, pills, evidence blocks and bottom action bars.
4. State matrices for each screen: loading, empty, ready, error, permission denied, degraded AI and clinical protected.
5. Prototypes for the six end-to-end journeys in Section 14.
6. Accessibility and responsive acceptance criteria from Section 16.
7. A source-to-action visual language that makes evidence, AI interpretation and approved work unmistakably different.

