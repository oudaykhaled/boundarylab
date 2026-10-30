# FieldPack interaction and visual source

Original local design, 2026-10-30. Open [index.html](index.html) directly. This browser prototype simulates processing and uses the repository's three owned reference fixtures; it is **not** an installed native/Flutter client, broker integration, or Figma-parity evidence. No Figma source was supplied. Native and Flutter should share this design and record their own runtime evidence.

## Visual contract

Use semantic theme tokens; values belong only in the theme definitions. This standalone public repository does not depend on the olivium OMDS package (see the request-scoped guardrail brief). Native framework controls and Flutter Material 3 controls share these tokens and semantics.

| Token | Value | Use |
|---|---|---|
| navy | `#102C3A` | Header, strong identity |
| accent | `#006B52` | Primary action with white text |
| background / surface | `#F3F6F7` / `#FFFFFF` | Page / cards |
| text / muted | `#173340` / `#4D6470` | Main / secondary text |
| border | `#C6D3D8` | Decorative separators; not sole control boundary |
| successSurface | `#E3F3EC` | Completed result panel |
| danger / dangerSurface | `#A52A35` / `#FFF0F0` | Failed or lost result |
| warning / warningSurface | `#775400` / `#FFF4D6` | Cancellation pending |
| focus | `#175CD3` | Visible keyboard focus |

Spacing tokens: 4, 8, 12, 16, 24, 32 logical pixels. Page inset 24; card inset 16; card radius 12; panel radius 16. Minimum touch target 48×48. Main title 28/34, section 20/28, body 16/24, label 14/20; respect system font scaling. Use system sans; no downloadable font dependency. Token text/accent/danger combinations have measured WCAG AA contrast; see VALIDATION.md. Insets are directional; aliases/digests remain LTR inside RTL layout.

## Shared layout

Navy top bar: small “BoundaryLab”, title “FieldPack”, subtitle “Inspection handoff”. Top actions expose session history. A separate **Generated workload** destination may reuse the same header, surfaces, typography and action tokens, but its results must never be labeled attachment processing. The local prototype focuses on FieldPack.

Below the header: numbered Select → Prepare → Export indicator; explanatory integrity note; current attempt heading; attachment list; summary; state panel; primary action; manual-transfer instructions. Each attachment uses its public alias, size, selection/status text and accessible control. Do not expose URI or private source path. Supported-input guidance: “Up to 32 supported files · 128 MiB total. Some document providers are unsupported.” Selection alone does not prove locality or offline access.

Keep the primary action in normal scroll flow so large text cannot be covered by a sticky footer. At 320px width, long aliases wrap; no horizontal scrolling. Desktop prototype surrounds the phone-sized view with clearly separate scenario controls. All runtime/native screens remain ordinary product UI without simulation controls.

## State and copy contract

| State | Heading / explanation | Actions |
|---|---|---|
| Empty | Select attachments / Choose stable files you are authorized to read. | Select files |
| Selected | Ready to prepare / Public aliases are the filenames the recipient needs. | Prepare pack; add/remove |
| Processing | Preparing pack / File N of M. Keep this session open. | Request cancellation |
| Cancellation pending | Cancellation requested / Waiting for the current read to finish. This can take time. | No new attempt or export until cleanup/terminal result |
| Complete | Pack ready / Every selected file succeeded in this attempt. | Export manifest |
| Export cancelled | Pack ready / Export was cancelled. No export was confirmed. | Export manifest again |
| Export failed | Export failed / The destination could not be written. Your prepared pack remains available in this session. | Try export again |
| Export confirmed | Manifest exported / Transfer the attachments separately using these exact aliases. | New attempt |
| Failed | Pack could not be prepared / At least one attachment failed. No complete manifest is available. | Start a new attempt; edit selection |
| Cancelled | Preparation cancelled / No complete manifest is available. | Start a new attempt |
| Lost | Connection lost / The outcome is unknown. This attempt cannot be exported. | Start a new attempt after reconnect |

No overall time estimate or invented byte percentage. Show file count plus indeterminate work unless real bounded byte progress is available. Cancellation is cooperative: immediate RUNNING(cancelRequested=true) is not CANCELLED, and success may win the terminal race. Terminal status controls the UI. Loss never becomes “failed safely” or automatic replay. A new user-initiated attempt rehashes **every** selected file. Old digests may be displayed as history only and cannot complete a new pack.

Export is enabled only after all current-attempt files succeeded. Production shows “Manifest exported” only after destination writing succeeds. Browser download initiation cannot verify a saved destination, so this prototype says “Sample download requested”. Its sample manifest uses reference-fixture hashes, never hashes user files, and never claims runtime work occurred.

Permanent handoff copy: **“Transfer files separately, with filenames matching these aliases. A checksum compares file contents; it does not authenticate the sender.”** Alias creation does not rename or transfer originals. Include an alias mapping before export where original local filenames differ, without exporting private paths. Do not claim snapshot consistency for changing files.

## Session history and accessibility

History is an in-memory view of this app session, explicitly titled “This session only”. It clears on reload/app restart and has no saved/resume affordance. Broker result retention is bounded separately; retained display rows are not durable receipts. Store no history in browser localStorage, sessionStorage or IndexedDB. History does not re-enable past exports.

Use heading hierarchy, native controls, visible keyboard focus, named remove actions, text+symbol status, a polite live status region and meaningful progress labels. On terminal transition, move focus to the state heading; when closing a modal, return focus to its opener. Cancellation waits use live text, without repeatedly stealing focus. The prototype uses no animation; reduced-motion behavior therefore remains stable. Target WCAG 2.2 AA, test keyboard, contrast, 200% text, 320px reflow and screen readers on actual platforms. An RTL layout preview is provided; it is not an Arabic translation or proof of native RTL parity.
