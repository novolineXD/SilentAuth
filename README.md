# SilentAuth

Account manager for Minecraft 1.8.9 (Forge). Everything happens in the menus: add a session
token, sign in with a Microsoft account, bind proxies, switch the running session. No server
is ever joined by the mod itself.

## Features

- **Session token login** - paste a token or import a list of them, the profile endpoint
  fills in the name and uuid, and the session is swapped in place without restarting.
- **Microsoft device code sign in** - no password is ever typed into the game. A code is
  shown, you enter it at `microsoft.com/link`, and the refresh token is stored so the
  account can be reused later.
- **Offline accounts** - plain username, no token.
- **Proxies** - HTTP, SOCKS4 and SOCKS5, with or without credentials. Each account can be
  bound to its own proxy, or a default can be set for everything. Every token check, sign in
  and session-server call then goes out through it.
- **Bulk import** - a text file of tokens and a text file of proxies, both read from the
  config folder.
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

A **SilentAuth** button sits in the top left of the main menu and the server list, so the
whole mod is reachable straight from the title screen. Right shift opens the same screen in
game, and `/sa` works from chat.

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

### Importing tokens

`Import file` on the account screen reads `config/silentauth/accounts.txt`, one account per
line, `#` for comments. The shape of each field is detected, so all of these work:

```
eyJhbGciOiJIUzI1NiJ9.token.here
Notch:eyJhbGciOiJIUzI1NiJ9.token.here
token:eyJhbGciOiJIUzI1NiJ9.token.here:069a79f444e94726a5befca90e38aaf5
Notch:069a79f444e94726a5befca90e38aaf5:eyJhbGciOiJIUzI1NiJ9.token.here
```

A line that carries both a name and a uuid is stored as is. A line with only a token is
looked up against the profile endpoint through the selected proxy, which also confirms the
token still works.

### Proxy formats

```
1.2.3.4:1080
1.2.3.4:1080:user:pass
user:pass@1.2.3.4:1080
socks5://user:pass@1.2.3.4:1080
http://1.2.3.4:8080
```

Without a scheme the type from the config (`defaultType`, SOCKS5 out of the box) is used.
`Import file` on the proxy screen reads `config/silentauth/proxies.txt` the same way, and
`Test` opens a real connection through the proxy to measure it.

`Set proxy` on the account screen steps the selected account through the stored proxies and
then back to the default, so an account can be pointed at its own proxy before it is ever
used.

### Where things are stored

```
config/silentauth/silentauth.cfg   settings
config/silentauth/accounts.json    accounts, tokens encrypted
config/silentauth/proxies.json     proxies, passwords encrypted
config/silentauth/key.bin          local key, owner readable only
config/silentauth/accounts.txt     optional import list
config/silentauth/proxies.txt      optional import list
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
