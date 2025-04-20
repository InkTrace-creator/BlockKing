package InkTrace.cn.blockking.game;

import InkTrace.cn.blockking.manager.GameManager;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public interface GameState {
    void onEnable();
    void onDisable();
    void onPlayerJoin(Player player);
    void onPlayerLeave(Player player);
    int getCurrentPhase();
    // 随机给物品功能统一由 GameState 接口声明
    void startItemGiveTask(GameManager gameManager, Set<UUID> alivePlayers, boolean gameStarted, int phase);
}