package InkTrace.cn.blockking.game;

import InkTrace.cn.blockking.manager.ConfigManager;
import InkTrace.cn.blockking.manager.GameManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class PlayingState implements GameState {
    private final GameManager gameManager;
    private final ConfigManager configManager;
    private final Set<UUID> alivePlayers = new HashSet<>();
    private BukkitTask checkTask;
    private boolean gameStarted = false;
    private final List<Location> usedSpawns = new ArrayList<>();
    private Set<Player> initialPlayers;
    private int currentPhase = 1;
    private int phaseTimer = 0;
    private BukkitTask phaseTask;
    private Scoreboard board;
    private Objective obj;
    private Map<UUID, Integer> killCounts = new HashMap<>();
    private long gameStartTime;
    private static final long GAME_DURATION = 600000;
    private final SpectatorState spectatorState;
    private static Plugin plugin;

    public PlayingState(GameManager gameManager, ConfigManager configManager, Plugin plugin) {
        this.gameManager = gameManager;
        this.configManager = configManager;
        this.plugin = plugin;
        this.spectatorState = new SpectatorState(configManager, gameManager, plugin);
        setupScoreboard();
    }

    public static void setPlugin(Plugin p) {
        plugin = p;
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        board = manager.getNewScoreboard();
        obj = board.registerNewObjective("playing", "dummy");
        obj.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&l方块之王"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateScoreboard();
    }

    private void updateScoreboard() {
        for (String entry : new HashSet<>(board.getEntries())) {
            board.resetScores(entry);
        }

        String mapName = configManager.getMapName();
        String serverIP = configManager.getServerIP();
        long remainingTime = getRemainingTime();

        obj.getScore(ChatColor.translateAlternateColorCodes('&', "&r")).setScore(9);
        obj.getScore(ChatColor.translateAlternateColorCodes('&', "&f地图: &a" + mapName)).setScore(8);
        obj.getScore(" ").setScore(7);
        obj.getScore(ChatColor.translateAlternateColorCodes('&', "&f时间: &a" + formatTime(remainingTime))).setScore(6);
        obj.getScore(" ").setScore(5);
        obj.getScore(ChatColor.translateAlternateColorCodes('&', "&f当前阶段:")).setScore(4);
        obj.getScore(ChatColor.translateAlternateColorCodes('&', "&f第&b " + currentPhase + " &f阶段")).setScore(3);
        obj.getScore(" ").setScore(2);
        obj.getScore(ChatColor.translateAlternateColorCodes('&', "&f剩余玩家: &a" + alivePlayers.size())).setScore(1);
        int totalKills = killCounts.values().stream().mapToInt(Integer::intValue).sum();
        obj.getScore(ChatColor.translateAlternateColorCodes('&', "&f击杀玩家: &a" + totalKills)).setScore(0);
        obj.getScore("").setScore(-1);
        obj.getScore(ChatColor.translateAlternateColorCodes('&', serverIP)).setScore(-2);

        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(board);
            }
        }
    }

    private long getRemainingTime() {
        if (!gameStarted) return GAME_DURATION;
        long elapsed = System.currentTimeMillis() - gameStartTime;
        return Math.max(0, GAME_DURATION - elapsed);
    }

    private String formatTime(long millis) {
        int seconds = (int) (millis / 1000) % 60;
        int minutes = (int) (millis / 60000);
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void setInitialPlayers(Set<Player> players) {
        this.initialPlayers = players;
    }

    @Override
    public void onEnable() {
        Bukkit.getWorlds().forEach(world -> {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
        });

        List<Location> spawns = configManager.getSpawnPoints();
        List<Float> yaws = configManager.getSpawnYaws();
        if (spawns.size() < GameManager.MIN_PLAYERS) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&c出生点不足！需要至少 " + GameManager.MIN_PLAYERS + " &c个出生点"));
            gameManager.setState(new WaitingState(gameManager, configManager));
            return;
        }

        Collections.shuffle(spawns);
        Iterator<Location> spawnIterator = spawns.iterator();
        Iterator<Float> yawIterator = yaws.iterator();

        if (initialPlayers == null || initialPlayers.isEmpty()) {
            Bukkit.getLogger().warning("No initial players found when starting the game.");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&c没有等待的玩家，无法开始游戏！"));
            gameManager.setState(new WaitingState(gameManager, configManager));
            return;
        }

        for (Player player : initialPlayers) {
            if (!spawnIterator.hasNext() || !yawIterator.hasNext()) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        "&c出生点或角度信息不足，无法让所有玩家进入游戏！"));
                break;
            }
            Location spawn = spawnIterator.next();
            float yaw = yawIterator.next();
            usedSpawns.add(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(10);
            player.setFireTicks(0);
            if (spawn.getWorld() != null) {
                Location centerLocation = new Location(spawn.getWorld(), spawn.getBlockX() + 0.5, spawn.getBlockY(), spawn.getBlockZ() + 0.5, yaw, 0);
                player.teleport(centerLocation);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a你已被传送到战斗位置！"));
                alivePlayers.add(player.getUniqueId());
                killCounts.put(player.getUniqueId(), 0);
            } else {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        "&c出生点世界无效，无法传送玩家 " + player.getName() + "！"));
            }
        }

        if (alivePlayers.size() < GameManager.MIN_PLAYERS) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&c玩家数量不足，无法开始游戏！"));
            gameManager.setState(new WaitingState(gameManager, configManager));
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gameStarted = true;
            gameStartTime = System.currentTimeMillis();
            startGameLoop();
            startItemGiveTask(gameManager, alivePlayers, gameStarted, currentPhase);

            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&6===== &l游戏开始 &6====="));
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&a地图: &e" + configManager.getMapName() + " &f| 时长: &a10分钟"));
            startPhaseTimer();
            updateScoreboard();
        }, 20L);
    }

    private void startGameLoop() {
        checkTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::checkGameStatus,
                20L,
                20L
        );
    }

    private void checkGameStatus() {
        if (gameStarted) {
            if (System.currentTimeMillis() - gameStartTime >= GAME_DURATION) {
                endGame();
                return;
            }
            if (alivePlayers.size() <= 1) {
                endGame();
            }
        }
    }

    public void handleVoidDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player) {
            event.setCancelled(true);
            Player player = (Player) event.getEntity();
            Player killer = null;
            if (event instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent damageByEntityEvent = (EntityDamageByEntityEvent) event;
                if (damageByEntityEvent.getDamager() instanceof Player) {
                    killer = (Player) damageByEntityEvent.getDamager();
                }
            }
            handlePlayerDeath(player, killer);
        }
    }

    public void handleMove(PlayerMoveEvent event) {
        if (event.getTo().getY() < 0) {
            Player player = event.getPlayer();
            Player killer = null;
            EntityDamageEvent lastDamageEvent = player.getLastDamageCause();
            if (lastDamageEvent instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent damageByEntityEvent = (EntityDamageByEntityEvent) lastDamageEvent;
                if (damageByEntityEvent.getDamager() instanceof Player) {
                    killer = (Player) damageByEntityEvent.getDamager();
                }
            }
            handlePlayerDeath(player, killer);
        }
    }

    public void handlePlayerDeath(Player deadPlayer, Player killer) {
        String deadPlayerPrefix = getPlayerPrefix(deadPlayer);
        String message;
        if (killer != null) {
            String killerPrefix = getPlayerPrefix(killer);
            message = ChatColor.translateAlternateColorCodes('&', "&c&l淘汰！ " + deadPlayerPrefix + deadPlayer.getName() + " 被 " + killerPrefix + killer.getName() + " &f击杀");
            int kills = killCounts.getOrDefault(killer.getUniqueId(), 0);
            killCounts.put(killer.getUniqueId(), kills + 1);
        } else {
            message = ChatColor.translateAlternateColorCodes('&', "&c&l死亡！ " + deadPlayerPrefix + deadPlayer.getName() + " &f死于虚空");
        }
        Bukkit.broadcastMessage(message);
        eliminatePlayer(deadPlayer);
        spectatorState.setPlayerAsSpectator(deadPlayer);
        updateScoreboard();
    }

    private void eliminatePlayer(Player player) {
        if (alivePlayers.remove(player.getUniqueId())) {
            player.setHealth(0);
            player.spigot().respawn();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c你已被淘汰！"));
            spectatorState.setPlayerAsSpectator(player);
            checkGameStatus();
        }
    }

    private void endGame() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        if (phaseTask != null) {
            phaseTask.cancel();
        }
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
            }
        }
        UUID winnerUUID = alivePlayers.stream().findFirst().orElse(null);
        if (winnerUUID != null) {
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&6&l恭喜 " + winner.getName() + " 获胜！"));
            }
        } else {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&c游戏结束，没有获胜者"));
        }
        gameManager.setState(new EndingState(gameManager, configManager));
    }

    @Override
    public void onDisable() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        if (phaseTask != null) {
            phaseTask.cancel();
        }
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
            }
        }
        alivePlayers.clear();
        usedSpawns.clear();
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&6===== 游戏结束 ====="));
    }

    @Override
    public void onPlayerJoin(Player player) {
        spectatorState.setPlayerAsSpectator(player);
    }

    @Override
    public void onPlayerLeave(Player player) {
        eliminatePlayer(player);
        updateScoreboard();
    }

    public Set<Player> getAlivePlayers() {
        Set<Player> players = new HashSet<>();
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) players.add(p);
        }
        return players;
    }

    private String getPlayerPrefix(Player player) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                LuckPerms api = LuckPermsProvider.get();
                CachedMetaData metaData = api.getPlayerAdapter(Player.class).getMetaData(player);
                String prefix = metaData.getPrefix();
                return prefix != null ? ChatColor.translateAlternateColorCodes('&', prefix) : "";
            }
        } catch (IllegalStateException e) {
        }
        return "";
    }

    @Override
    public void startItemGiveTask(GameManager gameManager, Set<UUID> alivePlayers, boolean gameStarted, int phase) {
        List<Material> validMaterials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (isBlock(material) || isWeapon(material) || isFood(material) || isMonsterEgg(material) || isPotion(material)) {
                validMaterials.add(material);
            }
        }

        long interval = phase == 1 ? 200L : 100L;

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gameStarted) {
                Random random = new Random();
                for (UUID uuid : alivePlayers) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        if (!validMaterials.isEmpty()) {
                            Material randomMaterial = validMaterials.get(random.nextInt(validMaterials.size()));
                            ItemStack item = new ItemStack(randomMaterial, 1);
                            player.getInventory().addItem(item);
                        }
                    }
                }
            }
        }, 0L, interval);
    }

    private boolean isBlock(Material material) {
        return material.isBlock();
    }

    private boolean isWeapon(Material material) {
        List<Material> weapons = Arrays.asList(
                Material.WOOD_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                Material.GOLD_SWORD, Material.DIAMOND_SWORD,
                Material.WOOD_AXE, Material.STONE_AXE, Material.IRON_AXE,
                Material.GOLD_AXE, Material.DIAMOND_AXE
        );
        return weapons.contains(material);
    }

    private boolean isFood(Material material) {
        return material.isEdible();
    }

    private boolean isMonsterEgg(Material material) {
        return material == Material.MONSTER_EGG;
    }

    private boolean isPotion(Material material) {
        return material == Material.POTION;
    }

    private void startPhaseTimer() {
        phaseTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            phaseTimer++;
            if (phaseTimer >= 60) {
                currentPhase = 2;
                if (phaseTask != null) {
                    phaseTask.cancel();
                }
                if (checkTask != null) {
                    checkTask.cancel();
                }
                startItemGiveTask(gameManager, alivePlayers, gameStarted, currentPhase);
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&e游戏进入第二阶段！"));
                updateScoreboard();
            }
        }, 0L, 20L);
    }

    @Override
    public int getCurrentPhase() {
        return currentPhase;
    }
}