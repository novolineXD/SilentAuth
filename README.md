# SilentAuth

Account manager for Minecraft 1.8.9 (Forge). Session tokens and proxies are typed straight
into the game: there are no config files to edit, no lists to prepare, and no server is ever
joined by the mod itself.

## Features

- **Session token login** - paste a token, the profile endpoint fills in the name and uuid,
  and the session is swapped in place without restarting the game.
- **Microsoft device code sign in** - no password is ever typed into the game. A code is
  shown, you enter it at `microsoft.com/link`, and the refresh token is stored so the
  account can be reused later.
- **Offline accounts** - plain username, no token.
- **Proxies** - HTTP, SOCKS4 and SOCKS5, with or without credentials. Each account can be
  bound to its own proxy, or a default can be set for everything. Every token check, sign in
  and session-server call then goes out through it.
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

Start the game and open **Multiplayer**. The **SilentAuth** button sits in the top left of
that screen (and of the title screen). Everything happens from there.

### Adding a session token

```
Multiplayer -> SilentAuth -> Add account -> paste into the token field -> Add
```

The type button cycles between **Session**, **Microsoft** and **Offline**. On Session the
pasted text is read by shape, so any of these work as they are:

```
eyJhbGciOiJIUzI1NiJ9.token.here
Notch:eyJhbGciOiJIUzI1NiJ9.token.here
token:eyJhbGciOiJIUzI1NiJ9.token.here:069a79f444e94726a5befca90e38aaf5
Notch:069a79f4-44e9-4726-a5be-fca90e38aaf5:eyJhbGciOiJIUzI1NiJ9.token.here
```

A name and uuid found in the paste, or typed into the two optional fields, are used as they
are. Otherwise the token is looked up against the profile endpoint, which also confirms it
still works. The proxy button on the same screen picks which proxy that check goes through.

### Adding a proxy

```
Multiplayer -> SilentAuth -> Proxies -> Add -> paste into the proxy field -> Save
```

```
1.2.3.4:1080
1.2.3.4:1080:user:pass
user:pass@1.2.3.4:1080
socks5://user:pass@1.2.3.4:1080
http://1.2.3.4:8080
```

Without a scheme the type from the type button is assumed. Saving tests the proxy straight
away and the list shows the latency, or why it failed.

### The rest of the screens

| Screen | Buttons |
| --- | --- |
| Accounts | Log in, Add account, Remove, Check token, Set proxy, Proxies |
| Proxies | Add, Remove, Test, Test all, Set default, Bind to account |

`Set proxy` steps the selected account through the stored proxies and then back to the
default, so an account can be pointed at its own proxy before it is ever used. The search box
at the top of the account list filters by name or type.

Right shift opens the same screen in game. `/sa` works from chat as a shortcut for anything
above: `/sa list`, `/sa login <name>`, `/sa status`, `/sa proxy list`, `/sa proxy add <proxy>`,
`/sa proxy default <host:port>`, `/sa proxy off`.

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

`Minecraft.session` is replaced through reflection (both MCP and SRG names are tried, so the
same jar works in a dev workspace and in a normal install), and the `MinecraftSessionService`
is rebuilt on top of the selected proxy, so anything the game later asks Mojang about the
session travels the same route. Everything that touches the network runs off the render
thread; only the swap itself is scheduled back onto the client thread.

## Notes

- Tokens expire. Microsoft accounts refresh themselves on login; session token accounts have
  to be replaced by hand when `Check token` starts failing.
- HTTP proxies that require credentials for HTTPS tunnels need
  `-Djdk.http.auth.tunneling.disabledSchemes=` in the launch arguments, a JVM restriction
  rather than a mod one.
- Nothing in this repository was copied from SchubiAuthV2. The auth flows here are the
  documented Microsoft and Mojang endpoints, written from scratch, and the only outbound
  connections are to `login.microsoftonline.com`, `*.auth.xboxlive.com`,
  `api.minecraftservices.com`, `sessionserver.mojang.com` and whatever proxy you configure.
