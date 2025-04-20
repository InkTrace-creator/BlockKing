package InkTrace.cn.blockking;

import org.bukkit.entity.Player;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class BungeeCord {
    public static void connect(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(
                player.getServer().getPluginManager().getPlugin("BlockKing"),
                "BungeeCord",
                out.toByteArray()
        );
    }
}