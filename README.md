# Happy Ghast Upgrades

**Permanent upgrades for the happy ghast, with nothing for players to install.**

A small Fabric mod for **Minecraft 26.3** that runs only on the server. Players join with a normal, unmodified client. The item, the recipe and the upgrades are all built from vanilla parts.

> Status: **early** (0.1.0). Written to the same rules as [Boss Altars](https://github.com/hashim-1610/boss-altars).

## How it works

1. **Collect** dried ghasts (crafted from ghast tears, or found in Soul Sand Valley fossils).
2. **Craft a Ghast Core:** 3 dried ghasts, shapeless, in any crafting table.
3. **Upgrade a happy ghast:** hold the Ghast Core in your **main hand** and a payment item in your **off-hand**, then right-click the happy ghast.

| Off-hand item | Upgrade | Per level | Max |
|---|---|---|---|
| Golden apple | Speed (flying speed) | +15% | 4 levels |
| Totem of undying | Health (max health) | +4 HP (2 hearts) | 5 levels |

The core and the payment item are both used up (creative mode keeps them). The upgrade stays on that ghast permanently. If the ghast dies, the upgrades are gone with it.

Holding a Ghast Core with the wrong off-hand item shows a hint instead of doing anything. Without a core in hand, right-clicking a ghast behaves exactly as in vanilla (riding, feeding).

## How it is built (and why clients need nothing)

* The **Ghast Core** is a vanilla `disc_fragment_5` with a custom name, a `custom_data` flag and the `item_model` of a ghast tear, so it looks like a glowing ghast tear on a vanilla client. Its recipe is plain datapack JSON (`data/happyghastupgrades/recipe/ghast_core.json`).
* The upgrade is a vanilla **attribute modifier** (`/attribute ... modifier add`) on the ghast, which Minecraft saves with the entity. A tag (`hgu_speed_N`, `hgu_health_N`) remembers the level.
* All of it runs through vanilla commands, so the mod never touches client code.

## Commands

| Command | Who | What |
|---|---|---|
| `/happyghast info` | everyone | explains the steps |
| `/happyghast core` | operators | gives yourself a Ghast Core |
| `/happyghast apply speed\|health` | operators | applies one level to the nearest happy ghast within 16 blocks, without using items (for testing) |
| `/happyghast reload` | operators | reloads `settings.json` |

## Configuration

`config/happyghastupgrades/settings.json` is created on first start:

```json
{
  "speed":  { "item": "minecraft:golden_apple",    "attribute": "minecraft:flying_speed", "operation": "add_multiplied_base", "per_level": 0.15, "max_levels": 4 },
  "health": { "item": "minecraft:totem_of_undying", "attribute": "minecraft:max_health",   "operation": "add_value",           "per_level": 4.0,  "max_levels": 5 }
}
```

Change the payment item, the amount per level or the level cap. Invalid values fall back to the defaults and are logged. The Ghast Core recipe is a datapack file, so it can be overridden with a normal datapack.

## Requirements

Minecraft 26.3, Fabric Loader 0.19.5+, Java 25, Fabric API. Server-side only.

## Building

CI builds every push (`.github/workflows/build.yml`) and runs `tools/smoke.sh`, which boots a real 26.3 server, spawns a happy ghast, applies upgrades, checks the level cap, and restarts the server to confirm the upgrades persist.

## License

MIT
