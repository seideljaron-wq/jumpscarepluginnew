# JumpScare

Scare players with MAX volume sounds and a screen flash effect.

## Commands

| Command | Description |
|---------|-------------|
| `/jumpscare <player> [sound]` | Scare a player |
| `/jumpscare add <player>` | Grant /jumpscare access |
| `/jumpscare remove <player>` | Revoke access |

## Sounds

| Key | Sound |
|-----|-------|
| `ghast` | Ghast scream |
| `wither` | Wither spawn |
| `guardian` | Elder Guardian curse *(default)* |
| `thunder` | Lightning thunder |

## Who can use it?
- Server OPs (always)
- Players added via `/jumpscare add`
- Players with `jumpscare.use` permission

## config.yml
```yaml
sound-volume: 10.0          # 10.0 = max deafening
title-duration-ticks: 30    # 30 ticks = 1.5 seconds
cooldown-seconds: 5
```
