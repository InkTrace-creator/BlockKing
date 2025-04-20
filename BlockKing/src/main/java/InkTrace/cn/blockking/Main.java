package InkTrace.cn.blockking;

import InkTrace.cn.blockking.commands.BlockKingCommand;
import InkTrace.cn.blockking.game.PlayingState;
import InkTrace.cn.blockking.game.SpectatorState;
import InkTrace.cn.blockking.game.WaitingState;
import InkTrace.cn.blockking.handler.MapResetHandler;
import InkTrace.cn.blockking.manager.GameManager;
import InkTrace.cn.blockking.manager.ConfigManager;
import InkTrace.cn.blockking.listener.GameListener;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    private GameManager gameManager;
    private ConfigManager configManager;
    private MapResetHandler mapResetHandler;
    private SpectatorState spectatorState;

    @Override
    public void onEnable() {
        getLogger().info("BlockKing 插件已启用");

        WaitingState.setPlugin(this);
        PlayingState.setPlugin(this);
        // 先初始化 ConfigManager
        configManager = new ConfigManager(this);
        // 再初始化 MapResetHandler
        mapResetHandler = new MapResetHandler(this);
        // 初始化 GameManager
        gameManager = new GameManager(this, configManager);

        // 注册 GameManager 服务
        ServicesManager servicesManager = getServer().getServicesManager();
        servicesManager.register(GameManager.class, gameManager, this, org.bukkit.plugin.ServicePriority.Normal);

        // 使用已初始化的 configManager 和 gameManager 初始化 SpectatorState
        spectatorState = new SpectatorState(configManager, gameManager, this);
        getServer().getPluginManager().registerEvents(spectatorState, this);

        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getCommand("blockking").setExecutor(new BlockKingCommand(gameManager, configManager));
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("BlockKing 插件已禁用");
        mapResetHandler.restoreBlocksOnDisable();
    }
}