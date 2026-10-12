package io.github.hashim1610.happyghastupgrades;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Right-click a happy ghast with a Ghast Core in the main hand and a payment item in the off-hand.
 * The upgrade is a vanilla attribute modifier on the ghast (saved with the entity), plus a tag that remembers the level.
 * Everything is done through vanilla commands, so there is nothing for a client to install.
 */
public final class Upgrades {
    private Upgrades() {}

    static final String HAPPY_GHAST = "minecraft:happy_ghast";
    /** The Ghast Core is this vanilla item (recipe: data/happyghastupgrades/recipe/ghast_core.json) with a custom_data flag. */
    static final String CORE_BASE_ITEM = "minecraft:disc_fragment_5";
    static final String CORE_FLAG = "hgu_core";

    static boolean isHappyGhast(Entity e) {
        return e != null && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(HAPPY_GHAST);
    }

    static boolean isItem(ItemStack st, String id) {
        return st != null && !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(id);
    }

    static boolean isCore(ItemStack st) {
        if (!isItem(st, CORE_BASE_ITEM)) return false;
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().contains(CORE_FLAG);
    }

    /** Current level of an upgrade kind ("speed" / "health"), read from the ghast's tags. */
    static int level(Entity ghast, String kind) {
        String prefix = "hgu_" + kind + "_";
        int best = 0;
        for (String t : ghast.entityTags()) {
            if (!t.startsWith(prefix)) continue;
            try { best = Math.max(best, Integer.parseInt(t.substring(prefix.length()))); }
            catch (NumberFormatException ignored) {}
        }
        return best;
    }

    /** Result of trying to apply one level. */
    record Result(boolean ok, int level, String error) {}

    /** Applies the next level of an upgrade to a ghast. Does not touch any items. */
    static Result apply(MinecraftServer server, ServerLevel l, Entity ghast, String kind, Settings.Upgrade u) {
        int cur = level(ghast, kind);
        if (cur >= u.max_levels) return new Result(false, cur, "max");
        int next = cur + 1;
        String uuid = ghast.getStringUUID();
        String modifier = HappyGhastUpgrades.MOD_ID + ":" + kind;
        CommandSourceStack src = server.createCommandSourceStack().withLevel(l).withPosition(Vec3.ZERO).withSuppressedOutput();
        var dispatcher = server.getCommands().getDispatcher();
        // replace the old modifier of this kind with one carrying the new total
        try { dispatcher.execute("attribute " + uuid + " " + u.attribute + " modifier remove " + modifier, src); }
        catch (Exception ignored) { /* nothing to remove on the first level */ }
        try {
            dispatcher.execute("attribute " + uuid + " " + u.attribute + " modifier add " + modifier + " "
                    + number(u.per_level * next) + " " + u.operation, src);
        } catch (Exception e) {
            HappyGhastUpgrades.LOGGER.warn("Could not apply {} upgrade (attribute {}): {}", kind, u.attribute, e.getMessage());
            // put the previous level back so a failed attempt never costs the ghast what it had
            if (cur > 0) {
                try { dispatcher.execute("attribute " + uuid + " " + u.attribute + " modifier add " + modifier + " "
                        + number(u.per_level * cur) + " " + u.operation, src); }
                catch (Exception ignored) {}
            }
            return new Result(false, cur, "attribute");
        }
        if (cur > 0) run(dispatcher, src, "tag " + uuid + " remove hgu_" + kind + "_" + cur);
        run(dispatcher, src, "tag " + uuid + " add hgu_" + kind + "_" + next);
        if (kind.equals("health")) run(dispatcher, src, "effect give " + uuid + " minecraft:instant_health 1 3 true");
        String at = number(ghast.getX()) + " " + number(ghast.getY() + 1.5) + " " + number(ghast.getZ());
        run(dispatcher, src, "particle minecraft:happy_villager " + at + " 1.2 0.8 1.2 0 40");
        run(dispatcher, src, "playsound minecraft:entity.player.levelup neutral @a " + at + " 1 1.2");
        return new Result(true, next, null);
    }

    private static void run(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d, CommandSourceStack src, String cmd) {
        try { d.execute(cmd, src); }
        catch (Exception e) { HappyGhastUpgrades.LOGGER.debug("Command had no effect: {}", cmd); }
    }

    static String number(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    /** "minecraft:golden_apple" -> "golden apple" */
    static String pretty(String id) {
        return id.substring(id.indexOf(':') + 1).replace('_', ' ');
    }

    /** UseEntityCallback: Ghast Core in main hand + payment item in off-hand, right-click a happy ghast. */
    public static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity entity) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!isHappyGhast(entity)) return InteractionResult.PASS;
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!isCore(main)) return InteractionResult.PASS;   // normal interaction (riding, feeding) is untouched

        MinecraftServer server = HappyGhastUpgrades.server;
        Settings s = HappyGhastUpgrades.settings;
        if (server == null || s == null) return InteractionResult.PASS;

        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        String kind;
        Settings.Upgrade u;
        if (isItem(off, s.speed.item)) { kind = "speed"; u = s.speed; }
        else if (isItem(off, s.health.item)) { kind = "health"; u = s.health; }
        else {
            note(sp, "Ghast Core: hold " + pretty(s.speed.item) + " (speed) or " + pretty(s.health.item) + " (health) in your off-hand", ChatFormatting.AQUA);
            return InteractionResult.SUCCESS;
        }

        Result r = apply(server, sl, entity, kind, u);
        if (!r.ok()) {
            note(sp, "error".equals(r.error()) || "attribute".equals(r.error())
                    ? "The upgrade could not be applied (see the server log)"
                    : "This happy ghast already has the maximum " + kind + " upgrade (level " + r.level() + ")", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (!player.isCreative()) { main.shrink(1); off.shrink(1); }
        note(sp, kind.substring(0, 1).toUpperCase(Locale.ROOT) + kind.substring(1) + " upgrade: level " + r.level() + " of " + u.max_levels, ChatFormatting.GREEN);
        return InteractionResult.SUCCESS;
    }

    private static void note(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color), true);
    }
}
