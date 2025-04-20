package InkTrace.cn.blockking.commands;

import InkTrace.cn.blockking.manager.GameManager;
import InkTrace.cn.blockking.manager.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.Arrays;

public class BlockKingCommand implements CommandExecutor {
    private final GameManager gameManager;
    private final ConfigManager configManager;

    public BlockKingCommand(GameManager gameManager, ConfigManager configManager) {
        this.gameManager = gameManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "可用命令:");
            if (sender.hasPermission("blockking.admin")) {
                sender.sendMessage(ChatColor.GOLD + "/blockking setlobby - 设置等待大厅位置");
                sender.sendMessage(ChatColor.GOLD + "/blockking setmap <名称> - 设置地图名称");
                sender.sendMessage(ChatColor.GOLD + "/blockking addspawn [编号] - 添加出生点");
                sender.sendMessage(ChatColor.GOLD + "/blockking setspectatorspawn - 设置旁观者出生点");
            }
            return true;
        }

        // 设置等待大厅位置
        if (args[0].equalsIgnoreCase("setlobby") && sender.hasPermission("blockking.admin")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "只有玩家可以设置等待大厅位置!");
                return true;
            }

            Player player = (Player) sender;
            configManager.setLobbyLocation(player.getLocation());
            sender.sendMessage(ChatColor.GREEN + "成功设置等待大厅位置!");
            return true;
        }

        // 设置地图名称
        if (args[0].equalsIgnoreCase("setmap") && sender.hasPermission("blockking.admin")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "用法: /blockking setmap <地图名称>");
                return true;
            }

            String mapName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            configManager.setMapName(mapName);
            sender.sendMessage(ChatColor.GREEN + "成功设置地图名称为: " + ChatColor.YELLOW + mapName);
            return true;
        }

        // 添加出生点
        if (args[0].equalsIgnoreCase("addspawn") && sender.hasPermission("blockking.admin")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "只有玩家可以设置出生点!");
                return true;
            }

            Player player = (Player) sender;
            Location location = player.getLocation();
            int spawnIndex = configManager.getSpawnPoints().size() + 1;
            if (args.length >= 2) {
                try {
                    spawnIndex = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "出生点编号必须是数字!");
                    return true;
                }
            }

            configManager.addSpawnPoint(location);
            sender.sendMessage(ChatColor.GREEN + "成功设置出生点 #" + spawnIndex);
            return true;
        }

        // 设置旁观者出生点
        if (args[0].equalsIgnoreCase("setspectatorspawn") && sender.hasPermission("blockking.admin")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "只有玩家可以设置旁观者出生点!");
                return true;
            }

            Player player = (Player) sender;
            configManager.setSpectatorSpawn(player.getLocation());
            sender.sendMessage(ChatColor.GREEN + "成功设置旁观者出生点!");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "未知命令! 可用命令:");
        if (sender.hasPermission("blockking.admin")) {
            sender.sendMessage(ChatColor.GOLD + "/blockking setlobby - 设置等待大厅位置");
            sender.sendMessage(ChatColor.GOLD + "/blockking setmap <名称> - 设置地图名称");
            sender.sendMessage(ChatColor.GOLD + "/blockking addspawn [编号] - 添加出生点");
            sender.sendMessage(ChatColor.GOLD + "/blockking setspectatorspawn - 设置旁观者出生点");
        }
        return true;
    }
}