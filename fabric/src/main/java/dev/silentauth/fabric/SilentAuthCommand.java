package dev.silentauth.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.LoginService;
import dev.silentauth.account.SessionSwapper;
import dev.silentauth.gui.AccountScreen;
import dev.silentauth.net.IpCheck;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.proxy.ProxyParser;
import dev.silentauth.util.Async;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;

/** The {@code /sa} (and {@code /silentauth}) client command - the chat counterpart of the screens. */
public final class SilentAuthCommand {

    private SilentAuthCommand() {
    }

    private static final SuggestionProvider<FabricClientCommandSource> ACCOUNTS = (ctx, builder) -> {
        for (Account account : SilentAuth.accounts().all()) {
            builder.suggest(account.getUsername());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> PROXIES = (ctx, builder) -> {
        for (ProxyEntry entry : SilentAuth.proxies().all()) {
            builder.suggest(entry.getHost() + ":" + entry.getPort());
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(build("silentauth"));
        dispatcher.register(build("sa"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        return ClientCommandManager.literal(name)
                .executes(ctx -> openGui())
                .then(ClientCommandManager.literal("gui").executes(ctx -> openGui()))
                .then(ClientCommandManager.literal("list").executes(SilentAuthCommand::list))
                .then(ClientCommandManager.literal("status").executes(SilentAuthCommand::status))
                .then(ClientCommandManager.literal("restore").executes(SilentAuthCommand::restore))
                .then(ClientCommandManager.literal("login")
                        .then(ClientCommandManager.argument("username", StringArgumentType.greedyString())
                                .suggests(ACCOUNTS)
                                .executes(ctx -> login(ctx, StringArgumentType.getString(ctx, "username")))))
                .then(ClientCommandManager.literal("join")
                        .then(ClientCommandManager.argument("address", StringArgumentType.string())
                                .executes(ctx -> join(ctx, StringArgumentType.getString(ctx, "address")))))
                .then(ClientCommandManager.literal("proxy")
                        .then(ClientCommandManager.literal("list").executes(SilentAuthCommand::proxyList))
                        .then(ClientCommandManager.literal("check").executes(SilentAuthCommand::proxyCheck))
                        .then(ClientCommandManager.literal("off").executes(SilentAuthCommand::proxyOff))
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("proxy", StringArgumentType.greedyString())
                                        .executes(ctx -> proxyAdd(ctx, StringArgumentType.getString(ctx, "proxy")))))
                        .then(ClientCommandManager.literal("default")
                                .then(ClientCommandManager.argument("endpoint", StringArgumentType.greedyString())
                                        .suggests(PROXIES)
                                        .executes(ctx -> proxyDefault(ctx, StringArgumentType.getString(ctx, "endpoint"))))));
    }

    // ------------------------------------------------------------------ subcommands

    private static int openGui() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(new AccountScreen(client.currentScreen)));
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        java.util.List<Account> accounts = SilentAuth.accounts().all();
        if (accounts.isEmpty()) {
            reply(source, "No accounts stored yet", Formatting.GRAY);
            return 1;
        }
        reply(source, accounts.size() + " accounts:", Formatting.GRAY);
        for (Account account : accounts) {
            ProxyEntry proxy = LoginService.resolveProxy(account);
            boolean active = SilentAuth.accounts().isActive(account);
            reply(source, "- " + account.getUsername() + "  " + account.getType().getLabel()
                            + (proxy == null ? "" : " via " + proxy.describe()),
                    active ? Formatting.GREEN : Formatting.WHITE);
        }
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        reply(source, "Session: " + SessionSwapper.currentUsername(), Formatting.GRAY);
        ProxyEntry proxy = LoginService.resolveProxy(SilentAuth.accounts().getActive());
        reply(source, "Proxy: " + (proxy == null ? "none" : proxy.describe() + " " + proxy.statusText()),
                Formatting.GRAY);
        return 1;
    }

    private static int login(CommandContext<FabricClientCommandSource> ctx, String username) {
        final FabricClientCommandSource source = ctx.getSource();
        Account account = SilentAuth.accounts().byUsername(username.trim());
        if (account == null) {
            reply(source, "No stored account called " + username, Formatting.RED);
            return 0;
        }
        reply(source, "Switching to " + account.getUsername(), Formatting.GRAY);
        LoginService.loginAsync(account, (success, message) ->
                reply(source, message, success ? Formatting.GREEN : Formatting.RED));
        return 1;
    }

    private static int restore(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        if (!SessionSwapper.hasOriginal()) {
            reply(source, "The original session was not captured", Formatting.RED);
            return 0;
        }
        SessionSwapper.restoreOriginal();
        SilentAuth.accounts().setActive(null);
        reply(source, "Back on " + SessionSwapper.originalUsername(), Formatting.GREEN);
        return 1;
    }

    private static int join(CommandContext<FabricClientCommandSource> ctx, String address) {
        final FabricClientCommandSource source = ctx.getSource();
        final MinecraftClient client = MinecraftClient.getInstance();
        reply(source, "Connecting to " + address
                + (SilentAuth.proxies().getDefault() == null ? "" : " through the proxy"), Formatting.GRAY);
        client.execute(() -> {
            ServerInfo info = new ServerInfo("SilentAuth", address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(address), info, false, null);
        });
        return 1;
    }

    private static int proxyList(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        java.util.List<ProxyEntry> entries = SilentAuth.proxies().all();
        if (entries.isEmpty()) {
            reply(source, "No proxies stored yet", Formatting.GRAY);
            return 1;
        }
        for (ProxyEntry entry : entries) {
            reply(source, "- " + entry.describe() + "  " + entry.statusText()
                            + (SilentAuth.proxies().isDefault(entry) ? "  (default)" : ""),
                    SilentAuth.proxies().isDefault(entry) ? Formatting.GREEN : Formatting.WHITE);
        }
        return 1;
    }

    private static int proxyOff(CommandContext<FabricClientCommandSource> ctx) {
        SilentAuth.proxies().setDefault(null);
        LoginService.applyCurrentProxy();
        reply(ctx.getSource(), "Default proxy cleared", Formatting.GRAY);
        return 1;
    }

    private static int proxyAdd(CommandContext<FabricClientCommandSource> ctx, String raw) {
        FabricClientCommandSource source = ctx.getSource();
        try {
            ProxyEntry stored = SilentAuth.proxies()
                    .add(ProxyParser.parse(raw.trim(), SilentAuth.config().getDefaultProxyType()));
            SilentAuth.tester().testAsync(stored);
            reply(source, "Added " + stored.describe(), Formatting.GREEN);
            return 1;
        } catch (IllegalArgumentException e) {
            reply(source, e.getMessage(), Formatting.RED);
            return 0;
        }
    }

    private static int proxyDefault(CommandContext<FabricClientCommandSource> ctx, String raw) {
        FabricClientCommandSource source = ctx.getSource();
        ProxyEntry match = findProxy(raw.trim());
        if (match == null) {
            reply(source, "No stored proxy matches " + raw, Formatting.RED);
            return 0;
        }
        SilentAuth.proxies().setDefault(match);
        LoginService.applyCurrentProxy();
        reply(source, "Default proxy is now " + match.describe(), Formatting.GREEN);
        return 1;
    }

    private static int proxyCheck(CommandContext<FabricClientCommandSource> ctx) {
        final FabricClientCommandSource source = ctx.getSource();
        final ProxyEntry proxy = SilentAuth.proxies().getDefault();
        if (proxy == null) {
            reply(source, "No proxy is in use. Pick one on the Proxies screen first", Formatting.RED);
            return 0;
        }
        reply(source, "Checking what " + proxy.describe() + " looks like from outside...", Formatting.GRAY);
        Async.run(() -> {
            String direct;
            try {
                direct = IpCheck.exitIp(null);
            } catch (IOException e) {
                direct = "unknown";
            }
            String through;
            try {
                through = IpCheck.exitIp(proxy);
            } catch (IOException e) {
                reply(source, "The proxy did not answer: " + e.getMessage() + " - it is dead", Formatting.RED);
                return;
            }
            reply(source, "Your real IP: " + direct, Formatting.GRAY);
            reply(source, "Through the proxy: " + through, Formatting.GRAY);
            if (through.equals(direct)) {
                reply(source, "They match, the proxy is NOT changing your IP. Try another", Formatting.RED);
            } else {
                reply(source, "The proxy changes your IP. If a server still blocks you, that IP is flagged - try another",
                        Formatting.GREEN);
            }
        });
        return 1;
    }

    // ------------------------------------------------------------------ helpers

    private static ProxyEntry findProxy(String raw) {
        for (ProxyEntry entry : SilentAuth.proxies().all()) {
            if ((entry.getHost() + ":" + entry.getPort()).equalsIgnoreCase(raw)) {
                return entry;
            }
        }
        for (ProxyEntry entry : SilentAuth.proxies().all()) {
            if (entry.describe().contains(raw) || entry.getLabel().equalsIgnoreCase(raw)) {
                return entry;
            }
        }
        return null;
    }

    private static void reply(FabricClientCommandSource source, String message, Formatting colour) {
        source.sendFeedback(Text.literal(message).formatted(colour));
    }
}
