package dev.silentauth.event;

import dev.silentauth.SilentAuth;
import dev.silentauth.gui.GuiAccountManager;
import dev.silentauth.net.ProxiedConnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/** Adds the button to the title and multiplayer screens, and owns the open keybind. */
public final class MainMenuHandler {

    /** Well outside the id range any vanilla screen uses. */
    private static final int BUTTON_ID = 0x5A17;

    private final KeyBinding openKey = new KeyBinding("Open SilentAuth", Keyboard.KEY_RSHIFT, "SilentAuth");
    private boolean restored;

    public MainMenuHandler() {
        ClientRegistry.registerKeyBinding(openKey);
    }

    private static boolean isHostScreen(GuiScreen screen) {
        return screen instanceof GuiMainMenu || screen instanceof GuiMultiplayer;
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!isHostScreen(event.gui)) {
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
        if (event.button == null || event.button.id != BUTTON_ID || !isHostScreen(event.gui)) {
            return;
        }
        open(event.gui);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openKey.isPressed()) {
            open(null);
        }
    }

    /**
     * Every join - server list, double click, direct connect or LAN - opens a
     * {@link GuiConnecting}. This is the one place all of them pass through, so the game
     * connection is rerouted through the proxy here.
     */
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

    private static void open(GuiScreen parent) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiAccountManager(parent));
    }
}
