package dev.jumpscare;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;

public class JumpScareCommand implements CommandExecutor, TabCompleter {

    private final JumpScare plugin;

    // Cooldown map: target UUID → timestamp of last scare (ms)
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    // Available sounds with friendly names
    // Volume 10.0 = MAX (default Minecraft cap per client is 1.0,
    // but sending volume > 1.0 via the API bypasses the slider and forces max)
    private static final Map<String, Sound> SOUNDS = new LinkedHashMap<>();
    static {
        SOUNDS.put("ghast",    Sound.ENTITY_GHAST_WARN);
        SOUNDS.put("wither",   Sound.ENTITY_WITHER_SPAWN);
        SOUNDS.put("guardian", Sound.ENTITY_ELDER_GUARDIAN_CURSE);
        SOUNDS.put("thunder",  Sound.ENTITY_LIGHTNING_BOLT_THUNDER);
    }

    public JumpScareCommand(JumpScare plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // ── /jumpscare add/remove <player> ────────────────────────────────────
        if (args.length >= 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            handleManage(sender, args);
            return true;
        }

        // ── /jumpscare <player> [sound] ───────────────────────────────────────
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        // Permission check: OP, jumpscare.use, or in allowed-users list
        if (!canUse(sender)) {
            sender.sendMessage(Component.text("✗ You don't have permission to use /jumpscare.", NamedTextColor.RED));
            return true;
        }

        // Resolve target
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("✗ Player '" + args[0] + "' is not online.", NamedTextColor.RED));
            return true;
        }

        // Prevent self-scare (unless console)
        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(Component.text("✗ You can't jumpscare yourself.", NamedTextColor.RED));
            return true;
        }

        // Cooldown check
        int cooldownSec = plugin.getConfig().getInt("cooldown-seconds", 5);
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(target.getUniqueId())) {
            long elapsed = now - cooldowns.get(target.getUniqueId());
            long remaining = (cooldownSec * 1000L) - elapsed;
            if (remaining > 0) {
                sender.sendMessage(Component.text(
                    "⚠ Wait " + (remaining / 1000 + 1) + "s before scaring " + target.getName() + " again.",
                    NamedTextColor.YELLOW));
                return true;
            }
        }

        // Resolve chosen sound (default: guardian for maximum terror)
        String soundKey = args.length >= 2 ? args[1].toLowerCase() : "guardian";
        Sound sound = SOUNDS.getOrDefault(soundKey, Sound.ENTITY_ELDER_GUARDIAN_CURSE);

        // ── FIRE THE JUMPSCARE ────────────────────────────────────────────────
        executeJumpScare(target, sound);
        cooldowns.put(target.getUniqueId(), now);

        sender.sendMessage(
            Component.text("💀 Jumpscared ", NamedTextColor.GREEN)
                .append(Component.text(target.getName(), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" with ", NamedTextColor.GREEN))
                .append(Component.text("[" + soundKey + "]", NamedTextColor.AQUA))
                .append(Component.text("!", NamedTextColor.GREEN))
        );

        return true;
    }

    // ── Core jumpscare logic ──────────────────────────────────────────────────

    private void executeJumpScare(Player target, Sound sound) {
        float volume = (float) plugin.getConfig().getDouble("sound-volume", 10.0);
        int durationTicks = plugin.getConfig().getInt("title-duration-ticks", 30);

        // Play sound at MAX volume (10.0 blasts past the client volume slider)
        // Pitch 0.5 = deep and terrifying
        target.playSound(target.getLocation(), sound, volume, 0.5f);

        // Also layer thunder on top for extra punch (unless it IS thunder)
        if (sound != Sound.ENTITY_LIGHTNING_BOLT_THUNDER) {
            target.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, volume * 0.6f, 0.6f);
        }

        // Screen effect: red flash title
        // fadeIn=0, stay=durationTicks, fadeOut=5 → appears instantly, disappears quickly
        Title.Times times = Title.Times.times(
            Duration.ZERO,                                    // fade in  – instant
            Duration.ofMillis(durationTicks * 50L),          // stay
            Duration.ofMillis(5 * 50L)                       // fade out – very quick
        );

        Title title = Title.title(
            Component.text("⚠", NamedTextColor.RED).decorate(TextDecoration.BOLD),
            Component.text("BOO!", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
            times
        );

        target.showTitle(title);

        // Send red action bar text too
        target.sendActionBar(
            Component.text("☠ ☠ ☠", NamedTextColor.RED).decorate(TextDecoration.BOLD)
        );

        // Stop ALL sounds after durationTicks + a tiny buffer (so it cuts off hard)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            target.stopAllSounds();
            target.clearTitle();
            target.sendActionBar(Component.empty());
        }, durationTicks + 2L);
    }

    // ── /jumpscare add/remove ─────────────────────────────────────────────────

    private void handleManage(CommandSender sender, String[] args) {
        // Only OPs or jumpscare.admin can manage the list
        boolean isConsole = !(sender instanceof Player);
        boolean isOp      = !isConsole && ((Player) sender).isOp();
        boolean hasPerm   = !isConsole && sender.hasPermission("jumpscare.admin");

        if (!isConsole && !isOp && !hasPerm) {
            sender.sendMessage(Component.text("✗ Only OPs can manage jumpscare users.", NamedTextColor.RED));
            return;
        }

        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            // Try offline
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(Component.text("✗ Player '" + args[1] + "' not found.", NamedTextColor.RED));
                return;
            }
            handleOfflineManage(sender, action, offline);
            return;
        }

        handleOnlineManage(sender, action, target);
    }

    private void handleOnlineManage(CommandSender sender, String action, Player target) {
        switch (action) {
            case "add" -> {
                plugin.addUser(target.getUniqueId());
                sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                    .append(Component.text(target.getName(), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" can now use /jumpscare.", NamedTextColor.GREEN)));
                target.sendMessage(Component.text("✔ You can now use ", NamedTextColor.GREEN)
                    .append(Component.text("/jumpscare", NamedTextColor.AQUA))
                    .append(Component.text("!", NamedTextColor.GREEN)));
            }
            case "remove" -> {
                if (plugin.removeUser(target.getUniqueId())) {
                    sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                        .append(Component.text(target.getName(), NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(" can no longer use /jumpscare.", NamedTextColor.GREEN)));
                    target.sendMessage(Component.text("✗ Your /jumpscare access was revoked.", NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("⚠ " + target.getName() + " is not in the list.", NamedTextColor.YELLOW));
                }
            }
        }
    }

    private void handleOfflineManage(CommandSender sender, String action, org.bukkit.OfflinePlayer target) {
        String name = target.getName() != null ? target.getName() : "Unknown";
        switch (action) {
            case "add" -> {
                plugin.addUser(target.getUniqueId());
                sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                    .append(Component.text(name, NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" can now use /jumpscare.", NamedTextColor.GREEN)));
            }
            case "remove" -> {
                if (plugin.removeUser(target.getUniqueId())) {
                    sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                        .append(Component.text(name, NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(" removed from jumpscare list.", NamedTextColor.GREEN)));
                } else {
                    sender.sendMessage(Component.text("⚠ " + name + " is not in the list.", NamedTextColor.YELLOW));
                }
            }
        }
    }

    // ── Permission helper ─────────────────────────────────────────────────────

    private boolean canUse(CommandSender sender) {
        if (!(sender instanceof Player p)) return true; // console always allowed
        return p.isOp()
            || p.hasPermission("jumpscare.use")
            || plugin.isAllowed(p.getUniqueId());
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("── JumpScare Commands ──", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /jumpscare <player> [sound]  ", NamedTextColor.AQUA)
            .append(Component.text("Scare a player", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  Sounds: ", NamedTextColor.AQUA)
            .append(Component.text("ghast, wither, guardian, thunder", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /jumpscare add <player>      ", NamedTextColor.AQUA)
            .append(Component.text("Grant access", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /jumpscare remove <player>   ", NamedTextColor.AQUA)
            .append(Component.text("Revoke access", NamedTextColor.GRAY)));
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("add", "remove"));
            String pref = args[0].toLowerCase();
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(pref)) options.add(p.getName());
            });
            return options.stream().filter(s -> s.startsWith(pref)).toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("remove")) {
                List<String> names = new ArrayList<>();
                String pref = args[1].toLowerCase();
                Bukkit.getOnlinePlayers().forEach(p -> {
                    if (p.getName().toLowerCase().startsWith(pref)) names.add(p.getName());
                });
                return names;
            }
            // Second arg for scare = sound
            return new ArrayList<>(SOUNDS.keySet());
        }

        return List.of();
    }
}
