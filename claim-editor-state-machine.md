# Claim Editor State Machine

## Goal

Define one internal editing flow for claim creation and claim modification.

This editor must serve both:

- in-world shaped mode interactions
- future external GUI-driven flows such as the GPExpansion claim map

The editor is not a Bukkit listener and not a GUI controller.
It is an internal service/state model that accepts normalized edit intents, validates them, and either applies or rejects changes while still producing preview/selection data when useful.

## Core Rule

All claim editing must go through the same internal editor operations.

That means:

- tool clicks do not edit claims directly
- commands do not edit claims directly
- GPExpansion GUIs do not edit claims directly
- claim geometry is never mutated ad hoc from outside the editor layer

Everything becomes an edit request against the same core engine.

Core ships the editor and the bridge points.
Core does not ship the claim map UI itself.

## Why This Matters

This avoids repeating the same mistakes from the current codebase:

- event logic mixed with geometry logic
- resize behavior hidden in listener branches
- addon integrations accidentally overriding built-in behavior
- UI-specific shortcuts bypassing validation

It also solves the future GPExpansion map problem cleanly:

- the map can request claim/unclaim/expand actions
- the editor decides what segment or polygon change that means
- shaped mode and the map stay consistent

## Layers

### 1. Input Boundary Layer

Translates raw platform inputs into editor intents.

Examples:

- right-click with investigation tool
- `/shapedclaims`
- `/expandclaim 5`
- click gray pane in claim map
- click green pane in claim map to unclaim

This layer is allowed to know about:

- Bukkit events
- inventories and GUI slots
- commands and arguments
- player location and facing

This layer is not allowed to:

- mutate claim geometry
- bypass validation
- choose alternate claim rules

### 2. Claim Editor Layer

Receives normalized requests and current editor state.

Responsibilities:

- choose the active edit target
- track current mode
- accumulate open shaped paths
- resolve segment selection
- request geometry validation
- build previews
- apply approved edits

### 3. Geometry Layer

Pure geometry and validation.

Responsibilities:

- orthogonal polygon rules
- self-intersection detection
- segment splitting
- area and containment calculations
- claim-shape diff calculations

### 4. Persistence Layer

Stores committed edits only.

Responsibilities:

- save updated parent polygon
- save created or removed claims
- run migration/version rules

## Primary Concepts

### ClaimEditorSession

Per-player editing state.

Suggested fields:

- `UUID playerId`
- `ClaimEditorMode mode`
- `ClaimEditTarget activeTarget`
- `ShapedPathDraft openPath`
- `SegmentSelection activeSegment`
- `ClaimEditPreview preview`
- `ClaimEditSource source`

### ClaimEditorMode

Suggested modes:

- `IDLE`
- `BASIC_RECTANGLE`
- `SHAPED`

Map interactions should arrive as editor intents from an external source, not as a built-in core mode.

### ClaimEditSource

Tracks where the edit request came from.

Examples:

- `TOOL`
- `COMMAND`
- `GUI_MAP`
- `GUI_RESIZE`

This is mostly for UX and audit behavior, not for geometry rules.

### ClaimEditTarget

Represents what is currently being edited.

Possible targets:

- create a new top-level claim
- modify an existing parent claim
- modify a rectangular subclaim

For shaped mode MVP, parent-claim editing is the main focus.

### ShapedPathDraft

Represents an in-progress orthogonal path before commit.

Suggested fields:

- ordered list of points
- start claim id or `null` if creating from unclaimed space
- current snapped preview point
- closure-ready flag

### SegmentSelection

Represents a specific editable boundary segment on a parent claim.

Suggested fields:

- claim id
- edge index
- optional node-bounded subsection
- local orientation
- selected anchor side

This is the bridge between node-based shaped editing and map-based claim expansion.

### ClaimEditPreview

Represents a non-committed result shown to the player.

Suggested outputs:

- current polygon after an instant successful edit
- highlighted segment
- conflict markers
- validation messages

## Edit Intents

Every UI path should become one of a small set of editor intents.

Suggested intents:

- `ENTER_MODE`
- `EXIT_MODE`
- `SELECT_CLAIM`
- `SELECT_SEGMENT`
- `ADD_NODE`
- `ADD_CORNER`
- `MOVE_SEGMENT`
- `EXPAND_SEGMENT`
- `UNCLAIM_SEGMENT`
- `CLOSE_PATH`
- `CANCEL_PATH`

This list should stay small. If a new feature cannot fit here, that is a sign the editor boundary may be wrong.

## State Flow

### Global Flow

1. player enters a claim-edit mode
2. input boundary translates action into an editor intent
3. editor session updates selection or draft state
4. geometry layer validates the proposed result
5. editor applies the valid edit immediately or returns validation errors
6. player continues editing
7. persistence stores the accepted change

## Shaped Mode Flow

### Existing Claim Boundary Editing

1. player enters `/shapedclaims`
2. editor resolves the nearby editable parent claim
3. player targets a boundary section
4. editor creates a `SegmentSelection`
5. player adds a node or expands the selected section
6. editor computes the resulting polygon candidate
7. geometry validator accepts or rejects it
8. the edit applies immediately if valid; otherwise conflict/selection feedback is shown

### New Corner Path In Unclaimed Space

1. player enters `/shapedclaims`
2. player places the first corner in unclaimed space
3. editor opens a `ShapedPathDraft`
4. each next point is snapped to valid orthogonal continuation
5. invalid diagonal attempts stay rejected at the boundary/editor layer
6. clicking the starting corner attempts closure
7. geometry validator checks minimum corners and self-intersection
8. the edit applies immediately if valid; otherwise conflict feedback is shown

## GPExpansion Claim Map Flow

The GPExpansion claim map must use the same editor, not parallel logic.

### Initial Scope

For now the external map only needs:

- claim land
- unclaim land

No fine-grained reshape UI is required in the first version.

### Map Interaction Model

1. player opens the map
2. GUI calculates the clicked cell and zoom level
3. GPExpansion translates that into a normalized editor intent
4. editor resolves which claim/segment/cell the action affects
5. editor computes the proposed geometry result
6. the editor returns an applied result or a failure with conflict/selection data

### Important Constraint

The map must never directly:

- add polygon nodes
- rewrite corner lists
- choose claim merge rules
- decide which segment should move

Instead, GPExpansion passes high-level requests such as:

- claim this cell
- unclaim this cell
- expand from this side

The editor owns the translation from map selection to geometry edit.

## GPExpansion Bridge Operations

These are not separate geometry systems. They are bridge operations over the same editor.

Suggested bridge operations:

- `previewClaimCells(player, cellSelection, zoomLevel)`
- `commitClaimCells(player, cellSelection, zoomLevel)`
- `previewUnclaimCells(player, cellSelection, zoomLevel)`
- `commitUnclaimCells(player, cellSelection, zoomLevel)`

For shaped claims, these operations may:

- attach a rectangular nib to an existing edge
- remove a rectangular nib from an existing shape
- create a new detached square claim if allowed by claim rules

If a requested map action cannot be represented cleanly as a valid orthogonal edit, it must fail with a clear message instead of silently approximating.

These bridge operations may live in core, but the inventory UI and map rendering should remain in GPExpansion.

## Validation Responsibilities

The editor layer must validate more than raw geometry.

Examples:

- player ownership/admin rights
- claim block cost
- world rules
- minimum width
- subdivision compatibility
- detached-claim rules
- overlap with nearby claims

The geometry layer should answer:

- is this shape valid?
- where does it intersect?
- what are the resulting segments?

The editor layer should answer:

- is the player allowed to do this?
- can this valid shape become a claim here?

## Preview Responsibilities

The editor should expose one result format regardless of source.

That preview should support:

- fake-block or glowing boundary rendering
- active segment highlighting
- conflict visualization
- map-cell ghost overlays later
- chat messages for invalid steps

The same invalid self-intersection should look meaningfully invalid whether it came from a shovel click or a map click.

Valid edits should apply immediately rather than waiting for a separate confirmation step.

## Commit Model

Edits should be immediate by default:

1. an edit request arrives
2. the editor validates it
3. if valid, the editor applies it immediately
4. if invalid, the editor returns failure plus conflict/selection feedback

Preview data still exists, but it is support data for guidance and errors, not a required confirmation screen.

## Failure Model

Failures must be explicit.

Suggested categories:

- `INVALID_GEOMETRY`
- `SELF_INTERSECTION`
- `MIN_WIDTH`
- `INSUFFICIENT_CLAIM_BLOCKS`
- `OVERLAPS_OTHER_CLAIM`
- `NOT_OWNER`
- `NOT_EDITABLE_FROM_HERE`
- `AMBIGUOUS_EDIT_REQUEST`

This matters for UX. “Nothing happened” is not acceptable.

Where possible, failures should map back to existing core GP message keys so GPExpansion and in-world flows can reuse the same wording.

## Source-Agnostic Rules

These rules should hold regardless of whether the request came from shaped mode or the map:

- same claim rules
- same geometry rules
- same overlap rules
- same minimum-width rules
- same persistence path
- same golden-shovel requirement for modification actions unless a future mode explicitly overrides it

Only the input and presentation layers should differ.

## Immediate Next Steps

After this document, the next implementation targets should be:

1. add an internal `ClaimEditorSession` model
2. define intent/result types
3. connect shaped-mode draft creation to the new polygon validator
4. design a map-cell-to-edit translation bridge for GPExpansion

The claim map should be treated as a GPExpansion client of the editor, not as a core feature and not as a separate claim system.
