package dev.silentauth.fabric;

import dev.silentauth.SilentAuth;
import dev.silentauth.account.SessionSwapper;
import dev.silentauth.gui.AccountScreen;
import dev.silentauth.util.Log;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.File;

/**
 * The Fabric client entry point. Sets the managers up, registers the open key and the {@code /sa}
 * command, and polls the key every tick so the screen opens from anywhere.
 */
public final class SilentAuthClient implements ClientModInitializer {

    private KeyBinding openKey;
    private boolean captured;
    private boolean restored;

    @Override
    public void onInitializeClient() {
        File directory = FabricLoader.getInstance().getConfigDir().resolve(SilentAuth.MOD_ID).toFile();
        SilentAuth.init(directory);

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.silentauth.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, access) -> SilentAuthCommand.register(dispatcher));

        Log.info("SilentAuth ready with " + SilentAuth.accounts().size() + " accounts and "
                + SilentAuth.proxies().size() + " proxies");
    }

    private void onEndTick(MinecraftClient client) {
        if (!captured && client.getSession() != null) {
            captured = true;
            SessionSwapper.captureOriginal();
        }
        if (!restored && client.currentScreen instanceof TitleScreen) {
            restored = true;
            SilentAuth.restoreLastAccount();
        }
        while (openKey.wasPressed()) {
            if (!isOurScreen(client.currentScreen)) {
                client.setScreen(new AccountScreen(client.currentScreen));
            }
        }
    }

    /** True for any of the mod's own screens, so the key does not reopen on top of one. */
    private static boolean isOurScreen(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("dev.silentauth.gui.");
    }
}
