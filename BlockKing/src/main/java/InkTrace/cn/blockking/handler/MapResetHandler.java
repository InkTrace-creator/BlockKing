package InkTrace.cn.blockking.handler;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class MapResetHandler implements Listener {
    private final Map<Location, Material> originalBlocks = new HashMap<>();

    public MapResetHandler(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        originalBlocks.put(location, event.getBlock().getType());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Location location = event.getBlock().getLocation();
        originalBlocks.put(location, event.getBlockReplacedState().getType());
    }

    /**
     * 插件卸载时恢复方块
     */
    public void restoreBlocksOnDisable() {
        for (Map.Entry<Location, Material> entry : originalBlocks.entrySet()) {
            Location location = entry.getKey();
            Material originalMaterial = entry.getValue();
            Block block = location.getBlock();
            block.setType(originalMaterial);
        }
        originalBlocks.clear();
    }
}    