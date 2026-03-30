# Geometry Model

## Goal

Define the internal shape model for the GP3D recode before wiring it into claim editing, persistence, or visualization.

This model is focused on one core target:

- top-level claims can become orthogonal 2D polygons
- subclaims remain cuboids
- all parent claim edges stay axis-aligned
- invalid or self-intersecting shapes must be detected early and surfaced as conflict previews

## Shape Types

### Parent Claim Footprint

A parent claim footprint is an **orthogonal closed polygon** in X/Z space.

Rules:

- at least 4 corners
- closed loop
- every edge is axis-aligned
- no zero-length edges
- no self-intersections
- no disconnected regions

### Subclaims

Subclaims remain bounded by the existing cuboid model.

That means:

- rectangular X/Z footprint
- Y-bounded for 3D subclaims
- no polygon editing for subdivisions in the first recode target

## Proposed Core Types

### `OrthogonalPoint2i`

Represents a single grid-aligned X/Z corner.

Suggested fields:

- `int x`
- `int z`

### `OrthogonalEdge2i`

Represents a segment between two orthogonal points.

Suggested derived properties:

- `isHorizontal`
- `isVertical`
- `minX`, `maxX`
- `minZ`, `maxZ`
- `length`

### `OrthogonalPolygon`

Represents a closed parent-claim footprint.

Suggested state:

- ordered list of corners
- cached edge list
- cached bounding rectangle

The polygon should be immutable once constructed. Editing should happen through a builder or edit session object.

### `OrthogonalPolygonValidationResult`

Represents whether a proposed polygon is valid.

Suggested fields:

- `boolean valid`
- `OrthogonalPolygon polygon` when valid
- `List<OrthogonalPoint2i> normalizedPath`
- `List<OrthogonalPolygonValidationIssue> issues`

### `OrthogonalPolygonValidationIssue`

Represents a specific failure reason.

Suggested categories:

- `TOO_FEW_POINTS`
- `NOT_CLOSED`
- `NON_ORTHOGONAL_EDGE`
- `ZERO_LENGTH_EDGE`
- `SELF_INTERSECTION`
- `DUPLICATE_CORNER`
- `DISCONNECTED_SHAPE`
- `BELOW_MIN_WIDTH`
- `BELOW_MIN_AREA`

## Construction Rules

### Raw Point Sequence

A polygon can start from a raw point sequence during shaped-mode editing.

Example:

`[(0,0), (5,0), (5,3), (2,3), (2,7), (0,7), (0,0)]`

From that sequence we should:

1. ensure the path closes
2. ensure each segment is horizontal or vertical
3. ensure no segment has zero length
4. normalize redundant points if safe
5. validate self-intersections

### Normalization

Normalization should be conservative.

Allowed:

- removing duplicate consecutive points
- removing a middle point on the same straight segment if it is purely redundant

Not allowed:

- silently changing the intended footprint
- reordering corners
- auto-fixing self-intersections

## Validation Strategy

### Orthogonality

For each consecutive pair of points:

- valid if `x1 == x2` xor `z1 == z2`
- invalid if both coordinates differ
- invalid if both coordinates are equal

### Closure

A polygon path is complete only if:

- first point equals last point
- number of distinct corners is at least 4

### Self-Intersection

This is the key rule for shaped mode.

If a player snakes the polygon into itself, we must reject it and show a conflict preview.

Detection approach:

- compare every edge against every non-adjacent edge
- ignore edges that share a legal endpoint as immediate neighbors
- flag intersection if:
  - horizontal edge crosses vertical edge in-range
  - overlapping collinear edges produce an invalid overlap

This validator should also provide enough information for future conflict visualization:

- the intersecting edges
- the approximate intersection point

The first implementation should expose this directly through validation issues rather than trying to render conflict previews itself.

### Minimum Width and Area

Rectangle-only minimum checks are not enough for polygons.

We need:

- total enclosed area check
- narrow-corridor check so players cannot create degenerate one-block spikes unless explicitly allowed

The narrow-corridor rule can start simple:

- each edge movement must preserve minimum claim width against neighboring parallel boundaries where applicable

This likely needs a dedicated pass after basic polygon validation.

## Area Model

Parent-claim area should remain a 2D block area in X/Z.

Recommended approach:

- compute the enclosed orthogonal polygon area
- convert to block area using grid-cell semantics consistent with GP rectangles

Important:

- we need one clear block-inclusion convention
- visualization, containment, and claim block cost must all use the same convention

If we get that wrong, players will see one shape, pay for another, and collide against a third.

## Containment Model

The recode will need point-in-polygon checks for:

- block protection
- trust checks
- parent claim resolution
- shape editing and preview

Suggested methods on `OrthogonalPolygon`:

- `containsPoint(int x, int z)`
- `containsCell(int x, int z)`
- `containsRectangle(...)`
- `intersectsRectangle(...)`

The exact choice between point and cell semantics should be documented once we bind the shape model to Minecraft blocks.

## Editing Operations

These should eventually live in a separate editor/service layer, but the geometry model should support them cleanly.

Needed operations:

- create from rectangular bounds
- add node on existing edge
- insert corner into open shaped-mode path
- move edge segment outward/inward
- close path
- remove redundant node

Each operation should return a new polygon or a validation failure, not mutate shared state in place.

## Initial Implementation Slice

The first code slice for the recode should stay narrow:

- internal-only geometry package
- immutable orthogonal point, edge, and polygon types
- path validator for shaped-mode closure and self-intersection checks
- issue objects that can later drive `CONFLICT_ZONE` previews

Do not add registries or addon-facing APIs at this stage.

## Conflict Preview Integration

Validation should not just return `true` or `false`.

It should return enough structure to support preview visualization:

- invalid edge pair
- intersection coordinate if any
- offending segment range

That lets shaped mode show `CONFLICT_ZONE` at the right place instead of only emitting a vague error message.

## Initial Implementation Order

1. `OrthogonalPoint2i`
2. `OrthogonalEdge2i`
3. `OrthogonalPolygon`
4. `OrthogonalPolygonValidator`
5. unit tests for:
   - simple rectangle
   - valid orthogonal L-shape
   - diagonal edge rejection
   - duplicate point rejection
   - self-intersection rejection
   - overlapping collinear edge rejection
6. preview/conflict helper output for shaped mode

## Example Valid Shape

```text
(0,0)
(6,0)
(6,2)
(3,2)
(3,5)
(0,5)
(0,0)
```

This should validate as a simple orthogonal polygon.

## Example Invalid Shape

```text
(0,0)
(6,0)
(6,4)
(2,4)
(2,1)
(5,1)
(5,5)
(0,5)
(0,0)
```

This should fail because the path folds into itself.

That failure should eventually map to a shaped-mode conflict visualization, not just a generic denial.
