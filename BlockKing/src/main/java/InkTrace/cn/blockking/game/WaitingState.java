package InkTrace.cn.blockking.game;

import InkTrace.cn.blockking.manager.ConfigManager;
import InkTrace.cn.blockking.manager.GameManager;
import InkTrace.cn.blockking.BungeeCord;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WaitingState implements GameState, Listener {
    private final GameManager gameManager;
    private final ConfigManager configManager;
    private final Set<Player> waitingPlayers = new HashSet<>();
    private final ConcurrentHashMap<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Set<Player> adminPlayers = new HashSet<>();
    private int countdownTime = 0;
    private int countdownTaskId = -1;
    private Scoreboard board;
    private Objective obj;
    private boolean isUpdatingScoreboard = false;
    private static Plugin plugin;

    public WaitingState(GameManager gameManager, ConfigManager configManager) {
        this.gameManager = gameManager;
        this.configManager = configManager;
        setupScoreboard();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static void setPlugin(Plugin p) {
        plugin = p;
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        board = manager.getNewScoreboard();
        obj = board.registerNewObjective("waiting", "dummy");
        obj.setDisplayName("§e§l方块之王");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateScoreboard();
    }

    private void updateScoreboard() {
        if (isUpdatingScoreboard) return;
        isUpdatingScoreboard = true;

        try {
            for (String entry : new HashSet<>(board.getEntries())) {
                board.resetScores(entry);
            }

            obj.getScore("").setScore(8);
            obj.getScore("§f地图: §a" + configManager.getMapName()).setScore(7);
            obj.getScore("§f玩家: §a" + waitingPlayers.size() + "/" + GameManager.MAX_PLAYERS).setScore(6);
            obj.getScore(" ").setScore(5);
            obj.getScore("§f状态: §a" + (countdownTime > 0 ? countdownTime + "秒" : "等待中")).setScore(4);
            obj.getScore("  ").setScore(3);
            obj.getScore("§f模式: §a普通").setScore(2);
            obj.getScore("  ").setScore(1);
            obj.getScore("§7" + configManager.getServerIP()).setScore(0);

            for (Player player : waitingPlayers) {
                player.setScoreboard(board);
            }
        } finally {
            isUpdatingScoreboard = false;
        }
    }

    @Override
    public void onEnable() {
        System.out.println("WaitingState.onEnable() - Enabling waiting state");
        Bukkit.getWorlds().forEach(world -> {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
        });

        waitingPlayers.clear();
        countdownTime = 0;
        cancelCountdown();
        adminPlayers.clear();

        // 清除所有玩家的物品和隐身效果
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPlayerState(player);
        }
    }

    @Override
    public void onDisable() {
        System.out.println("WaitingState.onDisable() - Disabling waiting state. Waiting players size: " + waitingPlayers.size());
        cancelCountdown();
        waitingPlayers.forEach(player -> {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            cancelPendingTeleport(player);
        });
        // Do not clear waitingPlayers here
        // waitingPlayers.clear();
        adminPlayers.clear();
    }

    @Override
    public void onPlayerJoin(Player player) {
        if (waitingPlayers.size() >= GameManager.MAX_PLAYERS) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c游戏人数已满！"));
            return;
        }

        // 清除玩家的物品和隐身效果
        clearPlayerState(player);

        waitingPlayers.add(player);
        player.setScoreboard(board);
        player.setFoodLevel(20);
        player.setSaturation(10);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFallDistance(0);
        giveReturnBed(player);

        if (player.hasPermission("blockking.admin")) {
            adminPlayers.add(player);
            giveAdminItem(player);
        }

        Location lobby = configManager.getLobbyLocation();
        if (lobby != null && lobby.getWorld() != null) {
            player.teleport(lobby);
        }

        String prefix = getPlayerPrefix(player);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                String.format("%s%s &e加入游戏 (&b%d&e/&b%d&e)",
                        prefix,
                        player.getName(),
                        waitingPlayers.size(),
                        GameManager.MAX_PLAYERS)));
        updateScoreboard();
        checkStartConditions();
    }


    @Override
    public void onPlayerLeave(Player player) {
        waitingPlayers.remove(player);
        cancelPendingTeleport(player);
        adminPlayers.remove(player);

        String prefix = getPlayerPrefix(player);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                String.format("%s%s &e离开游戏 (&b%d&e/&b%d&e)",
                        prefix,
                        player.getName(),
                        waitingPlayers.size(),
                        GameManager.MAX_PLAYERS)));

        updateScoreboard();

        if (waitingPlayers.size() < GameManager.MIN_PLAYERS && countdownTaskId != -1) {
            cancelCountdown();
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&c人数不足，倒计时已取消！"));
        }
    }

    private void giveReturnBed(Player player) {
        ItemStack bed = new ItemStack(Material.BED);
        ItemMeta meta = bed.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&c返回大厅 &7(右键)"));
        bed.setItemMeta(meta);
        player.getInventory().setItem(8, bed);
    }

    private void giveAdminItem(Player player) {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b强制开始 &7(右键)"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        // 将钻石物品放到物品栏第八格
        player.getInventory().setItem(7, item);
    }

    public boolean handleBedClick(Player player) {
        ItemStack item = player.getInventory().getItemInHand();
        if (item == null || item.getType() != Material.BED ||
                !item.hasItemMeta() ||
                !item.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', "&c返回大厅 &7(右键)"))) {
            return false;
        }

        if (cancelPendingTeleport(player)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c&l已取消传送"));
            return true;
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a&l五秒后将传送至大厅... 再次右键取消"));
        pendingTeleports.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    pendingTeleports.remove(player.getUniqueId());
                    BungeeCord.connect(player, configManager.getLobbyServerName());
                },
                20 * 5
        ));
        return true;
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
            // LuckPerms未加载时忽略
        }
        return "";
    }

    private boolean cancelPendingTeleport(Player player) {
        BukkitTask task = pendingTeleports.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            return true;
        }
        return false;
    }

    private void checkStartConditions() {
        if (waitingPlayers.size() == GameManager.MAX_PLAYERS && countdownTaskId != -1 && countdownTime > 5) {
            countdownTime = 5;
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&e人数已满，即将开始！"));
            updateScoreboard();
            return;
        }

        if (waitingPlayers.size() >= GameManager.MIN_PLAYERS && countdownTaskId == -1) {
            startCountdown(60);
        }
    }

    private void startCountdown(int seconds) {
        cancelCountdown();
        countdownTime = seconds;

        AtomicInteger timeLeft = new AtomicInteger(seconds);
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                () -> {
                    int currentTime = timeLeft.getAndDecrement();
                    countdownTime = currentTime;

                    if (shouldAnnounce(currentTime)) {
                        String message = ChatColor.translateAlternateColorCodes('&',
                                "&e游戏将在 &c" + currentTime + " &e秒后开始！");
                        Bukkit.broadcastMessage(message);
                        playSoundToAllPlayers(Sound.NOTE_PLING);

                        if (currentTime <= 5) {
                            sendTitleToAllPlayers(currentTime);
                        }
                    }

                    updateScoreboard();

                    if (currentTime <= 0) {
                        if (waitingPlayers.size() >= GameManager.MIN_PLAYERS &&
                                configManager.getSpawnPoints().size() >= GameManager.MIN_PLAYERS) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                List<Location> spawnPoints = configManager.getSpawnPoints();
                                int index = 0;
                                for (Player player : new HashSet<>(waitingPlayers)) {
                                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a正在传送至游戏位置..."));
                                    Location spawn = spawnPoints.get(index % spawnPoints.size());
                                    player.teleport(new Location(spawn.getWorld(), spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()));
                                    index++;
                                }
                                gameManager.setState(new PlayingState(gameManager, configManager, plugin));
                            });
                        } else {
                            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                                    "&c需要至少 " + GameManager.MIN_PLAYERS + " &c名玩家和 &4" + GameManager.MIN_PLAYERS + " &c个出生点！"));
                            cancelCountdown();
                        }
                    }
                },
                0L,
                20L
        );
    }

    private boolean shouldAnnounce(int time) {
        return time == 60 || time == 45 || time == 30 || time == 15 || time == 10 || time <= 5;
    }

    private void playSoundToAllPlayers(Sound sound) {
        for (Player player : waitingPlayers) {
            player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
        }
    }

    private void sendTitleToAllPlayers(int time) {
        String title = ChatColor.translateAlternateColorCodes('&', " ");
        String subtitle = ChatColor.translateAlternateColorCodes('&', "&a" + time);
        for (Player player : waitingPlayers) {
            player.sendTitle(title, subtitle);
        }
    }

    private void cancelCountdown() {
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
        countdownTime = 0;
        updateScoreboard();
    }

    public Set<Player> getWaitingPlayers() {
        return new HashSet<>(waitingPlayers);
    }

    // 判断玩家是否为旁观者状态的方法，假设旁观者状态是通过 playerListName 为空字符串判断
    private boolean isSpectator(Player player) {
        return player.getPlayerListName().isEmpty();
    }

    // 清除玩家的物品和隐身效果
    private void clearPlayerState(Player player) {
        player.getInventory().clear();
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setPlayerListName(player.getName());
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                onlinePlayer.showPlayer(player);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (adminPlayers.contains(player) && event.getAction().name().contains("RIGHT_CLICK")) {
            ItemStack item = player.getInventory().getItem(7);
            if (item != null && item.getType() == Material.DIAMOND) {
                if (waitingPlayers.size() >= GameManager.MIN_PLAYERS &&
                        configManager.getSpawnPoints().size() >= GameManager.MIN_PLAYERS) {
                    // 移除所有管理员玩家的强制开始物品
                    for (Player admin : adminPlayers) {
                        admin.getInventory().setItem(7, null);
                    }
                    // 设置倒计时为 5 秒
                    cancelCountdown();
                    countdownTime = 5;
                    AtomicInteger timeLeft = new AtomicInteger(5);
                    countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                            plugin,
                            () -> {
                                int currentTime = timeLeft.getAndDecrement();
                                countdownTime = currentTime;

                                if (shouldAnnounce(currentTime)) {
                                    String message = ChatColor.translateAlternateColorCodes('&',
                                            "&e游戏将在 &c" + currentTime + " &e秒后开始！");
                                    Bukkit.broadcastMessage(message);
                                    playSoundToAllPlayers(Sound.NOTE_PLING);

                                    if (currentTime <= 5) {
                                        sendTitleToAllPlayers(currentTime);
                                    }
                                }

                                updateScoreboard();

                                if (currentTime <= 0) {
                                    if (waitingPlayers.size() >= GameManager.MIN_PLAYERS &&
                                            configManager.getSpawnPoints().size() >= GameManager.MIN_PLAYERS) {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            for (Player p : new HashSet<>(waitingPlayers)) {
                                                p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a正在传送至游戏位置..."));
                                            }
                                            gameManager.setState(new PlayingState(gameManager, configManager, plugin));
                                        });
                                    } else {
                                        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                                                "&c需要至少 " + GameManager.MIN_PLAYERS + " &c名玩家和 &4" + GameManager.MIN_PLAYERS + " &c个出生点才能开始游戏！"));
                                        cancelCountdown();
                                    }
                                }
                            },
                            0L,
                            20L
                    );
                } else {
                    // 给出不满足开始条件的提示消息
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&c需要至少 " + GameManager.MIN_PLAYERS + " &c名玩家和 &4" + GameManager.MIN_PLAYERS + " &c个出生点才能开始游戏！"));
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (adminPlayers.contains(player) && event.getSlot() == 7) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (adminPlayers.contains(player) && event.getRawSlots().contains(7)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (adminPlayers.contains(player) && player.getInventory().getHeldItemSlot() == 7) {
            event.setCancelled(true);
        }
    }

    @Override
    public void startItemGiveTask(GameManager gameManager, Set<UUID> alivePlayers, boolean gameStarted, int phase) {
        // 等待状态不发放物品，空实现
    }

    @Override
    public int getCurrentPhase() {
        // 等待状态可以自定义阶段，这里假设为 0
        return 0;
    }
}