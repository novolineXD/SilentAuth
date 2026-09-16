# SilentAuth

Account manager for Minecraft 1.8.9 (Forge). Session tokens and proxies are typed straight
into the game: there are no config files to edit, no lists to prepare, and no server is ever
joined by the mod itself.

## Features

- **Session token login** - paste a token, the profile endpoint fills in the name and uuid,
  and the session is swapped in place without restarting the game.
- **Microsoft device code sign in** - no password is ever typed into the game. A code is
  shown, you enter it at `microsoft.com/link`, and the refresh token is stored so the
  account can be reused later. Expired tokens refresh themselves on the next login.
- **Proxies** - HTTP, SOCKS4a and SOCKS5, with or without credentials. When a proxy is in use
  the whole connection travels through it: the token check and sign in, the session server
  calls, and - crucially - the connection to the game server itself, so the server sees the
  proxy's IP and not yours. The proxy resolves the server's address, so that lookup does not
  leak either.
- **Encrypted storage** - tokens are stored AES-GCM encrypted under a key file next to them,
  never in plain text, and never written to the log. Saves are atomic, so a crash halfway
  through a write cannot lose the accounts file.

## Installing

Grab the jar from the [releases page](https://github.com/novolineXD/SilentAuth/releases) or
build it below, then drop it into `.minecraft/mods` next to Forge `11.15.1.2318` for 1.8.9.
The mod is client side only.

## Building

The 1.8.9 toolchain needs **Java 8**. The Gradle wrapper pins Gradle 4.10.3, because newer
versions of either will fail on ForgeGradle 2.1.

```bash
./gradlew setupDecompWorkspace   # decompiles Minecraft, slow, only needed once
./gradlew build
```

The jar lands in `build/libs/SilentAuth-<version>.jar`.

For IDEA: run `./gradlew setupDecompWorkspace idea` first, then import the project.

## Using it

Open **Multiplayer** (or stay on the title screen). The **SilentAuth** button sits in the top
left of both. **Right Ctrl** opens the screen from anywhere - the main menu, the server list or in game -
so it works even on clients like Lunar that replace the vanilla menus. The key can be rebound
under Options, Controls, SilentAuth.

Everything happens from there. `/sa` does the same things from chat, with tab completion.

### Adding an account

```
SilentAuth -> Add account -> paste -> Log in
```

One field. Paste a session token, press **Log in**, and you are on that account - it is saved
on the way, so there is no separate add-then-select step. **Save only** stores it without
switching. The pasted text is read by shape, so any of these work as they are:

```
eyJhbGciOiJIUzI1NiJ9.token.here
Notch:eyJhbGciOiJIUzI1NiJ9.token.here
token:eyJhbGciOiJIUzI1NiJ9.token.here:069a79f444e94726a5befca90e38aaf5
Notch:069a79f4-44e9-4726-a5be-fca90e38aaf5:eyJhbGciOiJIUzI1NiJ9.token.here
```

If the paste already contains a name and a uuid they are used as they are and nothing is
looked up. Otherwise the token goes to the profile endpoint once, which fills in the name and
uuid and proves the token still works.

**Microsoft sign in** is a button on the same screen, so there is no account type to pick
first.

### Getting your own account back

**Your account** is always the first row in the list, standing for the session the launcher
started the game with. Click it to go back to yourself and stop using any proxy. `/sa restore`
does the same from chat. Switching away is never one way, and it does not need a restart.

### Adding a proxy

```
SilentAuth -> Proxies -> Add -> paste -> Add
```

One field again, and it takes a whole list at once - separate them with spaces or commas.
Each one is tested as soon as it is added, and the list shows the latency or why it failed.

```
1.2.3.4:1080
1.2.3.4:1080:user:pass
user:pass@1.2.3.4:1080
socks5://user:pass@1.2.3.4:1080
http://1.2.3.4:8080
```

The scheme in the text sets the type. Anything without one uses `proxy.defaultType` from the
config (`SOCKS5` unless you change it), so there is nothing to choose on screen. Pasting a
proxy whose host and port are already stored updates that entry instead of adding a duplicate.

### The screens

Both screens are the same shape: a field at the top to paste into, a list under it, and the
way out at the bottom.

| Screen | What a row does | Buttons |
| --- | --- | --- |
| Accounts | Click to play as it, x to forget it | Add, Microsoft sign in, Proxies, Close |
| Proxies | Click to use it for everything, x to drop it | Add, Back |

Clicking the account already in use does nothing; clicking the proxy already in use turns it
off again. The arrow keys move the selection, enter acts on it, and the mouse wheel scrolls.

Nothing is checked by hand. Tokens are validated and proxies are tested when the screen
opens, and results are cached for five minutes so reopening is instant. Rows read **green**
when in use, **red** when the token was rejected or the proxy is dead, and grey otherwise -
and every colour is paired with a word, so it still reads without colour.

### Commands

`/sa` (or `/silentauth`) with no arguments opens the screen.

| Command | Does |
| --- | --- |
| `/sa list` | Lists the stored accounts, the active one in green |
| `/sa login <name>` | Switches to that account |
| `/sa restore` | Switches back to your own account |
| `/sa status` | Shows the current session and proxy |
| `/sa proxy list` | Lists the stored proxies and their latency |
| `/sa proxy add <proxy>` | Adds a proxy in any of the formats above |
| `/sa proxy default <host:port>` | Makes that proxy the default |
| `/sa proxy off` | Clears the default proxy |

### Settings

`config/silentauth/silentauth.cfg`, written on first launch.

| Setting | Default | Does |
| --- | --- | --- |
| `general.refreshOnLogin` | `true` | Refresh expired Microsoft tokens when switching to that account |
| `general.showMainMenuButton` | `true` | Add the button to the title and multiplayer screens |
| `general.restoreLastAccount` | `false` | Switch back to the last used account when the game starts |
| `general.microsoftClientId` | Minecraft's | Azure application id used for the device code sign in |
| `proxy.routeGameThroughProxy` | `true` | Carry the actual server connection through the proxy, so the server sees the proxy IP |
| `proxy.proxyAuthRequests` | `true` | Send login and token checks through the account's proxy |
| `proxy.defaultType` | `SOCKS5` | Type assumed for proxies pasted without a scheme |

### Where things are kept

The mod writes these itself; nothing here has to be opened by hand.

```
config/silentauth/silentauth.cfg   settings
config/silentauth/accounts.json    accounts, tokens encrypted
config/silentauth/proxies.json     proxies, passwords encrypted
config/silentauth/key.bin          local key, owner readable only
```

Delete `key.bin` and every stored token becomes unreadable, which is the quickest way to
wipe the accounts if the machine changes hands.

## How the session swap works

`Minecraft.session` is replaced through reflection, and the `MinecraftSessionService` is
rebuilt on top of the selected proxy, so anything the game later asks Mojang about the
session travels the same route. The fields are found by their mapped and obfuscated names,
with a search by field type as a fallback, so the same jar works in a dev workspace and in a
normal install even if a name moves.

Everything that touches the network runs off the render thread; only the swap itself is
scheduled back onto the client thread.

The game's own connection to a server does not use that proxy field - Minecraft opens a
direct socket. So when a proxy is in use the mod starts a small relay on `127.0.0.1`, points
the game at it, and the relay tunnels the connection through the proxy, rewriting the first
handshake packet so the server still receives its own hostname (the Forge `FML` marker is
kept). This covers joining from the server list, a double click, Direct Connect and LAN. Turn
it off with `proxy.routeGameThroughProxy` if you only want the auth traffic proxied.

Proxy credentials are handed to the JDK through a `java.net.Authenticator` rather than a
`Proxy-Authorization` header, because on an HTTPS request that header would travel inside the
tunnel and reach Mojang rather than the proxy. The mod's own calls bind their credentials per
request; the proxy the session is using stays registered so the session server calls the game
makes on its own threads while joining a server authenticate too.

## Notes

- Tokens expire. Microsoft accounts refresh themselves on login; a session token account
  turns red once its token stops working and has to be replaced by pasting a new one.
- Java 8u111 and later refuse Basic proxy credentials on an HTTPS tunnel. SilentAuth lifts
  that restriction itself at startup, so an HTTP proxy that needs a username and password
  works without launch arguments. If the log says it could not, add
  `-Djdk.http.auth.tunneling.disabledSchemes=` to the JVM arguments.
- Nothing in this repository was copied from SchubiAuthV2. The auth flows here are the
  documented Microsoft and Mojang endpoints, written from scratch, and the only outbound
  connections are to `login.microsoftonline.com`, `*.auth.xboxlive.com`,
  `api.minecraftservices.com`, `sessionserver.mojang.com` and whatever proxy you configure.
