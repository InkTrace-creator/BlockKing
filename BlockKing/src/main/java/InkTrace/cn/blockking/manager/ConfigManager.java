package InkTrace.cn.blockking.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private List<Location> spawnPoints = new ArrayList<>();
    private List<Float> spawnYaws = new ArrayList<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public String getServerIP() {
        return config.getString("server-ip", "play.example.com");
    }

    public String getMapName() {
        return config.getString("map-name", "默认地图");
    }

    public String getLobbyServerName() {
        return config.getString("lobby-server", "lobby");
    }

    public Location getLobbyLocation() {
        if (!config.contains("lobby.world")) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(
                Bukkit.getWorld(config.getString("lobby.world")),
                config.getDouble("lobby.x"),
                config.getDouble("lobby.y"),
                config.getDouble("lobby.z"),
                (float) config.getDouble("lobby.yaw", 0),
                (float) config.getDouble("lobby.pitch", 0)
        );
    }

    public void setLobbyLocation(Location location) {
        config.set("lobby.world", location.getWorld().getName());
        config.set("lobby.x", location.getX());
        config.set("lobby.y", location.getY());
        config.set("lobby.z", location.getZ());
        config.set("lobby.yaw", location.getYaw());
        config.set("lobby.pitch", location.getPitch());
        save();
    }

    public void setMapName(String name) {
        config.set("map-name", name);
        save();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        reloadSpawns();
        reloadSpectatorSpawn();
    }

    public void save() {
        plugin.saveConfig();
    }

    public FileConfiguration getRawConfig() {
        return config;
    }

    public void addSpawnPoint(Location location) {
        int index = spawnPoints.size() + 1;
        String path = "spawn-points." + index;
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        float yaw = location.getYaw();
        config.set(path + ".yaw", yaw);
        config.set(path + ".pitch", location.getPitch());
        save();
        reloadSpawns();
        spawnYaws.add(yaw);
    }

    public List<Location> getSpawnPoints() {
        return new ArrayList<>(spawnPoints);
    }

    public List<Float> getSpawnYaws() {
        return new ArrayList<>(spawnYaws);
    }

    private void reloadSpawns() {
        spawnPoints.clear();
        spawnYaws.clear();
        if (config.getConfigurationSection("spawn-points") != null) {
            for (String key : config.getConfigurationSection("spawn-points").getKeys(false)) {
                Location location = new Location(
                        Bukkit.getWorld(config.getString("spawn-points." + key + ".world")),
                        config.getDouble("spawn-points." + key + ".x"),
                        config.getDouble("spawn-points." + key + ".y"),
                        config.getDouble("spawn-points." + key + ".z"),
                        (float) config.getDouble("spawn-points." + key + ".yaw", 0),
                        (float) config.getDouble("spawn-points." + key + ".pitch", 0)
                );
                spawnPoints.add(location);
                spawnYaws.add((float) config.getDouble("spawn-points." + key + ".yaw", 0));
            }
        }
    }

    public Location getSpectatorSpawn() {
        if (!config.contains("spectator-spawn.world")) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(
                Bukkit.getWorld(config.getString("spectator-spawn.world")),
                config.getDouble("spectator-spawn.x"),
                config.getDouble("spectator-spawn.y"),
                config.getDouble("spectator-spawn.z"),
                (float) config.getDouble("spectator-spawn.yaw", 0),
                (float) config.getDouble("spectator-spawn.pitch", 0)
        );
    }

    public void setSpectatorSpawn(Location location) {
        config.set("spectator-spawn.world", location.getWorld().getName());
        config.set("spectator-spawn.x", location.getX());
        config.set("spectator-spawn.y", location.getY());
        config.set("spectator-spawn.z", location.getZ());
        config.set("spectator-spawn.yaw", location.getYaw());
        config.set("spectator-spawn.pitch", location.getPitch());
        save();
    }

    private void reloadSpectatorSpawn() {
    }
}