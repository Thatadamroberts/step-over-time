# Replacing the placeholder art

Every sprite is generated in code by `StepQuest/Assets/_Game/Unity/Art/PixelArt.cs`. Before it draws anything, it looks for **`Assets/Resources/Art/<key>.png`**. If that file exists, it's used instead. Import settings are applied automatically (point filtering, no compression, no mipmaps) by `PixelArtImporter`.

Sizes are in pixels, at 16 pixels per world unit. Other sizes work, since sprites scale with their texture.

| Key | What | Size | Facing |
|---|---|---|---|
| `hero_warrior`, `hero_rogue`, `hero_mage`, `hero_druid`, `hero_priest` | Party members | 32×40 | **left** (party stands on the right) |
| `enemy_<id>` e.g. `enemy_slime`, `enemy_wolf`, `enemy_golem` | Normal enemies | 32×32 | **right** |
| `enemy_boss_boar`, `enemy_boss_treant`, `enemy_boss_wyrm`, `enemy_boss_kraken`, `enemy_boss_dragon`, `enemy_boss_tyrant` | Bosses | 64×64 | right |
| `prop_<kind>_<region>` e.g. `prop_tree_forest`, `prop_cactus_desert` | Scenery | any | — |
| `icon_<name>` e.g. `icon_sword`, `icon_flame`, `icon_star` | UI icons | 16×16 | — |

Enemy ids: `slime rat wasp wolf sprite mushroom scorpion mummy djinn crab siren pirate yeti harpy golem gargoyle wraith sentinel`.
Prop kinds: `tree rock grass pillar cactus crystal`. Regions: `meadow forest desert coast peaks ruins`.

Feet should touch the bottom edge of the image. Sprites stand on the ground plane from their bottom edge.

## Where to find CC0 pixel art

CC0 means public domain: no attribution required, commercial use allowed. Still list what you use in `CREDITS.md`.

* **Kenney** (kenney.nl): everything there is CC0. *Tiny Dungeon*, *Tiny Town*, *1-Bit Pack* and *Roguelike Characters* fit well.
* **OpenGameArt.org**: filter the License field to **CC0**. Search for "LPC" for many compatible characters, but note LPC itself is CC-BY-SA/GPL, **not** CC0.
* **itch.io**: browse game assets tagged **cc0**, and check each pack's licence text.

For a consistent HD-2D look, pick one pack (or one artist) for all characters, and keep sprite resolution consistent across heroes and enemies.
