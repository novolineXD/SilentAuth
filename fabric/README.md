# SilentAuth (Fabric)

The modern-Minecraft port of SilentAuth, built with Fabric for **1.21.11**. It is the same mod as
the 1.8.9 Forge build - session-token login, Microsoft device-code sign in, offline accounts, a
username spoofer, and proxies that also carry the game connection - rebuilt on the modern client.

All of the account, auth, proxy, crypto and networking logic is shared, byte for byte, with the
1.8.9 build; only the thin layer that touches Minecraft is written against the modern API:

- `fabric/mixin/MinecraftClientAccessor` - opens the private `session`, `networkProxy` and
  `apiServices` fields so the swap can replace them (the mixin equivalent of the 1.8.9 reflection).
- `account/SessionSwapper` - swaps the session and rebuilds the api services on the chosen proxy.
- `fabric/ProxiedJoin` + `fabric/mixin/ConnectScreenMixin` - every server join funnels through
  `ConnectScreen.connect`, so one mixin reroutes the connection through the local relay; pings are
  left alone.
- `gui/*` - the mauve glass screens, drawn with `DrawContext` instead of the 1.8.9 tessellator.
- `fabric/SilentAuthClient` - the entry point: sets things up, registers the open key and polls it
  every tick so the screen opens from anywhere, and registers `/sa`.

## Using it

Drop the jar into `.minecraft/mods` alongside **Fabric Loader** and **Fabric API** for 1.21.11.
Press **Right Ctrl** to open the screen (rebind under Options, Controls, Miscellaneous), or use
`/sa`. Everything else works exactly as the [root README](../README.md) describes.

## Building

The Fabric toolchain needs **JDK 25** (which modern Minecraft also requires) and uses Loom 1.18.2
on Gradle 9.7.

```bash
./gradlew build
```

The jar lands in `build/libs/silentauth-fabric-<version>.jar` (the one without the `-sources`
suffix). Run `./gradlew genSources` first if you want the decompiled Minecraft sources in the IDE.

## Targeting a different version

The Minecraft, Yarn, loader and Fabric-API versions live in `gradle.properties`. Because the
Minecraft-facing surface the mod uses is small, bumping those to a newer 1.21.x / 26.x release and
rebuilding is usually all that is needed; only a rename in the modern client API would need a code
change, which the compiler points straight at.
