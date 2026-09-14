# SilentAuth

Account switcher for Minecraft 1.8.9 (Forge). Log in with a session token, with a
Microsoft account through the device code flow, or offline, and send the auth traffic
(optionally the game traffic as well) through HTTP, SOCKS4 or SOCKS5 proxies.

## Features

- **Session token login** - paste a token, the profile endpoint fills in the name and uuid,
  the session is swapped in place without restarting the game.
- **Microsoft device code sign in** - no password is ever typed into the game. A code is
  shown, you enter it at `microsoft.com/link`, and the refresh token is stored so the
  account can be reused later.
- **Offline accounts** - plain username for servers in offline mode.
- **Proxies** - HTTP, SOCKS4 and SOCKS5, with or without credentials. Each account can be
  bound to its own proxy, or a default can be set for everything.
- **Proxied joining** - a local relay forwards the game connection through the proxy and
  rewrites the handshake so the server still sees the real hostname.
- **Encrypted storage** - tokens are stored AES-GCM encrypted under a key file next to them,
  never in plain text, and never written to the log.

## Building

The 1.8.9 toolchain needs **Java 8** and an old Gradle. Newer versions of either will fail
on ForgeGradle 2.1.

```bash
gradle setupDecompWorkspace   # Gradle 4.10.3, JDK 8
gradle build
```

The jar lands in `build/libs/`. Drop it into `.minecraft/mods` next to Forge
`11.15.1.2318` for 1.8.9.

For IDEA: run `gradle setupDecompWorkspace idea` first, then import the project.

## Using it

A **SilentAuth** button appears in the top left of the main menu and the server list.
Right shift opens the same screen in game, and `/sa` works from chat.

| Command | What it does |
| --- | --- |
| `/sa` or `/sa gui` | opens the account screen |
| `/sa list` | lists stored accounts |
| `/sa login <name>` | switches to a stored account |
| `/sa status` | shows the current session and proxy |
| `/sa proxy list` | lists proxies with their last latency |
| `/sa proxy add <proxy>` | adds a proxy |
| `/sa proxy default <host:port>` | sets the default proxy |
| `/sa proxy off` | stops using a default proxy |

### Proxy formats

All of these parse:

```
1.2.3.4:1080
1.2.3.4:1080:user:pass
user:pass@1.2.3.4:1080
socks5://user:pass@1.2.3.4:1080
http://1.2.3.4:8080
```

Without a scheme the type from the config (`defaultType`, SOCKS5 out of the box) is used.
`Import file` on the proxy screen reads `config/silentauth/proxies.txt`, one per line,
`#` for comments.

### Where things are stored

```
config/silentauth/silentauth.cfg   settings
config/silentauth/accounts.json    accounts, tokens encrypted
config/silentauth/proxies.json     proxies, passwords encrypted
config/silentauth/key.bin          local key, owner readable only
config/silentauth/proxies.txt      optional import list
```

Delete `key.bin` and every stored token becomes unreadable, which is the quickest way to
wipe the accounts if the machine changes hands.

## How the session swap works

`Minecraft.session` is replaced through reflection (both MCP and SRG names are tried, so the
same jar works in a dev workspace and in a normal install), and the `MinecraftSessionService`
is rebuilt on top of the selected proxy so the `joinServer` call that authenticates you to a
server goes out through that proxy too. Everything that touches the network runs off the
render thread; only the swap itself is scheduled back onto the client thread.

## Proxied joining

Normal joins from the server list use the game's own connection. To put the game traffic
through a proxy as well, use **Proxies -> Proxied join**: it opens a listener on
`127.0.0.1`, connects out through the proxy, rewrites the address inside the handshake
packet so virtual hosts and forge's `\0FML\0` marker survive, and then hands the local port
to the vanilla connect screen.

## Notes

- Tokens expire. Microsoft accounts refresh themselves on login; session token accounts have
  to be updated by hand when `Check token` starts failing.
- Some proxies do not allow CONNECT to port 25565, in which case only the auth traffic can be
  proxied.
- HTTP proxies that require credentials for HTTPS tunnels need
  `-Djdk.http.auth.tunneling.disabledSchemes=` in the launch arguments, a JVM restriction
  rather than a mod one.
- Nothing in this repository was copied from SchubiAuthV2. The auth flows here are the
  documented Microsoft and Mojang endpoints, written from scratch, and the only outbound
  connections are to `login.microsoftonline.com`, `*.auth.xboxlive.com`,
  `api.minecraftservices.com`, `sessionserver.mojang.com` and whatever proxy you configure.
