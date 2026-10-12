package io.github.hashim1610.happyghastupgrades;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HappyGhastUpgrades implements ModInitializer {
    public static final String MOD_ID = "happyghastupgrades";
    public static final Logger LOGGER = LoggerFactory.getLogger("Happy Ghast Upgrades");

    public static volatile MinecraftServer server;
    public static volatile Settings settings;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            settings = Settings.load();
            LOGGER.info("Happy Ghast Upgrades ready: speed = {} (max {}), health = {} (max {})",
                    settings.speed.item, settings.speed.max_levels, settings.health.item, settings.health.max_levels);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> server = null);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> Upgrades.onUse(player, level, hand, entity));
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> Commands.register(dispatcher));
        LOGGER.info("Happy Ghast Upgrades loaded");
    }
}
