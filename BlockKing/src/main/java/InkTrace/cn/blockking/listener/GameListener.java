package InkTrace.cn.blockking.listener;

import InkTrace.cn.blockking.game.PlayingState;
import InkTrace.cn.blockking.game.WaitingState;
import InkTrace.cn.blockking.manager.GameManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {
    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!(gameManager.getCurrentState() instanceof WaitingState)) return;
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null &&
                    item.getType() == Material.BED &&
                    item.hasItemMeta() &&
                    item.getItemMeta().getDisplayName().equals("§c返回大厅 §7(右键)")) {
                if (((WaitingState) gameManager.getCurrentState()).handleBedClick(event.getPlayer())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(gameManager.getCurrentState() instanceof WaitingState)) return;
        if (event.getSlot() == 8 && event.getSlotType() == InventoryType.SlotType.QUICKBAR) {
            event.setCancelled(true);
            return;
        }
        if (event.getCurrentItem() != null &&
                event.getCurrentItem().getType() == Material.BED &&
                event.getCurrentItem().getItemMeta().getDisplayName().equals("§c返回大厅 §7(右键)")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!(gameManager.getCurrentState() instanceof WaitingState)) return;
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.getType() == Material.BED &&
                item.getItemMeta().getDisplayName().equals("§c返回大厅 §7(右键)")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (gameManager.getCurrentState() instanceof WaitingState) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        gameManager.getCurrentState().onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        gameManager.getCurrentState().onPlayerLeave(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String format;
        String prefix = "";
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                LuckPerms api = LuckPermsProvider.get();
                CachedMetaData metaData = api.getPlayerAdapter(Player.class).getMetaData(player);
                prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
            }
        } catch (IllegalStateException ignored) {}
        if (gameManager.getCurrentState() instanceof WaitingState ||
                gameManager.getCurrentState() instanceof PlayingState) {
            format = ChatColor.translateAlternateColorCodes('&',
                    prefix + player.getName() + " &7: &f") + "%2$s";
        } else {
            format = event.getFormat();
        }
        event.setFormat(format);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (gameManager.getCurrentState() instanceof PlayingState) {
            PlayingState playingState = (PlayingState) gameManager.getCurrentState();
            playingState.handleVoidDamage(event);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (gameManager.getCurrentState() instanceof PlayingState) {
            PlayingState playingState = (PlayingState) gameManager.getCurrentState();
            playingState.handleMove(event);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (gameManager.getCurrentState() instanceof PlayingState) {
            event.setDeathMessage(null); // 取消原版死亡消息
            Player deadPlayer = event.getEntity();
            Player killer = deadPlayer.getKiller();
            PlayingState playingState = (PlayingState) gameManager.getCurrentState();
            if (deadPlayer.getHealth() <= 0) {
                deadPlayer.setHealth(1);
                playingState.handlePlayerDeath(deadPlayer, killer);
            }
        }
    }
}