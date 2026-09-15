package dev.silentauth.command;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.Account;
import dev.silentauth.account.LoginService;
import dev.silentauth.account.SessionSwapper;
import dev.silentauth.gui.GuiAccountManager;
import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.proxy.ProxyParser;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CommandSilentAuth extends CommandBase {

    @Override
    public String getCommandName() {
        return "silentauth";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("sa");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/sa <gui|list|login|status|proxy>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(final ICommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    Minecraft mc = Minecraft.getMinecraft();
                    mc.displayGuiScreen(new GuiAccountManager(mc.currentScreen));
                }
            });
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("list")) {
            list(sender);
        } else if (sub.equals("status")) {
            status(sender);
        } else if (sub.equals("login")) {
            login(sender, args);
        } else if (sub.equals("proxy")) {
            proxy(sender, args);
        } else {
            reply(sender, "\u00a7cUnknown option, use " + getCommandUsage(sender));
        }
    }

    private void list(ICommandSender sender) {
        List<Account> accounts = SilentAuth.accounts().all();
        if (accounts.isEmpty()) {
            reply(sender, "\u00a77No accounts stored yet");
            return;
        }
        reply(sender, "\u00a77" + accounts.size() + " accounts:");
        for (Account account : accounts) {
            ProxyEntry proxy = LoginService.resolveProxy(account);
            reply(sender, "\u00a78- \u00a7f" + account.getUsername() + " \u00a77" + account.getType().getLabel()
                    + (proxy == null ? "" : " \u00a78via \u00a77" + proxy.describe()));
        }
    }

    private void status(ICommandSender sender) {
        Account active = SilentAuth.accounts().getActive();
        reply(sender, "\u00a77Session: \u00a7f" + SessionSwapper.currentUsername());
        ProxyEntry proxy = LoginService.resolveProxy(active);
        reply(sender, "\u00a77Proxy: \u00a7f" + (proxy == null ? "none" : proxy.describe() + " " + proxy.statusText()));
    }

    private void login(final ICommandSender sender, String[] args) {
        if (args.length < 2) {
            reply(sender, "\u00a7cUsage: /sa login <username>");
            return;
        }
        Account account = SilentAuth.accounts().byUsername(args[1]);
        if (account == null) {
            reply(sender, "\u00a7cNo stored account called " + args[1]);
            return;
        }
        reply(sender, "\u00a77Switching to " + account.getUsername());
        LoginService.loginAsync(account, new LoginService.Callback() {
            @Override
            public void onResult(boolean success, String message) {
                reply(sender, (success ? "\u00a7a" : "\u00a7c") + message);
            }
        });
    }

    private void proxy(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            reply(sender, "\u00a7cUsage: /sa proxy <list|add|default|off>");
            return;
        }
        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            List<ProxyEntry> entries = SilentAuth.proxies().all();
            if (entries.isEmpty()) {
                reply(sender, "\u00a77No proxies stored yet");
                return;
            }
            for (ProxyEntry entry : entries) {
                reply(sender, "\u00a78- \u00a7f" + entry.describe() + " \u00a77" + entry.statusText()
                        + (SilentAuth.proxies().isDefault(entry) ? " \u00a7a(default)" : ""));
            }
        } else if (action.equals("add")) {
            if (args.length < 3) {
                reply(sender, "\u00a7cUsage: /sa proxy add <host:port|scheme://user:pass@host:port>");
                return;
            }
            try {
                ProxyEntry entry = ProxyParser.parse(args[2], SilentAuth.config().getDefaultProxyType());
                SilentAuth.proxies().add(entry);
                SilentAuth.tester().testAsync(entry, null);
                reply(sender, "\u00a7aAdded " + entry.describe());
            } catch (IllegalArgumentException e) {
                reply(sender, "\u00a7c" + e.getMessage());
            }
        } else if (action.equals("default")) {
            if (args.length < 3) {
                reply(sender, "\u00a7cUsage: /sa proxy default <host:port>");
                return;
            }
            ProxyEntry match = findByHost(args[2]);
            if (match == null) {
                reply(sender, "\u00a7cNo stored proxy matches " + args[2]);
                return;
            }
            SilentAuth.proxies().setDefault(match);
            LoginService.applyCurrentProxy();
            reply(sender, "\u00a7aDefault proxy is now " + match.describe());
        } else if (action.equals("off")) {
            SilentAuth.proxies().setDefault(null);
            LoginService.applyCurrentProxy();
            reply(sender, "\u00a77Proxy disabled");
        } else {
            reply(sender, "\u00a7cUsage: /sa proxy <list|add|default|off>");
        }
    }

    private ProxyEntry findByHost(String raw) {
        List<ProxyEntry> matches = new ArrayList<ProxyEntry>();
        for (ProxyEntry entry : SilentAuth.proxies().all()) {
            if (entry.describe().contains(raw) || (entry.getHost() + ":" + entry.getPort()).equals(raw)) {
                matches.add(entry);
            }
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }
}
