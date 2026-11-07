# Immersive Portals Mod for Forge

> **This is Immersive Portals for Vault Hunters**, an unofficial fork of the Forge 1.18.2 version (1.4.20) made
> compatible with Vault Hunters 3 (Remastered 3.0.1 and 3rd Edition 3.21.7): see-through vault portals that keep Vault
> Hunters' own rules, plus the engine fixes the packs need. All changes from upstream are listed in [CHANGELOG.md](CHANGELOG.md). Immersive Portals
> is made by qouteall and kept alive on Forge by Nick1st; this fork stays under the Apache License 2.0. Please report
> problems with this fork [here](https://github.com/Masuary/ImmersivePortalsModForForge/issues), not to the original
> authors.
>
> Build: `./gradlew build` with JDK 17 (run `clean` as a separate command, and do not pass `--parallel`).

It's a Minecraft mod that provides see-through portals and seamless teleportation. It also can create "Non-Euclidean" (Uneuclidean) space effect.

![immptl.png](https://i.loli.net/2021/09/30/chHMG45dsnZNqep.png)

[On CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-mod)     [On Modrinth](https://modrinth.com/mod/immersiveportals)     [Website](https://qouteall.fun/immptl/)

This mod changes a lot of underlying Minecraft mechanics. This mod allows the client to load multiple dimensions at the same time and synchronize remote world information(blocks/entities) to client. It can render portal-in-portals. The portal rendering is roughly compatible with some versions of Sodium and Iris. The portal can transform player scale and gravity direction.  [Implementation Details](https://qouteall.fun/immptl/wiki/Implementation-Details)

## API

This mod also provides some API for:

* Manage see-through portals
* Dynamically add dimensions
* Synchronize remote chunks to client
* Render the world into GUI
* Other utilities

[API description](https://qouteall.fun/immptl/wiki/API-for-Other-Mods.html).

## How to run this code
https://docs.minecraftforge.net/en/1.18.x/gettingstarted/#from-zero-to-modding

## Other

[Wiki](https://qouteall.fun/immptl/wiki/)

[Discord Server](https://discord.gg/BZxgURK)

[Support qouteall on Patreon](https://www.patreon.com/qouteall)

The forge version is currently maintained by @Nick1st
