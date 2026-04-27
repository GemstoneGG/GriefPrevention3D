# GriefPrevention3D v17.3.8

**Wiki:** https://github.com/castledking/GriefPrevention3D/wiki

## Highlights

- **Official plugin rename** - Plugin is now officially named `GriefPrevention3D` (output jar, `plugin.yml`, dependency declarations)
- **Folia fix** - Boundary violation alert scheduler now correctly uses the Folia-safe `SchedulerUtil` instead of `Bukkit.getScheduler()` directly

## Plugin Rename

The plugin's official name is now `GriefPrevention3D` across the board:

- `plugin.yml` `name:` field changed from `GriefPrevention` to `GriefPrevention3D`
- Output jar is now `GriefPrevention3D.jar`
- Maven `<artifactId>` and `<name>` updated to `GriefPrevention3D`

### Impact on addon/dependent plugins

If you have plugins that declare a dependency on this plugin, update your `plugin.yml`:

```yaml
# Before
softdepend: [GriefPrevention]

# After
softdepend: [GriefPrevention3D]
```

JitPack dependency coordinates also updated to `com.github.castledking:GriefPrevention3D`.

## Folia Fix

The boundary violation alert scheduler in `ClaimBoundaryViolationTracker` was calling `Bukkit.getScheduler().runTaskLater()` directly, which is not available on Folia servers.

Fixed to use `SchedulerUtil.runLaterGlobal()` — the existing Folia-safe adapter used throughout the rest of the plugin. This routes through Folia's `GlobalRegionScheduler` when present and falls back to `Bukkit.getScheduler()` on Paper/Spigot.

## Migration

- **Server owners**: rename the jar file reference in your startup scripts if you have any hardcoded references to `GriefPrevention.jar`.
- **Addon developers**: update `softdepend`/`depend` in your `plugin.yml` to `GriefPrevention3D`.
- **No claim data migration required.** Existing claims load without modification.
