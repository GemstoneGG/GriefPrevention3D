v17.4.7: Add proper non-terrain snapping 3D first-click markers
# GriefPrevention3D v17.4.7

**Wiki:** https://github.com/castledking/GriefPrevention3D/wiki

## Highlights

- **3D initialization visualization** — New `INITIALIZE_ZONE_3D` visualization type renders the first-click diamond marker at the exact Y level without terrain snapping.
- **3D subdivision raycast parity** — 3D subdivisions now use the same raycast targeting as 3D admin claims, resolving through snow layers and replaceable blocks.
- **Removed `visualizeAreaExact` hack** — All initialization markers now go through the proper visualization pipeline.

## INITIALIZE_ZONE_3D Visualization

The first-click initialization marker for 3D claims previously either bypassed the visualization system entirely (raw `sendBlockChange`) or used `INITIALIZE_ZONE` which terrain-snaps the diamond block to the surface.

A new `INITIALIZE_ZONE_3D` visualization type renders the diamond block marker at the exact clicked Y coordinate. It routes through `drawRespectingYBoundaries` in `FakeBlockVisualization` and uses exact Y placement in `GlowingVisualization`, matching how `SUBDIVISION_3D` and `ADMIN_CLAIM_3D` types work.

Both 3D admin claims and 3D subdivisions now use this type for their first-click marker.

Affected files: `VisualizationType.java`, `FakeBlockVisualization.java`, `GlowingVisualization.java`, `BoundaryVisualization.java`.

## 3D Subdivision Raycast Targeting

3D subdivisions (`/3dsubdivideclaims`) now share the same `raycastForAdmin3D` targeting logic as 3D admin claims. This resolves through replaceable blocks (snow layers, tall grass) to find the actual solid block underneath, giving precise Y coordinates for both the first and second click.

Previously, 3D subdivisions used the raw `clickedBlock` from the interact event, which could target a snow layer instead of the block beneath it, leading to incorrect Y bounds.

All three first-click paths are covered:
- Nested subdivision inside a 3D parent claim
- Nested subdivision inside a 2D parent claim
- Top-level subdivision in a claim

The second click (finishing corner) also uses raycast for accurate Y on the closing boundary.

Affected file: `PlayerEventHandler.java`.

## Removed visualizeAreaExact

The `BoundaryVisualization.visualizeAreaExact` method sent a raw `sendBlockChange` with a diamond block, completely bypassing the visualization pipeline (no proper cleanup tracking, no glow support, no event firing). It has been removed in favor of `visualizeArea` with `INITIALIZE_ZONE_3D`, which goes through the full visualization system.

Affected file: `BoundaryVisualization.java`.

## Migration

No data migration or configuration changes required.
