package io.github.hashim1610.happyghastupgrades;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * config/happyghastupgrades/settings.json. Written with defaults on first start.
 * Every value that ends up inside a vanilla command is validated first, so a typo in the file can never inject a command.
 */
public final class Settings {
    /** One kind of upgrade (speed, health): which off-hand item pays for it and what it changes on the ghast. */
    public static final class Upgrade {
        /** Off-hand item that selects this upgrade, e.g. "minecraft:golden_apple". */
        public String item;
        /** Attribute that gets the modifier, e.g. "minecraft:flying_speed". */
        public String attribute;
        /** add_value, add_multiplied_base or add_multiplied_total (the vanilla /attribute operations). */
        public String operation;
        /** Amount added per level. */
        public double per_level;
        /** Level cap. */
        public int max_levels;

        public Upgrade() {}

        Upgrade(String item, String attribute, String operation, double perLevel, int maxLevels) {
            this.item = item;
            this.attribute = attribute;
            this.operation = operation;
            this.per_level = perLevel;
            this.max_levels = maxLevels;
        }
    }

    public Upgrade speed = new Upgrade("minecraft:golden_apple", "minecraft:flying_speed", "add_multiplied_base", 0.15, 4);
    public Upgrade health = new Upgrade("minecraft:totem_of_undying", "minecraft:max_health", "add_value", 4.0, 5);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern ID = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");
    private static final Set<String> OPERATIONS = Set.of("add_value", "add_multiplied_base", "add_multiplied_total");

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("happyghastupgrades").resolve("settings.json");
    }

    public static Settings load() {
        Settings defaults = new Settings();
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            if (!Files.exists(f)) {
                try (Writer w = Files.newBufferedWriter(f)) { GSON.toJson(defaults, w); }
                return defaults;
            }
            Settings s;
            try (Reader r = Files.newBufferedReader(f)) { s = GSON.fromJson(r, Settings.class); }
            if (s == null) throw new IllegalStateException("settings.json is empty");
            s.speed = clean("speed", s.speed, defaults.speed);
            s.health = clean("health", s.health, defaults.health);
            return s;
        } catch (Exception e) {
            HappyGhastUpgrades.LOGGER.warn("Could not read {} ({}), using the built-in defaults", f, e.getMessage());
            return defaults;
        }
    }

    /** Falls back to the default for any single bad value instead of rejecting the whole file. */
    private static Upgrade clean(String name, Upgrade u, Upgrade def) {
        if (u == null) return def;
        if (!validId(u.item) || !itemExists(u.item)) { warn(name, "item", u.item, def.item); u.item = def.item; }
        if (!validId(u.attribute)) { warn(name, "attribute", u.attribute, def.attribute); u.attribute = def.attribute; }
        if (u.operation == null || !OPERATIONS.contains(u.operation)) { warn(name, "operation", u.operation, def.operation); u.operation = def.operation; }
        if (!(u.per_level > 0) || Double.isInfinite(u.per_level)) { warn(name, "per_level", String.valueOf(u.per_level), String.valueOf(def.per_level)); u.per_level = def.per_level; }
        if (u.max_levels < 1 || u.max_levels > 100) { warn(name, "max_levels", String.valueOf(u.max_levels), String.valueOf(def.max_levels)); u.max_levels = def.max_levels; }
        return u;
    }

    private static boolean validId(String s) {
        return s != null && ID.matcher(s).matches();
    }

    private static boolean itemExists(String id) {
        try { return BuiltInRegistries.ITEM.containsKey(Identifier.parse(id)); }
        catch (Exception e) { return false; }
    }

    private static void warn(String name, String key, String bad, String def) {
        HappyGhastUpgrades.LOGGER.warn("settings.json: {}.{} = {} is not valid, using {}", name, key, bad, def);
    }
}
