package dev.silentauth.event;

import dev.silentauth.SilentAuth;
import dev.silentauth.gui.GuiAccountManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public final class MainMenuHandler {

    private static final int BUTTON_ID = 0x5A17;

    private final KeyBinding openKey = new KeyBinding("Open SilentAuth", Keyboard.KEY_RSHIFT, "SilentAuth");

    public MainMenuHandler() {
        ClientRegistry.registerKeyBinding(openKey);
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!SilentAuth.config().isShowMainMenuButton()) {
            return;
        }
        if (!(event.gui instanceof GuiMainMenu) && !(event.gui instanceof GuiMultiplayer)) {
            return;
        }
        event.buttonList.add(new GuiButton(BUTTON_ID, 5, 5, 90, 20, "SilentAuth"));
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.button == null || event.button.id != BUTTON_ID) {
            return;
        }
        if (!(event.gui instanceof GuiMainMenu) && !(event.gui instanceof GuiMultiplayer)) {
            return;
        }
        Minecraft.getMinecraft().displayGuiScreen(new GuiAccountManager(event.gui));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!openKey.isPressed()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(new GuiAccountManager(null));
    }
}
