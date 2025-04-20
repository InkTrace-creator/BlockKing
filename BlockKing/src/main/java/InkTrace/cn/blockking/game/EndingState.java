package InkTrace.cn.blockking.game;

import InkTrace.cn.blockking.manager.GameManager;
import org.bukkit.Bukkit;
import InkTrace.cn.blockking.manager.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.Set;
import java.util.UUID;

public class EndingState implements GameState {
    private final GameManager gameManager;
    private final ConfigManager configManager;

    public EndingState(GameManager gameManager, ConfigManager configManager) {
        this.gameManager = gameManager;
        this.configManager = configManager;
    }

    @Override
    public void onEnable() {
        String mapName = configManager.getMapName();
        Bukkit.broadcastMessage(ChatColor.GOLD + "本次游戏地图: " + ChatColor.AQUA + mapName);

        Bukkit.getScheduler().runTaskLater(gameManager.getPlugin(), () -> {
            gameManager.setState(new WaitingState(gameManager, configManager));
            // 关闭服务器，由外部工具重启
            Bukkit.getServer().shutdown();
        }, 20 * 5);
    }

    @Override
    public void onDisable() {
        gameManager.getPlugin().getLogger().info("清理结束状态");
    }

    @Override
    public void onPlayerJoin(Player player) {
        player.sendMessage("游戏正在结算，请稍后再加入!");
    }

    @Override
    public void onPlayerLeave(Player player) {
        player.sendMessage("感谢参与 BlockKing 游戏!");
    }

    @Override
    public void startItemGiveTask(GameManager gameManager, Set<UUID> alivePlayers, boolean gameStarted, int phase) {
        // 结束状态不发放物品，空实现
    }

    @Override
    public int getCurrentPhase() {
        // 结束状态可以自定义阶段，这里假设为 3
        return 3;
    }
}