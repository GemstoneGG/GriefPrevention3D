# GriefPrevention Recode Plan

## Purpose

This document plans a ground-up recode of GriefPrevention with two main goals:

1. Keep the parts of GP that actually work well for real servers.
2. Replace the parts that make claims hard to learn, hard to extend, and hard to maintain.

This is not a plan to slowly pile more abstractions onto the current codebase. It is a plan to rebuild the plugin around cleaner boundaries, better claim geometry, and a player flow that does not depend so heavily on server owners teaching players how to use a golden shovel correctly.

## What We Keep

- Chest claims and first-claim protection.
- Join-time education such as chat guidance and the book.
- The general idea of claims auto-extending vertically.
- Subclaims as a separate concept.
- The simple server-owner mental model: claims protect land, trust controls access, admins can override.

## What We Replace

- Rectangle-only parent claims.
- The current oversized `PlayerEventHandler`-driven behavior model.
- Heavy dependence on physical tool possession as the main learning path.
- Claim editing logic that is too tightly coupled to Bukkit events, items, and one-off code paths.
- Geometry and visualization code that assumes every top-level claim is one rectangle forever.

## Core Design Principles

- Internal first: extract clear internal subsystems before exposing addon APIs.
- One responsibility per boundary: command parsing, tool interaction, geometry editing, persistence, and visualization should be separate.
- Geometry is data, not event logic.
- Default player flows must be teachable without staff intervention.
- Extensibility should come from stable internal seams first, public API second.
- Add new abstractions only when current in-tree behavior needs them.

## Salvage From Existing PR Work

The recent PR work still has value even if some API shapes were rejected.

We should keep these lessons:

- Move large claim/tool code paths out of giant listener classes into dedicated handlers/listeners.
- Normalize Bukkit event state at the boundary and pass smaller, domain-shaped inputs inward.
- Prefer unified command routing over duplicated command logic, but do not create parallel event systems when Bukkit events already exist.
- Keep visualization rendering logic close to the visualization types or geometry source rather than scattering switches everywhere.
- Avoid shipping addon-facing registries until core actually needs the abstraction.

In short: the extraction direction was good. The mistake was trying to publish too much of it too early.

## Main Product Problem

The current GP experience still assumes too much prior knowledge from players and too much support effort from server owners.

Real problems:

- Players often do not understand that the golden shovel is the main claim tool.
- Server owners end up making starter kits or custom onboarding just so basic claiming works.
- The rectangular model forces awkward claim layouts for roads, paths, towns, and irregular builds.
- Subdivisions help, but they are not a satisfying answer for shaping a parent claim footprint.

The result is predictable: players lose land, get confused, and leave.

## Geometry Model

### Parent Claims

Top-level claims become **orthogonal 2D polygons**:

- All edges are axis-aligned.
- No angled sides.
- No circles.
- No arbitrary noisy geometry.
- The footprint is a 2D polygon in X/Z.
- Vertically, the claim is still extruded like GP does now.

This gives better shape freedom without making claim borders hard to read.

### Subclaims

Subclaims remain **cuboids**.

That keeps subclaim editing understandable and avoids dragging full polygon complexity into every claim layer.

### Invariants

- Parent claim polygons must be simple, non-self-intersecting, and orthogonal.
- Minimum width rules still apply per segment corridor, not just total area.
- Minimum area rules still apply.
- Parent claims cannot create one-block spikes, degenerate edges, or disconnected regions.

## Proposed Player UX

## Keep Basic Claiming

The easy path should stay easy:

- Chest claims stay.
- The book stays, though it can be rewritten.
- Claim notifications stay.
- Rectangle claiming stays as the default first experience.

## Reduce Dependence On Physical Tools

A recode should stop assuming that a physical golden shovel is the only reasonable entry point.

Possible direction:

- `/claim` opens or enters claim mode.
- Players can still use the traditional tool if the server wants that.
- Servers do not need kits just to teach basic claiming.
- The plugin can give clearer stateful feedback for current claim mode.

The golden shovel can remain as a compatibility path, but it should not be the only sane path.

## Shaped Mode

This is the most important new feature.

### Goal

Allow a player to start from a normal 2D rectangular parent claim, then progressively shape it into an orthogonal polygon without forcing them into a noisy freeform editor.

### Minimal Change Proposal

Add a shaped-claim entry point such as:

`/shapedclaims` or `/shapedclaim`

In shaped mode:

- The player works on an existing top-level 2D claim.
- Right-clicking adds **nodes** on existing X/Z-aligned boundaries.
- Right-clicking in unclaimed space can also place new corner points for an orthogonal shape path.
- Nodes create separations in the claim edge graph.
- Later expansions operate on the segment between node-to-node bounds instead of always resizing the whole rectangle side.

Both of these belong to the same mode:

- editing an existing claim boundary by adding nodes
- sketching a new orthogonal polygon path in unclaimed space

They should not be split into separate shaped submodes unless implementation reality forces it later.

This is the key idea worth building around.

### Why This Works

It keeps the current GP mental model mostly intact:

- Corners are still corners.
- Sides are still 90 degrees.
- Expanding still feels like “push this wall out”.

But it adds the missing piece:

- a side can be split into editable sections
- each section can move independently
- the result becomes an orthogonal polygon instead of one rectangle
- players can also extend or sketch a valid orthogonal path into unclaimed space without learning a separate advanced tool

That is a strong middle ground between “rectangle only” and “full CAD tool”.

### Segment Editing Model

The MVP editing model should be:

1. Create a normal parent claim.
2. Enter shaped mode.
3. Add nodes to split a side into segments, or start a new orthogonal corner path into unclaimed space.
4. Stand near or target one segmented section.
5. Use expand/resize on that section only, or confirm the new corner path.
6. Keep the existing corner resize path as the fallback for whole-corner edits.

This matches your idea well: if the player is on the left or right side of a node-separated section, `/expandclaim` applies only to that bounded segment.

### Orthogonal Placement Rules

Shaped mode should enforce the 90-degree rule directly in the interaction model.

- after the first point, the next valid point must line up on either the same X or same Z axis
- the shape can be completed by clicking the starting corner again once a valid loop exists
- completion should require at least 4 sides
- invalid diagonal placement should be rejected immediately
- the player should get a direct chat message explaining why the point is invalid
- the editor should always prefer snapping over silent failure when the intended orthogonal point is obvious

This avoids the worst outcome: players thinking the tool is bugged when the real issue is that they are trying to place a diagonal edge.

### Shape Completion

A full polygon path should be allowed in shaped mode.

- the player places orthogonal corners in sequence
- once the path forms a valid loop, clicking the starting corner completes the claim shape
- the loop must remain simple and orthogonal
- completion should fail with a clear reason if the shape would self-intersect, collapse, or violate claim rules

This gives players a true polygon workflow when they want it.

### Practical Tradeoff

The cost of full polygon placement is travel.

As the number of sides increases, placing every corner manually becomes less convenient, especially for players without elytra or other mobility advantages. That is exactly why the node-on-existing-claim workflow matters.

The two workflows should coexist:

- full corner-by-corner polygon completion for players who want to sketch a new shape directly
- node splitting on an existing rectangular claim for players who want the same end result with less movement

Both workflows should sit under `/shapedclaims`.

That second path is not just a convenience feature. It is the accessibility path for normal players on foot.

### Guided Boundary Preview

The mode needs a strong live guide.

One good direction is:

- when the player is near a valid 2D claim boundary, shaped mode can auto-arm against that edge
- a visual marker appears at eye level on the claim boundary in X/Z space
- that marker follows the player while staying constrained to the active boundary
- once the first point is set, the preview should continue to show the only valid orthogonal direction(s)
- if the player drifts into an invalid diagonal placement, the preview should make that obvious before the click

Particles are a reasonable fit for this as long as they stay restrained and readable. The goal is not spectacle. The goal is to keep the player on track without needing external explanation.

### User Feedback Requirements

Shaped mode only works if feedback is obvious.

We need:

- active segment highlighting
- preview before commit
- invalid-shape warnings before placement
- explicit invalid-angle feedback in chat
- a clear distinction between “corner resize” and “segment resize”
- obvious exit from shaped mode back to basic mode
- boundary-following guidance when the player is close enough to edit a side

If visualization is weak, polygon claims will feel broken even if the geometry is correct.

## Visualization Plan

The visualization system should stop assuming a rectangle as the only geometry source.

Instead:

- claims provide a list of orthogonal boundary points or segments
- visualization consumes boundary geometry, not rectangle corners only
- the default renderer remains simple fake-block outlines
- no angled or curved visualization is needed in core

This should support:

- rectangular claims
- orthogonal polygon parent claims

## Recode Checklist

This section is the practical checkpoint for the current branch, not the ideal end-state.

### Done On Branch

- Top-level shaped parent claims exist as orthogonal X/Z polygons.
- Shaped claim geometry persists in the flatfile path.
- Shaped claim visuals follow polygon geometry instead of always falling back to rectangular shells.
- `AllowShapedClaims: false` exists as a config gate for shaped claim creation and editing.
- When shaped claims are disabled:
  - `/claim mode shaped` is not offered.
  - `/shapedclaim` and `/shapedclaims` are hidden or hard-disabled cleanly.
  - shaped-only helper behavior stays dormant and falls back to basic mode.
- `/shapedclaims` and `/shapedclaim` exist as shaped-mode entry points.
- `/claim mode shaped` exists.
- Right-click corner-to-corner polygon creation works for new shaped claims.
- Right-click existing-claim reshape paths work for owned top-level 2D claims.
- Right-click corner resize works while staying in shaped mode.
- Shift-click on an owned shaped boundary creates persistent segment markers.
- Shift-click on the same shaped marker removes it again.
- `/expandclaim` resolves a shaped boundary subsection and applies segment expansion.
- Repeated shaped corner/segment normalization now removes stale collinear marker leftovers when a nib is flattened back into a straight face.
- Nearby-claim visualization can be triggered from shaped mode.
- Selected-claim management commands work from resize selection context instead of only from player position.
- Addons can add `/claim` subcommands through a narrow selected-claim command hook.
- The older-version compatibility fixes for `COPPER_GOLEM_STATUES`, `CHAINS`, and chain material lookups are in.
- Temporary shaped debug logging has been removed.

### Working But Still Being Tightened

- `/expandclaim` on shaped segments is mostly working, but the “choose subsection by player stance/facing” behavior is still being refined.
- Right-click resize on segmented faces now has the proper geometry direction, but still needs more real in-game validation around left/right/center stance expectations.

### Still Missing Before Rebase-Ready

- Add `/claim` local management coverage where it improves usability:
  - keep `trust`, `trustlist`, `abandon`, and explosions obvious under `/claim`
  - make sure the selected-claim flow covers the remaining resize-oriented command variants cleanly
- Remove any branch-only exploratory comments that are no longer worth carrying into a rebase.
- Re-test with shaped claims both disabled and enabled.

### Final Shaped UX Item Still Requested

- Shift-clicking a claim boundary in **basic** claims mode should be allowed as a lightweight segment preview/setup path for convenience.
- Those basic-mode boundary segments should be temporary only:
  - they should not persist across visualization refreshes
  - they should disappear if the player does nothing with them
  - only deliberate `/shapedclaim` shift-click segmentation should persist to the claim

### Good Rebase Order

1. Finish the last `/expandclaim` stance/facing correctness pass.
2. Add the final `/claim` local management routing changes.
3. Add temporary basic-mode shift-click segmentation.
4. Remove branch-only exploratory comments.
5. Re-test with shaped claims both disabled and enabled.
- cuboid subclaims
- conflict previews
- active-segment previews in shaped mode

## Command And Interaction Architecture

## Dedicated Systems

The rewrite should break the plugin into clear subsystems:

- command layer
- tool interaction layer
- claim geometry/editor layer
- permission/trust layer
- visualization layer
- persistence layer

## Commands

Use a unified claim command structure, but keep it pragmatic:

- `/claim` for claim workflows
- standalone compatibility commands where useful
- shared internal handlers instead of duplicated command implementations

Important rule:

- command ownership must be explicit
- do not let addons silently steal built-in commands
- compatibility wrappers must delegate cleanly, not recurse

The `GPExpansion` `/trustlist` problem is exactly the kind of bug the recode should prevent by design.

## Tool Interactions

Claim tools should be handled in their own listener/service, not buried in a giant player event class.

Boundary rules:

- Bukkit events stay at the outer edge.
- Internal code receives normalized domain input.
- Selection state lives in a claim-editor/session object.
- Mode switching is explicit.

## Data Model

### Claim Geometry

Parent claims should store:

- claim id
- world
- owner/admin ownership
- Y bounds
- polygon node list for X/Z footprint
- metadata and trust settings

Subclaims should store:

- parent claim id
- cuboid bounds
- permissions
- metadata

### Migration

Existing rectangle claims convert cleanly:

- each rectangle becomes a 4-corner orthogonal polygon
- no loss of data
- old trust and ownership data remains

The migration path should be one-way and versioned.

## Rewrite Scope

This recode should not try to solve every historical GP problem at once.

Not in initial scope:

- arbitrary angled claims
- curved claims
- full 3D polygonal claims
- addon-defined geometry types in core
- deep modularity work that requires datastore plugins from day one

Those can come later if they still make sense.

## Proposed Phases

### Phase 0: Foundations

- split giant listener logic into dedicated listeners/services
- unify command ownership
- move claim editing into explicit editor/session classes
- define new internal geometry interfaces

### Phase 1: Rectangle Parity

- implement the new engine with rectangle claims only
- migrate existing GP behavior onto the new architecture
- prove parity for trust, resize, abandon, admin, and visualization

### Phase 2: Polygon Parent Claim Core

- add orthogonal polygon support for parent claims
- keep subclaims cuboid
- add polygon-aware overlap and containment checks
- update persistence schema

### Phase 3: Shaped Mode MVP

- `/shapedclaims`
- node insertion on orthogonal boundaries
- segment-aware expand/resize
- active segment visualization
- validation for simple orthogonal polygons

### Phase 4: UX Rewrite

- reduce hard dependency on physical golden shovel flow
- improve onboarding and claim-state messaging
- preserve chest claims and educational prompts
- add a cleaner mode/status presentation

### Phase 5: Public API Review

- expose only extension points core actually needs
- prefer Bukkit events where they fit
- avoid speculative registries
- document safe addon hooks after core settles

## Open Questions

- Should shaped mode require a tool at all, or should `/claim` mode be enough?
- Should segment expansion be command-only, tool-only, or both?
- Should node placement be allowed anywhere on a boundary, or only snapped to block positions with minimum spacing rules?
- How should parent-claim polygon editing interact with existing subdivisions?
- Do we keep “expand claim” as a command name, or replace it with a clearer shaped-mode verb?

## Initial Recommendation

Your node-based shaped-mode idea is good.

More specifically:

- it is easier to teach than full freeform polygon editing
- it preserves GP's current corner-and-side mental model
- it avoids ugly angle spam and hard-to-read borders
- it creates real shape freedom where server owners actually need it

If we do this recode, I would build around that idea rather than around arbitrary polygon drawing.

## First Concrete Deliverables

If this plan moves forward, the first implementation docs should be:

1. `geometry-model.md`
2. `claim-editor-state-machine.md`
3. `shaped-mode-ux.md`
4. `migration-plan.md`
5. `command-ownership-rules.md`

Those five docs would turn this plan into actual buildable work.

## Out Of Scope For Core Recode

For this core recode, stop at shaped claims and the core GP experience.

That means this plan does **not** currently include:

- a built-in claim map
- a built-in inventory claim editor
- GPExpansion-specific GUI planning
- bridge work for future map-based claiming

Those ideas may still be worth exploring later, but they should not drive the core rewrite right now.


requires new standalone commands /claimlavaflow /claimwaterflow /claimpistons with /claim pistons /claim waterflow mapping to those (alias.yml)

## Human Notes Interpreted

These notes point to three real product gaps that should stay in scope for the core recode.

### 1. Confusing Cross-Claim Mechanics

Players get confused by:

- water flow across claims
- lava flow across claims

This is not just a configuration problem. It is a feedback problem too.

Core recode goal:

- do not introduce a per-claim water/lava toggle system
- give a clear message when water or lava is being affected by claim boundary rules
- help the player understand where the relevant boundary/source interaction is happening so they know what to resize or why the flow stopped

Reason:

- a toggle-based fluid system creates weird non-survival behavior
- it opens niche but bad trap cases
- the real issue is player confusion, not lack of per-claim switches

So this should stay a feedback/clarity fix, not a new fluid-permission feature.

Pistons are different:

- the edge case is too niche to justify expanding core scope right now
- leave piston behavior alone for this recode unless another stronger reason appears later

### 2. Weak Claim Management Discovery

Right now, claim corner selection mostly leads to:

- resize
- abandon

That is too narrow for how players and staff actually manage claims.

Core recode goal:

- selecting a claim or corner should become a gateway into claim management actions
- the management flow should stay claim-local and obvious
- the most common owner actions should be reachable without memorizing a long command list

### 3. Better Claim-Local Commands

The suggested actions are all reasonable:

- abandon
- trustlist
- trust
- untrust
- containertrust, accesstrust etc
- claimexplosions
- witherexplosions

This should become a real design requirement:

- claim management actions should work naturally from the selected claim context
- standalone commands should still exist for direct use and aliases
- `/claim ...` subcommands should map cleanly to those standalone commands

## Revised Core Scope

The core recode should focus on:

- rectangle parity
- orthogonal polygon parent claims
- `/shapedclaims`
- segment-aware resizing from node-to-node boundaries
- better claim-local management UX
- clearer feedback around water/lava boundary behavior

Do not let the claim-map idea expand the core scope right now.

## Human Notes 2.0

After further consideration this water/lava flow toggle system allows non survival esque placements of water and lava if the player is +200iq, even leads to some pretty bad trapping cases like fooling a player there is a water source at the end of a hole but actually the water is held up by a claim rule the trapper set so they fall through the water to an even deeper hole to their death. pretty niche but something to think about. we could limit it to only be a message for the user and the location of the flow so they know where to resize / why its happening.

for pistons too its just too niche to include, just leave it to be honest.
