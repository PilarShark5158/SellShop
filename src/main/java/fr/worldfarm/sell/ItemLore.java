package fr.worldfarm.sell;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ajoute / retire les lignes de prix sur les objets affichés dans le menu /sell.
 * Le nombre de lignes ajoutées est mémorisé dans l'objet pour pouvoir les retirer proprement.
 */
public final class ItemLore {

    private final Main plugin;
    private final NamespacedKey key;

    public ItemLore(Main plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "sell_lore_lines");
    }

    /** Retire les lignes de prix. Retourne true si l'objet a été modifié. */
    public boolean strip(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer lines = pdc.get(key, PersistentDataType.INTEGER);
        if (lines == null) return false;

        List<Component> lore = meta.lore();
        if (lore != null) {
            int newSize = Math.max(0, lore.size() - lines);
            List<Component> kept = new ArrayList<>(lore.subList(0, newSize));
            meta.lore(kept.isEmpty() ? null : kept);
        }
        pdc.remove(key);
        item.setItemMeta(meta);
        return true;
    }

    /** Recalcule les lignes de prix (si l'objet est vendable). */
    public void apply(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        strip(item);

        int price = plugin.prices().get(item.getType());
        if (price <= 0) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        Map<String, String> ph = Map.of(
                "prix", String.valueOf(price),
                "pile", String.valueOf((long) price * item.getAmount()));

        List<String> lines = plugin.getConfig().getStringList("lore-prix");
        for (String line : lines) {
            lore.add(plugin.format(line, ph));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, lines.size());
        item.setItemMeta(meta);
    }
}
