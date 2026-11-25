package fr.worldfarm.sell;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class SellCommands implements CommandExecutor {

    private final Main plugin;

    public SellCommands(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (command.getName().equalsIgnoreCase("sell")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("sellshop.admin")) {
                    sender.sendMessage(plugin.format(plugin.getConfig().getString("messages.pas-permission"), null));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(plugin.msg("reload", null));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("joueur-seulement", null));
                return true;
            }
            plugin.menu().open(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("prix")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("joueur-seulement", null));
                return true;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                player.sendMessage(plugin.msg("main-vide", null));
                return true;
            }
            int price = plugin.prices().get(item.getType());
            if (price <= 0) {
                player.sendMessage(plugin.msg("prix-aucun", null));
                return true;
            }
            Component name = Component.translatable(item.getType().translationKey());
            Component message = plugin.msg("prix-objet", Map.of(
                    "prix", String.valueOf(price),
                    "quantite", String.valueOf(item.getAmount()),
                    "pile", String.valueOf((long) price * item.getAmount())));
            message = message.replaceText(TextReplacementConfig.builder()
                    .matchLiteral("{objet}").replacement(name).build());
            player.sendMessage(message);
            return true;
        }
        return false;
    }
}
