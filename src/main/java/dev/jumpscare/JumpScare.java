package dev.jumpscare;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JumpScare extends JavaPlugin {

    private static JumpScare instance;
    private final List<UUID> allowedUsers = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadAllowedUsers();

        JumpScareCommand cmd = new JumpScareCommand(this);
        getCommand("jumpscare").setExecutor(cmd);
        getCommand("jumpscare").setTabCompleter(cmd);

        getLogger().info("JumpScare enabled! BOO!");
    }

    @Override
    public void onDisable() {
        getLogger().info("JumpScare disabled.");
    }

    public static JumpScare getInstance() { return instance; }

    // ── Allowed users ─────────────────────────────────────────────────────────

    private void loadAllowedUsers() {
        allowedUsers.clear();
        for (String s : getConfig().getStringList("allowed-users")) {
            try { allowedUsers.add(UUID.fromString(s)); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    public boolean isAllowed(UUID uuid) {
        return allowedUsers.contains(uuid);
    }

    public void addUser(UUID uuid) {
        if (!allowedUsers.contains(uuid)) {
            allowedUsers.add(uuid);
            saveUsers();
        }
    }

    public boolean removeUser(UUID uuid) {
        boolean removed = allowedUsers.remove(uuid);
        if (removed) saveUsers();
        return removed;
    }

    private void saveUsers() {
        List<String> list = new ArrayList<>();
        allowedUsers.forEach(u -> list.add(u.toString()));
        getConfig().set("allowed-users", list);
        saveConfig();
    }
}
