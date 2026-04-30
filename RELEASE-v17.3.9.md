# GriefPrevention3D v17.3.9

**Wiki:** https://github.com/castledking/GriefPrevention3D/wiki

## Highlights

- **Fix duplicate permission messages** - Ender pearl access denial messages now only send once on Purpur/Folia
- **Folia fix** - Boundary violation alert scheduler now correctly uses the Folia-safe `SchedulerUtil` instead of `Bukkit.getScheduler()` directly
- **Rename back to GriefPrevention** - Renaming to 'GriefPrevention3D broke compatibility with GPFlags and other plugins that depend on GriefPrevention by name.

## Fix Duplicate Permission Messages

On Purpur and Folia servers, both `PlayerTeleportEvent` and `ProjectileHitEvent` fire for ender pearls. Previously, both handlers would send the "You don't have player's permission to use that" message when access was denied, resulting in 2-3 duplicate messages.

Fixed by adding a deduplication check in `PlayerTeleportEvent` handler — it now skips sending the message if `ProjectileHitEvent` already handled the denial (tracked via the existing `refundedByProjectileHitEvent` set).

## Folia Fix

The boundary violation alert scheduler in `ClaimBoundaryViolationTracker` was calling `Bukkit.getScheduler().runTaskLater()` directly, which is not available on Folia servers.

Fixed to use `SchedulerUtil.runLaterGlobal()` — the existing Folia-safe adapter used throughout the rest of the plugin. This routes through Folia's `GlobalRegionScheduler` when present and falls back to `Bukkit.getScheduler()` on Paper/Spigot.

## Migration

No configuration changes or data migration required.
