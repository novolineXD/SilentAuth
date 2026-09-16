package dev.silentauth.event;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.LoginService;
import dev.silentauth.gui.GuiAccountManager;
import dev.silentauth.gui.SilentAuthScreen;
import dev.silentauth.net.ProxiedConnect;
import dev.silentauth.util.Reflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenServerList;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Adds the button to the menu screens and owns the open key.
 *
 * <p>The key is polled every client tick rather than only through the in-game key event, so it
 * opens the screen from anywhere - including clients like Lunar that replace the vanilla menus
 * with their own screens, where neither the injected button nor the in-game-only key event
 * would fire. The screen itself is an ordinary {@code GuiScreen}, so it renders normally on
 * those clients.</p>
 */
public final class MainMenuHandler {

    /** Well outside the id range any vanilla screen uses. */
    private static final int BUTTON_ID = 0x5A17;

    private final KeyBinding openKey = new KeyBinding("Open SilentAuth", Keyboard.KEY_RCONTROL, "SilentAuth");
    private boolean keyWasDown;
    private boolean restored;

    public MainMenuHandler() {
        ClientRegistry.registerKeyBinding(openKey);
    }

    /**
     * True for a screen the button belongs on. Matches the vanilla menus, and - for clients
     * that swap them out - any screen whose class name reads like a menu, so the button appears
     * wherever the host actually renders it.
     */
    private static boolean isMenuScreen(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof GuiMainMenu || screen instanceof GuiMultiplayer) {
            return true;
        }
        String name = screen.getClass().getName().toLowerCase();
        return name.contains("mainmenu") || name.contains("titlescreen")
                || name.contains("multiplayer") || name.contains("serverselect")
                || name.contains("serverlist");
    }

    private static boolean isOurScreen(GuiScreen screen) {
        return screen instanceof SilentAuthScreen;
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!isMenuScreen(event.gui)) {
            return;
        }
        if (event.gui instanceof GuiMainMenu && !restored) {
            restored = true;
            SilentAuth.restoreLastAccount();
        }
        if (SilentAuth.config().isShowMainMenuButton()) {
            event.buttonList.add(new GuiButton(BUTTON_ID, 5, 5, 90, 20, "SilentAuth"));
        }
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.button == null || event.button.id != BUTTON_ID || !isMenuScreen(event.gui)) {
            return;
        }
        open(event.gui);
        event.setCanceled(true);
    }

    /**
     * Polls the open key. Runs every client tick regardless of the current screen, so it works
     * on hosts that replace the menus. Opens on the key's rising edge, unless a SilentAuth
     * screen is already open or a text field is focused.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int code = openKey.getKeyCode();
        boolean down = code != 0 && Keyboard.isCreated() && Keyboard.isKeyDown(code);
        boolean pressed = down && !keyWasDown;
        keyWasDown = down;
        if (!pressed) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (isOurScreen(mc.currentScreen) || isTyping(mc.currentScreen)) {
            return;
        }
        open(mc.currentScreen);
    }

    /** Avoids stealing the key while the user is typing in a field on the current screen. */
    private static boolean isTyping(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        try {
            for (java.lang.reflect.Field field : screen.getClass().getDeclaredFields()) {
                if (GuiTextField.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (value instanceof GuiTextField && ((GuiTextField) value).isFocused()) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Best effort; if the fields cannot be read, assume the user is not typing.
        }
        return false;
    }

    private static void open(GuiScreen parent) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiAccountManager(parent));
    }

    // ------------------------------------------------------------------ proxy join interception

    @SubscribeEvent
    public void onJoinButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiMultiplayer) || event.button == null || event.button.id != 1) {
            return;
        }
        if (!SilentAuth.config().isRouteGameThroughProxy()) {
            return;
        }
        if (LoginService.resolveProxy(SilentAuth.accounts().getActive()) == null) {
            return;
        }
        String ip = ProxiedConnect.selectedServerIp((GuiMultiplayer) event.gui);
        if (ip == null) {
            dev.silentauth.util.Log.warn("Could not read the selected server; this join may not use the proxy");
            return;
        }
        event.setCanceled(true);
        ProxiedConnect.connect(event.gui, ip);
    }

    @SubscribeEvent
    public void onDirectConnect(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiScreenServerList) || event.button == null || event.button.id != 0) {
            return;
        }
        if (!SilentAuth.config().isRouteGameThroughProxy()) {
            return;
        }
        if (LoginService.resolveProxy(SilentAuth.accounts().getActive()) == null) {
            return;
        }
        GuiScreenServerList gui = (GuiScreenServerList) event.gui;
        GuiTextField field = Reflect.get(gui, GuiScreenServerList.class, GuiTextField.class, "field_146302_g");
        String ip = field.getText().trim();
        if (ip.isEmpty()) {
            return;
        }
        GuiScreen parent = Reflect.get(gui, GuiScreenServerList.class, GuiScreen.class, "field_146303_a");
        event.setCanceled(true);
        ProxiedConnect.connect(parent, ip);
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!(event.gui instanceof GuiConnecting)) {
            return;
        }
        GuiScreen replacement = ProxiedConnect.maybeReroute((GuiConnecting) event.gui);
        if (replacement != null) {
            event.gui = replacement;
        }
    }
}
