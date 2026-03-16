# ⚔️ LEVEL TOOLS

**Don't let your tools break… let them EVOLVE!**

**![](https://i.imgur.com/sClWta7.png)**

 

[![Apoya a Linceros en BisectHosting](https://raw.githubusercontent.com/lenase0077/lenase0077/main/banner.gif)](https://lenase0077.github.io/lenase0077/)

 

### ⚠️ IMPORTANT REQUIREMENT ⚠️

**Please install [MultipleHUD](https://www.curseforge.com/hytale/mods/multiplehud) for the best experience.** This mod is highly recommended for the custom UI and HUD elements to function correctly.

***

## 🚀 NEW in v2.3: Prestige System & UI Overhaul

### 🏆 Prestige System

Reached the level cap on your weapon or tool? **Prestige** it!

Use `/leveltools prestige` while holding your item to convert your levels into **Ability Tokens**:

*   Every **5 levels** (configurable) converts into **1 Ability Token**
*   The item resets to the remaining levels below the threshold
*   **Tokens accumulate** — prestige multiple times to stack up tokens
*   Spend tokens in the **Weapon Mastery** menu to unlock powerful abilities

### 🖥️ UI Overhaul

The **Weapon Mastery** menu now uses the **official Hytale UI system**, giving it a native, polished look that fits seamlessly with the rest of the game:

*   Native panel borders and backgrounds via official Common.ui templates
*   Proper separators and layout containers
*   Hytale-standard buttons with correct hover/press states
*   **Tooltips** on every ability — hover over any ability to read its description

***

## 🪙 Token System (since v2.2)

Earn tokens to unlock powerful abilities permanently on your item!

### ✨ Available Abilities

#### ⚔️ Weapons

| Ability | Cost | Effect |
| :--- | :---: | :--- |
| **Fire Aspect** | 1 | Sets the target on fire, dealing burn damage over time. |
| **Vampirism** | 2 | Restores health equal to a portion of damage dealt on hit. |
| **Poison** | 1 | Poisons the target on hit, dealing damage over time. |
| **Unstoppable** | 3 | Your attacks bypass enemy defenses, dealing increased damage. |
| **Energy Saver** | 2 | Restores stamina on each successful attack. |

#### ⛏️ Tools

| Ability | Cost | Effect |
| :--- | :---: | :--- |
| **Vein Miner** | 1 | Mine all connected ore blocks simultaneously (up to 20 blocks). |
| **Auto Smelt** | 1 | Mined resources are automatically smelted in your inventory. |

***

## 🔥 Core Features

*   **Evolution:** When you use a tool, it gains XP. When the bar fills, it levels up and fully repairs itself!
*   **Growing Power:** Every level grants increased stats.
*   **Prestige:** Convert accumulated levels into Ability Tokens and reset the item for another run.
*   **RPG Progression:** Turn your standard equipment into master-crafted artifacts simply by using them.
*   **Universal Support:** Works on **Swords, Axes, Pickaxes, Shovels, Hoes, and Custom Items!**
*   **Visuals:** Features a beautiful HUD showing your tool's Level, XP Bar, and Max Level capacity.

## 📈 Stats & Bonuses

Your tools get stronger with every level:

| Stat         |Bonus per Level |Effect                                                      |
| ------------ |--------------- |----------------------------------------------------------- |
| <strong>Durability</strong> |<code>+15%</code> |Tool maximum durability increases, making it last longer.   |
| <strong>Damage</strong> |<code>+5%</code> |Weapons (Swords/Axes) deal more damage per hit.             |
| <strong>Mining Speed</strong> |<code>+5%</code> |Tools (Pickaxes/Shovels) break blocks significantly faster. |

## 💎 Rarity & Level Caps

The quality of the item determines its maximum potential:

| Tier       |Max Level    |
| ---------- |------------ |
| <strong>Trash/Junk</strong> |Lv. 3        |
| <strong>Common</strong> |Lv. 5        |
| <strong>Uncommon</strong> |Lv. 10       |
| <strong>Rare</strong> |Lv. 15       |
| <strong>Epic</strong> |Lv. 25       |
| <strong>Legendary</strong> |<strong>Infinite (∞)</strong> |

> _Special Exception: **Crude Hoe** also scales infinitely!_

## ⚖️ Balance Mechanics

*   **Use it to Lose it:** You gain XP by _using_ durability.
*   **Manual Repairs:** If you repair a tool manually (e.g., at a bench), you **lose the XP progress** of the current level. The tool must be worn down naturally to evolve!
*   **Auto-Repair:** Upon leveling up, the tool is fully repaired automatically as a reward.
*   **Prestige Resets the Race:** After prestiging, the item returns to a lower level — start grinding again, but now with abilities unlocked!

## ⚙️ Configuration

Fully customizable via `LevelTools.json`:

```
{
    "Enabled": true,
    "DurabilityLossToLevel": 100.0,
    "DurabilityBonusPercent": 15.0,
    "SpeedBonusPercent": 5.0,
    "DamageBonusPercent": 5.0,
    "WeaponXPMultiplier": 2.5,
    "ToolXPMultiplier": 1.0,
    "ShowLevelUpMessage": true,
    "ShowHUD": true,
    "TokensPerLevel": 5,
    "HudRefreshIntervalMs": 500,
    "MaxLevelJunk": 3,
    "MaxLevelCommon": 5,
    "MaxLevelUncommon": 10,
    "MaxLevelRare": 15,
    "MaxLevelEpic": 25,
    "MaxLevelLegendary": -1,
    "VeinMinerMaxBlocks": 20,
    "VampirismLifestealPercent": 20.0,
    "IgnoreDefenseMultiplier": 1.5,
    "EnergySaverStaminaRefund": 20.0,
    "FireBurnDurationSeconds": 5.0,
    "PoisonDurationSeconds": 4.0,
    "SalvageCompatEnabled": true,
    "LevelUpFullRepair": true
}
```

## 🎮 Commands

| Command | Description |
| :--- | :--- |
| `/leveltools` | Show all available commands |
| `/leveltools hud` | Toggle the LevelTools HUD on/off |
| `/leveltools tokens` | Open the Weapon Mastery menu |
| `/leveltools prestige` | Convert item levels into Ability Tokens (hold the item) |
| `/leveltools setlevel <level>` | Force-set item level _(requires `leveltools.admin` permission)_ |

## 📥 Installation

1.  Download `Linceros.LevelTools-2.3.jar`.
2.  Install [MultipleHUD](https://www.curseforge.com/hytale/mods/multiplehud).
3.  Place both files in your server's `mods` folder.
4.  Restart and start grinding!

## 👨‍💻 Author

Created by **Linceros**.

_Part of the Linceros Mod Collection for Hytale._