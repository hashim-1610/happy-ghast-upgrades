package io.github.hashim1610.happyghastupgrades;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.literal;

/** /happyghast info | core | apply speed|health | reload */
public final class Commands {
    private Commands() {}

    static java.util.function.Predicate<CommandSourceStack> admin() {
        return net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("happyghast");
        root.then(literal("info").executes(c -> info(c)));
        root.then(literal("core").requires(admin()).executes(c -> giveCore(c)));
        root.then(literal("apply").requires(admin())
                .then(literal("speed").executes(c -> applyNearest(c, "speed")))
                .then(literal("health").executes(c -> applyNearest(c, "health"))));
        root.then(literal("reload").requires(admin()).executes(c -> {
            HappyGhastUpgrades.settings = Settings.load();
            return ok(c, "Happy Ghast Upgrades settings reloaded");
        }));
        root.executes(c -> info(c));
        d.register(root);
    }

    private static int ok(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Component.literal(s).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendFailure(Component.literal(s));
        return 0;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        Settings s = HappyGhastUpgrades.settings;
        String[] lines = {
            "Happy Ghast Upgrades",
            "1. Craft a Ghast Core from 3 dried ghasts.",
            "2. Hold the core in your main hand and " + Upgrades.pretty(s.speed.item) + " (speed, max " + s.speed.max_levels
                + ") or " + Upgrades.pretty(s.health.item) + " (health, max " + s.health.max_levels + ") in your off-hand.",
            "3. Right-click a happy ghast. The upgrade is permanent for that ghast."
        };
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            ChatFormatting col = i == 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY;
            c.getSource().sendSuccess(() -> Component.literal(line).withStyle(col), false);
        }
        return 1;
    }

    /** Admin: gives yourself a Ghast Core (same item the recipe produces). */
    private static int giveCore(CommandContext<CommandSourceStack> c) {
        ServerPlayer p;
        try { p = c.getSource().getPlayerOrException(); } catch (Exception ex) { return fail(c, "Run this in game."); }
        String item = Upgrades.CORE_BASE_ITEM
                + "[custom_name={\"text\":\"Ghast Core\",\"color\":\"aqua\",\"italic\":false},"
                + "custom_data={" + Upgrades.CORE_FLAG + ":true},"
                + "item_model=\"minecraft:ghast_tear\",enchantment_glint_override=true]";
        try {
            c.getSource().getServer().getCommands().getDispatcher()
                    .execute("give " + p.getStringUUID() + " " + item, c.getSource().getServer().createCommandSourceStack().withSuppressedOutput());
        } catch (Exception ex) {
            return fail(c, "Could not give the Ghast Core: " + ex.getMessage());
        }
        return ok(c, "You received a Ghast Core");
    }

    /** Admin/testing: applies one level of an upgrade to the nearest happy ghast within 16 blocks, without using any items. */
    private static int applyNearest(CommandContext<CommandSourceStack> c, String kind) {
        Settings s = HappyGhastUpgrades.settings;
        ServerLevel l = c.getSource().getLevel();
        Vec3 p = c.getSource().getPosition();
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : l.getEntitiesOfClass(Entity.class, new AABB(p.x - 16, p.y - 16, p.z - 16, p.x + 16, p.y + 16, p.z + 16))) {
            if (!Upgrades.isHappyGhast(e)) continue;
            double d = e.distanceToSqr(p);
            if (d < bestD) { bestD = d; best = e; }
        }
        if (best == null) return fail(c, "No happy ghast within 16 blocks");
        Settings.Upgrade u = kind.equals("speed") ? s.speed : s.health;
        Upgrades.Result r = Upgrades.apply(c.getSource().getServer(), l, best, kind, u);
        if (!r.ok()) return fail(c, "Not applied (" + r.error() + ", level " + r.level() + ")");
        return ok(c, kind + " upgrade applied: level " + r.level() + " of " + u.max_levels);
    }
}
