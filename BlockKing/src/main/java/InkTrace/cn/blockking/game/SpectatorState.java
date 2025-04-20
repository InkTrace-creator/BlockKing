package InkTrace.cn.blockking.game;

import InkTrace.cn.blockking.manager.ConfigManager;
import InkTrace.cn.blockking.manager.GameManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.util.*;
import java.util.stream.Collectors;

public class SpectatorState implements Listener, PluginMessageListener {
    private final ConfigManager configManager;
    private final Map<UUID, Long> clickCooldown = new HashMap<>();
    private final Plugin plugin;
    private final GameManager gameManager;
    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Map<UUID, Boolean> isTeleportPromptSent = new HashMap<>();
    private final Set<UUID> spectatorPlayers = new HashSet<>();

    public SpectatorState(ConfigManager configManager, GameManager gameManager, Plugin plugin) {
        this.configManager = configManager;
        this.plugin = plugin;
        this.gameManager = gameManager;
        registerChannelsAndEvents();
    }

    private void registerChannelsAndEvents() {
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        Bukkit.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------ 核心状态管理 ------------------------
    public void setPlayerAsSpectator(Player player) {
        if (spectatorPlayers.contains(player.getUniqueId())) return;

        spectatorPlayers.add(player.getUniqueId());
        applySpectatorEffects(player);
        setupSpectatorInventory(player);
        showSpectatorTitle(player);

        player.setPlayerListName(""); // 从TAB列表隐藏
        player.setAllowFlight(true); // 允许飞行，但不强制开启
        player.setVelocity(player.getVelocity().setY(0));
    }

    private void applySpectatorEffects(Player player) {
        // 隐身效果（无粒子）
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        // 清除其他可能影响的效果
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOW);
    }

    private void showSpectatorTitle(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&', "&c&l淘汰");
        String subtitle = ChatColor.translateAlternateColorCodes('&', "&7处于旁观状态");
        player.sendTitle(title, subtitle);
    }

    // ------------------------ 物品栏管理 ------------------------
    private void setupSpectatorInventory(Player player) {
        // 指南针（第1格）
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compass.getItemMeta();
        compassMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a玩家 &7(右键)"));
        compass.setItemMeta(compassMeta);

        // 床（最后一格，索引35）
        ItemStack bed = new ItemStack(Material.BED);
        ItemMeta bedMeta = bed.getItemMeta();
        bedMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&c返回大厅 &7(右键)"));
        bed.setItemMeta(bedMeta);

        player.getInventory().setItem(0, compass);          // 设置指南针到第1格
        player.getInventory().setItem(35, bed);             // 设置床到最后一格（35格）
        player.getInventory().setHeldItemSlot(0);           // 强制手持第一格物品
    }

    // ------------------------ 交互逻辑 ------------------------
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!isSpectator(player)) return;

        // 阻止移动指南针和床的位置
        if (event.getSlot() == 0 || event.getSlot() == 35) {
            event.setCancelled(true);
            player.updateInventory(); // 立即更新物品栏显示
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!isSpectator(player)) return;

        // 阻止拖拽指南针和床的槽位
        if (event.getRawSlots().contains(0) || event.getRawSlots().contains(35)) {
            event.setCancelled(true);
            player.updateInventory(); // 防止拖拽后显示异常
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isSpectator(player)) return;
        if (clickCooldown.containsKey(player.getUniqueId()) &&
                System.currentTimeMillis() - clickCooldown.get(player.getUniqueId()) < 1000) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) return;

        Action action = event.getAction();
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && item.getType() == Material.COMPASS) {
            clickCooldown.put(player.getUniqueId(), System.currentTimeMillis());
            openAlivePlayerGui(player);
        } else if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && item.getType() == Material.BED) {
            clickCooldown.put(player.getUniqueId(), System.currentTimeMillis());
            handleBedClick(player);
        }
    }

    private void openAlivePlayerGui(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 18, ChatColor.GOLD + "存活玩家列表");

        // 获取游戏中的存活玩家
        List<Player> alivePlayers = gameManager.getAlivePlayers().stream()
                .filter(Objects::nonNull)
                .filter(p -> p.isOnline())
                .collect(Collectors.toList());

        for (Player p : alivePlayers) {
            ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwner(p.getName());
            meta.setDisplayName(ChatColor.YELLOW + p.getName());
            skull.setItemMeta(meta);
            inventory.addItem(skull);
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryPlayerClick(InventoryClickEvent event) {
        Player viewer = (Player) event.getWhoClicked();
        if (!isSpectator(viewer) || !event.getInventory().getTitle().equals(ChatColor.GOLD + "存活玩家列表")) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.SKULL_ITEM) {
            return;
        }

        if (event.getClick().isLeftClick()) {
            String owner = ((SkullMeta) clickedItem.getItemMeta()).getOwner();
            Player target = Bukkit.getPlayer(owner);
            if (target != null && gameManager.isPlayerAlive(target)) {
                viewer.teleport(target.getLocation());
                viewer.closeInventory();
            }
        }
    }

    // ------------------------ 状态维护 ------------------------
    private boolean isSpectator(Player player) {
        return spectatorPlayers.contains(player.getUniqueId());
    }

    public void removePlayerFromSpectator(Player player) {
        if (!spectatorPlayers.remove(player.getUniqueId())) return;

        player.setPlayerListName(player.getName());
        player.setAllowFlight(false); // 取消飞行权限
        player.setFlying(false);      // 关闭飞行状态
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        // 清空特殊物品
        player.getInventory().setItem(0, null);
        player.getInventory().setItem(35, null);
    }

    // ------------------------ 物理限制 ------------------------
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && isSpectator((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isSpectator(player)) return;

        Location location = player.getLocation();
        if (location.getY() < 0) { // 处理虚空
            Location spawn = configManager.getSpectatorSpawn();
            player.teleport(spawn != null ? spawn : player.getWorld().getSpawnLocation());
        }

        // 限制垂直移动速度，防止自动上升
        player.setVelocity(player.getVelocity().setY(Math.min(player.getVelocity().getY(), 0.1)));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // 保留BungeeCord消息处理逻辑
    }

    // ------------------------ 传送逻辑（与之前兼容） ------------------------
    private void handleBedClick(Player player) {
        ItemStack item = player.getInventory().getItem(35);
        if (item == null || !item.hasItemMeta() ||
                !item.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', "&c返回大厅 &7(右键)"))) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (cancelPendingTeleport(player)) {
            player.sendMessage(ChatColor.RED + "传送已取消");
            return;
        }

        sendTeleportPrompt(player);
        scheduleTeleport(player);
    }

    private void sendTeleportPrompt(Player player) {
        player.sendMessage(ChatColor.GREEN + "5秒后返回大厅，再次右键取消");
        isTeleportPromptSent.put(player.getUniqueId(), true);
    }

    private void scheduleTeleport(Player player) {
        pendingTeleports.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            performTeleport(player);
            removePlayerFromSpectator(player);
        }, 100)); // 5秒延迟（20tick=1秒）
    }

    private boolean cancelPendingTeleport(Player player) {
        UUID playerId = player.getUniqueId();
        if (pendingTeleports.containsKey(playerId)) {
            pendingTeleports.get(playerId).cancel();
            pendingTeleports.remove(playerId);
            isTeleportPromptSent.remove(playerId);
            return true;
        }
        return false;
    }

    private void performTeleport(Player player) {
        String lobbyServer = configManager.getLobbyServerName();
        if (lobbyServer == null) {
            player.sendMessage(ChatColor.RED + "大厅服务器配置错误");
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(lobbyServer);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }
}