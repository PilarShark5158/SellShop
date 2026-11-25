package fr.worldfarm.sell;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public final class PriceManager {

    private final Main plugin;
    private final Map<Material, Integer> prices = new EnumMap<>(Material.class);

    public PriceManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        prices.clear();
        File file = new File(plugin.getDataFolder(), "prices.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("prix");
        if (section == null) {
            plugin.getLogger().warning("prices.yml : section 'prix' introuvable.");
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("prices.yml : objet inconnu '" + key + "'.");
                continue;
            }
            int price = section.getInt(key);
            if (price > 0) {
                prices.put(material, price);
            }
        }
        plugin.getLogger().info(prices.size() + " objets vendables chargés.");
    }

    /** Prix à la pièce, 0 si l'objet n'est pas vendable. */
    public int get(Material material) {
        return prices.getOrDefault(material, 0);
    }
}
