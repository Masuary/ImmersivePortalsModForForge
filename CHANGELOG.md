# Changelog: Immersive Portals for Vault Hunters

Unofficial fork of [Immersive Portals for Forge](https://github.com/iPortalTeam/ImmersivePortalsModForForge) (1.18
branch, 1.4.20) by qouteall, maintained on Forge by Nick1st, made compatible with Vault Hunters 3 Remastered. Changes
below are relative to upstream 1.4.20.

## 1.4.20-vh3r.1

Built for Minecraft 1.18.2, Forge 40.2, Vault Hunters 3 Remastered 3.0.1 (`the_vault` 1.18.2-21.6.1-remastered.6956).
Server and clients must run this same build: its network protocol (`1.0-vh3r1`) does not accept stock Immersive
Portals on the other side.

### Vault portals you can see through

- A lit Vault Hunters portal frame in the hub becomes a window into its vault's start room, and the start-room portal
  shows the hub back.
- Walking through the window runs Vault Hunters' own join (party, crystal and every other check); Immersive Portals
  only does the teleport. Walking out through the start-room portal runs Vault Hunters' own exit and bail rules. A
  refused crossing leaves you standing where you were.
- Works for every framed crystal type (classic, Personal, Paradox, PvP, Royale), including rotated start rooms. Frames
  wider or taller than the start-room portal get a window stretched over the whole frame, and the window back to the
  hub covers the whole start-room portal even when the hub frame is smaller.
- Works for a player who enters alone: the hub frame stays loaded until its window is made.
- Lighting a frame creates its vault at once (made by Vault Hunters itself, with the lighter as its owner), so the frame
  shows the vault before anyone enters, and the first player walks in through the window too. The vault timer still
  only starts once a runner leaves the start room. An unentered vault is dropped when its frame is broken; config
  `vaultPreview` (`untilBroken` by default, or `untilRestart`, `timeout` with `vaultPreviewTimeoutMinutes`, or `off`
  for Vault Hunters' own behaviour) decides whether a restart or a timeout drops it too. A dropped vault's frame stays
  lit and makes a fresh vault on the next touch.
- Floor (horizontal) frames and Greed challenge crystals keep the normal Vault Hunters portal. Completion, death, kick
  and timeout keep Vault Hunters' own teleport.

### Vault rules

- No other Immersive Portals crossing can enter or leave a vault (players, spectators, items, projectiles, pearls,
  mobs).
- No remote block actions into or out of a vault level through a portal. With Vault Hunters installed, remote reach
  through portals is capped at 7 blocks.
- A player who ends up in a vault without being one of its runners (and is not a spectator or operator) is sent out
  after 2 seconds through Vault Hunters' own paths. Players still inside a vault that is being deleted are moved out.
- Fixed a crash in Vault Hunters' spirit handling when such a player died in a vault.

### Engine fixes for the Remastered pack

- Fixed: the game crashed at startup next to Vault Hunters (packet size limit change). Immersive Portals' change is now
  optional, so Vault Hunters keeps its own.
- Fixed: every multiplayer join was kicked with Connectivity installed.
- Fixed: a deadlock between vault ticking and portals. Vault levels now tick on the server thread (about 2.8 ms per
  active vault per tick, measured on a test server).
- Fixed: vault rooms placed by Vault Hunters did not show for players watching from another dimension.
- Fixed: a crash on server stop; servers with players in vaults stop cleanly.
- Fixed: dimension types that are not registered (vault levels) failed to sync to clients.
- Fixed: removed levels (closed vaults) left Immersive Portals state behind on server and client; portals into a
  removed level are removed at once.
- Portal crossings now post Forge's `EntityTravelToDimensionEvent`, so other mods can veto them.
- New config `restoreMissingVanillaDimensions` (off by default): Immersive Portals no longer recreates the Nether and
  the End on servers that removed them.
- New config `portalDisabledDimensions`: dimension ids or whole namespaces where portals are not shown, loaded or
  crossed.
- New configs `vaultPreview` and `vaultPreviewTimeoutMinutes` (see the vault portals above).
- Compatibility warnings are off by default (`enableWarning = false`); an admin can turn them back on.

### Mod compatibility and other fixes

- Fixed: Vault Hunters' end-of-vault screen showed empty tabs (no stats, no vault map) on a client with Immersive
  Portals. Immersive Portals changed the stencil state behind Minecraft's back, which broke the clipping Vault Hunters
  uses for its scrolling panels.
- Steam 'n' Rails: the conductor camera now loads and tracks chunks around the conductor.
- Taterzens: NPCs spawn without client errors and show up; Taterzens is no longer listed as incompatible.
- Fixed: a player sent out of a vault could land at the vault's coordinates in the hub ("Entity moves too fast" errors).
- Fixed: a player whose crossing was refused was put 1.62 blocks too low.
- Remote block breaking through a portal needs real mining progress, as in vanilla.
- Fixed: the Create World screen hook never applied in production.
- Fixed: a duplicate world generator registration error in the log.
- Backported shutdown and chunk-loading fixes from the Fabric versions.
- Fixed: cross-portal collision crashed when the portal's destination level had been removed.

### Build

- All dependencies resolve from CurseMaven or public Maven repositories; the project builds from a clean clone with
  JDK 17.
- Zero compiler warnings.
