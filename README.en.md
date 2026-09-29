# Crafty Cards · forge / Minecraft 1.21.11

[简体中文（默认）](README.md) | English

This branch is the independent source snapshot for **forge on Minecraft 1.21.11**. It includes shared game code and the overlays needed for this target. Older overlay directory names are build inputs; they do not produce JARs for those Minecraft versions.

Crafty Cards brings three-player multiplayer Dou Dizhu into Minecraft. See [CREDITS.md](CREDITS.md) for provenance, artwork, and licensing notes.

## Fixed target and build

| Item | Value |
| --- | --- |
| Minecraft | 1.21.11 |
| Loader dependency | Forge 1.21.11-61.2.1 |
| Java | 21 |
| Installable JAR | `forge/build/mc-1.21.11/libs/crafty_cards-forge-mc1.21.11-1.0.0.jar` |

After downloading this branch, run from the repository root in Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-target.ps1
```

This builds and tests only **forge / Minecraft 1.21.11** and verifies the release JAR name. A `-sources.jar`, when present, is a source archive for the same target, not an installable mod. Gradle rejects a different `-PmcTarget`. See the [branch version guide](versions/README.md) and [testing scope](docs/open-source/TESTING.md).

## Start a Dou Dizhu game

1. Craft and place a Dou Dizhu table: one green carpet, three planks, and two sticks.

   ```text
    C        C = green carpet
   PPP       P = any planks
   S S       S = sticks
   ```

2. Outside a game, the first player to **sneak + right-click** the table becomes its host. Configure that table's chip item, stake, and entry requirement, or turn chips off for a casual match. The server-wide `chipsRequired=false` setting also disables stakes on every table.
3. Three players each right-click the table to join. When chips are enabled, each player must carry at least the table's entry requirement; the table collects its stake upon joining. The game starts automatically with 17 cards per player and three bottom cards.
4. Joining gives you a **Dou Dizhu card item**. Hold it to show your hand in the HUD. Switching to another item hides the hand without leaving the game.

| Input | Dou Dizhu action |
| --- | --- |
| Right-click the table | Join; confirm a bid on your turn; play selected cards or pass on your turn |
| Sneak + right-click the table | Leave if playing; open table settings if you are the host outside the game; otherwise toggle spectating |
| Mouse wheel while holding the card | Choose no bid / 1 / 2 / 3 during bidding; move hand focus during play (scroll direction is reversed) |
| Left-click while holding the card | Select or deselect the focused card, including before your turn |
| Right-click empty space while holding the card | Clear the current selection |

Spectating is **opt-in**: a nearby non-player sneak-right-clicks the table. Simply walking close does not reveal hands. The server decides whether spectators see faces or only card backs and counts. Leaving during a match, disconnecting, or timing out can trigger a forfeit; leaving before the match starts returns your stake.

## Configuration and stakes

| Path | Owner | Purpose |
| --- | --- | --- |
| `config/crafty_cards/visual.json` | Client | HUD, world hands, played cards, and chip rendering; `/craftycards reload` applies visual changes |
| `config/crafty_cards/sounds.json` | Client | Sound enablement, volume, and selected sound pack; BGM channel and vanilla-music ducking are edited in the file |
| `config/crafty_cards/server.json` | Server | Global rules, permissions, timeouts, chip whitelist, and **defaults for new tables**; restart the server after editing |
| `config/crafty_cards/soundpacks/<pack-id>/` | Client | A sound pack's `pack.json` and Ogg Vorbis files |

Each table stores its own chip item, stake, entry requirement, and chips-on/off setting. Changing `chipStake` or `chipEntryCount` in `server.json` seeds **new tables**; it does not rewrite existing ones. With the default `chipEntryCount=0`, the entry requirement is `stake × 6`. The requirement is a check before joining; the actual amount collected at entry is the table's stake. The host can choose a different requirement, including one too low to cover a large loss.

Important server defaults include `participantsSeeFaces=false`, `spectatorsSeeCards=true`, `spectateRadius=0` (no automatic hand sharing), `joinRadius=8`, `turnTimeoutTicks=600` (30 seconds; `0` disables the timer), and `chipsRequired=true`. A player connected to a dedicated server cannot change its rules by editing the client's own `server.json`. NeoForge exposes the sound-pack page through its Mods configuration entry; the Fabric and Forge POCs do not currently have that button, so edit the client configuration and restart there.

Dou Dizhu scores use the bid as the base score. Each bomb or rocket doubles the multiplier, and spring or reverse spring doubles it again. The result is multiplied by the table's stake; the landlord pays or receives two shares. Staked items retain their components, such as enchantments or custom names. The item pool is used first, then a loser's inventory if necessary; an unpaid remainder is reported rather than silently creating items. Turning chips off makes the game purely recreational.

## Sound packs

The mod jar contains **no `.ogg` files**. Clients may install a separate pack under `config/crafty_cards/soundpacks/<pack-id>/`, scan it on the game's sound-pack page, select it, and **Save and Apply**. The repository includes [three complete 1.21.1 learning packs with audio](learning-soundpacks/README.md). Their origins and public redistribution rights have not been fully verified; see the [audio rights notice](release-materials/AUDIO-RIGHTS.md). If any material infringes your rights, please contact us for removal.

There are 61 supported sound keys: seven effects, six BGM cues, and 48 card or combination voice cues. A key can reference several files with integer weights from 1 to 99. Missing voice cues fall back to a general play/bomb effect and then to vanilla effects; missing BGM cues follow the documented BGM fallback chain. See the [format and complete key list](docs/音乐包格式.md).

To make a pack from your own audio, run the visual maker:

```text
python tools/soundpack_maker.py
```

Add source files and assign keys and weights, then open **Review Configuration**. Adding files only stages the mapping; the program copies or converts audio and writes `pack.json` **only after the player clicks Generate Sound Pack in the review window**. The [Harness prompt](docs/Harness音乐包制作提示词.md) lets an assistant prepare the visual configuration while leaving that final click to the player. The `--cli` path is for scripts and generates directly without this human review step. MP3/WAV/FLAC conversion requires PyAV; existing Ogg Vorbis files can be copied without it.

## Build and verify

Run `powershell -ExecutionPolicy Bypass -File .\build-target.ps1` from the repository root. The only installable output is `forge/build/mc-1.21.11/libs/crafty_cards-forge-mc1.21.11-1.0.0.jar`. Shared sources are in `common/`; loader sources are in `forge/`; required version overlays are in `versions/`.

The [developer guide](docs/open-source/DEVELOPMENT.md) covers this branch's source layout. The [testing scope](docs/open-source/TESTING.md) distinguishes build verification from client and audio checks.

## Artwork, credits, and license

Some card faces, backs, chips, and block textures were inherited from PlayingCards; 69 PNGs in this repository match the local upstream reference byte-for-byte. The current mod icon also combines inherited artwork. Read [CREDITS.md](CREDITS.md) and the [asset review](docs/open-source/ASSETS.md) before publishing code, a jar, an icon, or the default sound pack. The repository's [GPL-3.0-only license](LICENSE) does not by itself resolve upstream artwork or audio redistribution rights.

The [publication materials](docs/open-source/README.md) are still a local review draft. Nothing has been uploaded to GitHub yet.
