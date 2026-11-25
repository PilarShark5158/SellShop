package fr.worldfarm.sell;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class Main extends JavaPlugin {

    private Economy economy;
    private PriceManager prices;
    private ItemLore lore;
    private SellMenu menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "prices.yml").exists()) {
            saveResource("prices.yml", false);
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Aucune économie Vault trouvée (MoneyEconomy est-il installé ?). Le plugin se désactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();

        prices = new PriceManager(this);
        prices.load();
        lore = new ItemLore(this);
        menu = new SellMenu(this);
        getServer().getPluginManager().registerEvents(menu, this);

        SellCommands commands = new SellCommands(this);
        getCommand("sell").setExecutor(commands);
        getCommand("prix").setExecutor(commands);
    }

    @Override
    public void onDisable() {
        // Rend les objets des joueurs qui ont un menu ouvert
        if (menu != null) {
            menu.closeAll();
        }
    }

    public void reloadAll() {
        reloadConfig();
        prices.load();
    }

    public Economy economy() { return economy; }
    public PriceManager prices() { return prices; }
    public ItemLore lore() { return lore; }
    public SellMenu menu() { return menu; }

    /** Message de la section "messages" du config.yml. */
    public Component msg(String key, Map<String, String> placeholders) {
        String raw = getConfig().getString("messages." + key, "&c[message manquant : " + key + "]");
        return format(raw, placeholders);
    }

    /** Convertit un texte en & avec placeholders en Component (sans italique par défaut). */
    public Component format(String raw, Map<String, String> placeholders) {
        raw = raw.replace("{prefix}", getConfig().getString("messages.prefix", ""))
                 .replace("{devise}", getConfig().getString("devise", "$"));
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
                .decoration(TextDecoration.ITALIC, false);
    }
}
