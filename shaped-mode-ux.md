# Shaped Mode UX

## Scope

This document defines the core in-world behavior of `/shapedclaims`.

It covers:

- corner placement in unclaimed space
- node placement on existing claim boundaries
- segment selection
- segment expansion
- cancellation and feedback rules

It does not cover any inventory editor or claim map.

## Core Rule

Shaped mode is still a golden-shovel workflow.

Rules:

- shaped actions require the golden shovel
- existing GP failure messages should be reused where they already fit
- corner-based whole-claim resizing remains the normal resize path
- shaped mode adds node-based and segment-based editing on top of that

## Unclaimed Space Workflow

### Start

Right-clicking in unclaimed space sets the first corner of a shaped path.

### Continue

Each next point must continue on the same X or same Z axis from the last point.

If the click is diagonal:

- shaped mode should prefer snapping to the nearest orthogonal continuation
- only reject when snapping would collapse to the current endpoint or create another invalid step

### Close

Clicking the starting corner again closes the path.

Closure rules:

- at least 4 corners
- orthogonal only
- no self-intersection

## Existing Claim Workflow

### Add Node

Sneak-right-clicking the interior of an existing top-level claim edge adds a node.

Rules:

- corners are not valid node placements
- the clicked point must resolve to exactly one edge
- node insertion applies immediately when valid

### Select Segment

Once a side is split by nodes, right-clicking a node-bounded section selects that segment.

Rules:

- selection must resolve to exactly one interior segment
- corners should stay ambiguous
- selected segment should remain highlighted

### Expand Segment

`/expandclaim` or equivalent shaped expansion should apply only to the selected node-bounded segment.

Rules:

- valid segment expansion applies immediately
- invalid expansion fails immediately
- a node-to-node expansion creates an orthogonal protrusion instead of dragging the whole side diagonally

This is the intended “nib” behavior for shaped claims.

## Cancel Behavior

Players need a clean way to abandon an in-progress shaped path.

Rules:

- cancelling clears only the open path
- it does not discard an already-selected existing claim
- it clears transient snap/conflict feedback

## Feedback

Shaped mode needs clear, low-noise feedback.

Required:

- first-corner message
- snapped-corner message
- closed-path message
- cancelled-path message
- selected-segment highlight
- invalid-geometry/conflict feedback when closure or expansion fails
