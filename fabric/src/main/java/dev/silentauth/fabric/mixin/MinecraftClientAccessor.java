package dev.silentauth.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.util.ApiServices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.net.Proxy;

/**
 * Opens up the private final session, proxy and api-service fields on {@link MinecraftClient} so
 * the session swap can replace them without reflection - the Fabric counterpart of the reflective
 * field lookup the 1.8.9 build uses.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("session")
    @Mutable
    void silentauth$setSession(Session session);

    @Accessor("networkProxy")
    @Mutable
    void silentauth$setNetworkProxy(Proxy proxy);

    @Accessor("apiServices")
    @Mutable
    void silentauth$setApiServices(ApiServices apiServices);

    @Accessor("apiServices")
    ApiServices silentauth$getApiServices();
}
