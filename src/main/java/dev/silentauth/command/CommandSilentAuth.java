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
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CommandSilentAuth extends CommandBase {

    private static final String[] SUBCOMMANDS = { "gui", "list", "login", "restore", "status", "proxy" };
    private static final String[] PROXY_ACTIONS = { "list", "add", "default", "off" };

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
        return "/sa <gui|list|login|restore|status|proxy>";
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
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("login")) {
            return getListOfStringsMatchingLastWord(args, usernames());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("proxy")) {
            return getListOfStringsMatchingLastWord(args, PROXY_ACTIONS);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("proxy") && args[1].equalsIgnoreCase("default")) {
            return getListOfStringsMatchingLastWord(args, endpoints());
        }
        return null;
    }

    private static List<String> usernames() {
        List<String> names = new ArrayList<String>();
        for (Account account : SilentAuth.accounts().all()) {
            names.add(account.getUsername());
        }
        return names;
    }

    private static List<String> endpoints() {
        List<String> hosts = new ArrayList<String>();
        for (ProxyEntry entry : SilentAuth.proxies().all()) {
            hosts.add(entry.getHost() + ":" + entry.getPort());
        }
        return hosts;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "gui" : args[0].toLowerCase();
        if (sub.equals("gui")) {
            openGui();
        } else if (sub.equals("list")) {
            list(sender);
        } else if (sub.equals("status")) {
            status(sender);
        } else if (sub.equals("login")) {
            login(sender, args);
        } else if (sub.equals("restore")) {
            restore(sender);
        } else if (sub.equals("proxy")) {
            proxy(sender, args);
        } else {
            reply(sender, "\u00a7cUnknown option, use " + getCommandUsage(sender));
        }
    }

    /** The command runs while the chat screen is still up, so the swap waits for the next tick. */
    private void openGui() {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                mc.displayGuiScreen(new GuiAccountManager(mc.currentScreen));
            }
        });
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
            reply(sender, "\u00a78- " + (SilentAuth.accounts().isActive(account) ? "\u00a7a" : "\u00a7f")
                    + account.getUsername() + " \u00a77" + account.getType().getLabel()
                    + (proxy == null ? "" : " \u00a78via \u00a77" + proxy.describe()));
        }
    }

    private void status(ICommandSender sender) {
        reply(sender, "\u00a77Session: \u00a7f" + SessionSwapper.currentUsername());
        ProxyEntry proxy = LoginService.resolveProxy(SilentAuth.accounts().getActive());
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

    private void restore(ICommandSender sender) {
        if (!SessionSwapper.hasOriginal()) {
            reply(sender, "\u00a7cThe original session was not captured");
            return;
        }
        SessionSwapper.restoreOriginal();
        SilentAuth.accounts().setActive(null);
        reply(sender, "\u00a7aBack on " + SessionSwapper.originalUsername());
    }

    private void proxy(ICommandSender sender, String[] args) {
        String action = args.length < 2 ? "" : args[1].toLowerCase();
        if (action.equals("list")) {
            proxyList(sender);
        } else if (action.equals("add")) {
            proxyAdd(sender, args);
        } else if (action.equals("default")) {
            proxyDefault(sender, args);
        } else if (action.equals("off")) {
            SilentAuth.proxies().setDefault(null);
            LoginService.applyCurrentProxy();
            reply(sender, "\u00a77Default proxy cleared");
        } else {
            reply(sender, "\u00a7cUsage: /sa proxy <list|add|default|off>");
        }
    }

    private void proxyList(ICommandSender sender) {
        List<ProxyEntry> entries = SilentAuth.proxies().all();
        if (entries.isEmpty()) {
            reply(sender, "\u00a77No proxies stored yet");
            return;
        }
        for (ProxyEntry entry : entries) {
            reply(sender, "\u00a78- \u00a7f" + entry.describe() + " \u00a77" + entry.statusText()
                    + (SilentAuth.proxies().isDefault(entry) ? " \u00a7a(default)" : ""));
        }
    }

    private void proxyAdd(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            reply(sender, "\u00a7cUsage: /sa proxy add <host:port|scheme://user:pass@host:port>");
            return;
        }
        try {
            ProxyEntry stored = SilentAuth.proxies()
                    .add(ProxyParser.parse(args[2], SilentAuth.config().getDefaultProxyType()));
            SilentAuth.tester().testAsync(stored);
            reply(sender, "\u00a7aAdded " + stored.describe());
        } catch (IllegalArgumentException e) {
            reply(sender, "\u00a7c" + e.getMessage());
        }
    }

    private void proxyDefault(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            reply(sender, "\u00a7cUsage: /sa proxy default <host:port>");
            return;
        }
        ProxyEntry match = findProxy(args[2]);
        if (match == null) {
            reply(sender, "\u00a7cNo stored proxy matches " + args[2]);
            return;
        }
        SilentAuth.proxies().setDefault(match);
        LoginService.applyCurrentProxy();
        reply(sender, "\u00a7aDefault proxy is now " + match.describe());
    }

    /** Matches host:port exactly first, then falls back to any entry containing the text. */
    private ProxyEntry findProxy(String raw) {
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

    private static void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }
}
