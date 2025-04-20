package InkTrace.cn.blockking.manager;

import InkTrace.cn.blockking.game.GameState;
import InkTrace.cn.blockking.game.WaitingState;
import InkTrace.cn.blockking.game.PlayingState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;
import java.util.Set;

public class GameManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private GameState currentState;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 12;

    public GameManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        setState(new WaitingState(this, configManager));
    }

    public void setState(GameState state) {
        Set<Player> waitingPlayers = null;
        if (state instanceof PlayingState && currentState instanceof WaitingState) {
            waitingPlayers = ((WaitingState) currentState).getWaitingPlayers();
            ((PlayingState) state).setInitialPlayers(new HashSet<>(waitingPlayers));
        }
        if (currentState != null) {
            currentState.onDisable();
        }
        currentState = state;
        currentState.onEnable();
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Set<Player> getWaitingPlayers() {
        if (currentState instanceof WaitingState) {
            return ((WaitingState) currentState).getWaitingPlayers();
        }
        return new HashSet<>();
    }

    // 添加代理方法
    public Set<Player> getAlivePlayers() {
        if (currentState instanceof PlayingState) {
            return ((PlayingState) currentState).getAlivePlayers();
        }
        return new HashSet<>();
    }

    public boolean isPlayerAlive(Player player) {
        if (currentState instanceof PlayingState) {
            return ((PlayingState) currentState).getAlivePlayers().contains(player);
        }
        return false;
    }
}