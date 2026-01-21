package com.znxtech.sharedhealth;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class SharedHealth extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static SharedHealth INSTANCE;

    public SharedHealth(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    @Override
    public void setup() {
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, SharedHealthSystems::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, SharedHealthSystems::onPlayerDisconnect);
        this.getEntityStoreRegistry().registerSystem(new SharedHealthSystems.SharedDamage());
        this.getEntityStoreRegistry().registerSystem(new SharedHealthSystems.HealthChangeSystem());
    }

    public static SharedHealth getInstance() {
        return INSTANCE;
    }
}
