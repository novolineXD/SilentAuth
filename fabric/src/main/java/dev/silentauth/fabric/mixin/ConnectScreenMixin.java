package dev.silentauth.fabric.mixin;

import dev.silentauth.fabric.ProxiedJoin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every server join goes through {@link ConnectScreen#connect}, so intercepting it here reroutes
 * the game connection through the active proxy for the Join button, direct connect, double click
 * and quick play alike. Server-list pings do not call this method, so they are untouched.
 */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

    @Inject(
        method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;"
            + "Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;Z"
            + "Lnet/minecraft/client/network/CookieStorage;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void silentauth$route(Screen screen, MinecraftClient client, ServerAddress address,
                                         ServerInfo info, boolean quickPlay, CookieStorage cookieStorage,
                                         CallbackInfo ci) {
        if (ProxiedJoin.reroute(screen, client, address, info, quickPlay, cookieStorage)) {
            ci.cancel();
        }
    }
}
