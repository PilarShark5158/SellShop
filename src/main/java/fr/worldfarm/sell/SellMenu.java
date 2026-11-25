package fr.worldfarm.sell;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class SellMenu implements Listener {

    /** Slots 0 à 44 : zone de dépôt. Slots 45 à 53 : barre du bas. */
    private static final int ZONE = 45;
    private static final int SELL_SLOT = 53;

    private final Main plugin;

    public SellMenu(Main plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ holders

    public static final class SellHolder implements InventoryHolder {
        private Inventory inventory;
        private boolean done;

        @Override
        public Inventory getInventory() { return inventory; }
    }

    public static final class ConfirmHolder implements InventoryHolder {
        private Inventory inventory;
        private boolean done;
        private final List<ItemStack> items;

        private ConfirmHolder(List<ItemStack> items) { this.items = items; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    // ------------------------------------------------------------------ ouverture

    public void open(Player player) {
        SellHolder holder = new SellHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.format(plugin.getConfig().getString("menu.titre-vente", "&8Vendre des objets"), null));
        holder.inventory = inv;

        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, Component.space());
        for (int i = ZONE; i < 53; i++) {
            inv.setItem(i, pane);
        }
        inv.setItem(SELL_SLOT, sellButton(0, 0));
        player.openInventory(inv);
    }

    private void openConfirm(Player player, List<ItemStack> items) {
        long total = 0;
        int count = 0;
        for (ItemStack it : items) {
            int price = plugin.prices().get(it.getType());
            if (price > 0) {
                total += (long) price * it.getAmount();
                count += it.getAmount();
            }
        }

        ConfirmHolder holder = new ConfirmHolder(items);
        Inventory inv = Bukkit.createInventory(holder, 27,
                plugin.format(plugin.getConfig().getString("menu.titre-confirmation", "&8Confirmer la vente ?"), null));
        holder.inventory = inv;

        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, Component.space());
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        Map<String, String> ph = Map.of("total", String.valueOf(total), "items", String.valueOf(count));
        inv.setItem(11, button(Material.LIME_CONCRETE, "menu.confirmer-nom", "menu.confirmer-lore", ph));
        inv.setItem(13, button(Material.GOLD_INGOT, "menu.info-nom", "menu.info-lore", ph));
        inv.setItem(15, button(Material.RED_CONCRETE, "menu.annuler-nom", "menu.annuler-lore", ph));
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = e.getView().getTopInventory().getHolder();

        if (holder instanceof SellHolder sell) {
            if (sell.done) {
                e.setCancelled(true);
                return;
            }
            int raw = e.getRawSlot();
            if (raw >= ZONE && raw < 54) {
                e.setCancelled(true);
                if (raw == SELL_SLOT && (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.RIGHT
                        || e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT)) {
                    sellFromMenu(player, sell);
                }
                return;
            }
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            scheduleRefresh(player, sell);

        } else if (holder instanceof ConfirmHolder confirm) {
            e.setCancelled(true);
            if (confirm.done || e.getClickedInventory() != e.getView().getTopInventory()) return;

            if (e.getRawSlot() == 11) {
                confirm.done = true;
                sellItems(player, confirm.items);
                closeLater(player);
            } else if (e.getRawSlot() == 15) {
                confirm.done = true;
                giveBack(player, confirm.items);
                player.sendMessage(plugin.msg("annule", null));
                closeLater(player);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = e.getView().getTopInventory().getHolder();

        if (holder instanceof SellHolder sell) {
            for (int raw : e.getRawSlots()) {
                if (raw >= ZONE && raw < 54) {
                    e.setCancelled(true);
                    return;
                }
            }
            scheduleRefresh(player, sell);
        } else if (holder instanceof ConfirmHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = e.getInventory().getHolder();

        if (holder instanceof SellHolder sell) {
            if (sell.done) return;
            sell.done = true;

            List<ItemStack> items = takeItems(sell.inventory);
            stripPlayerLater(player);
            if (items.isEmpty()) return;

            boolean sellable = items.stream().anyMatch(it -> plugin.prices().get(it.getType()) > 0);
            boolean escape = e.getReason() == InventoryCloseEvent.Reason.PLAYER;

            if (escape && sellable && plugin.isEnabled()) {
                // Échap / touche d'inventaire : on demande confirmation avant de vendre
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        openConfirm(player, items);
                    } else {
                        giveBack(player, items);
                    }
                });
            } else {
                giveBack(player, items);
            }

        } else if (holder instanceof ConfirmHolder confirm) {
            if (confirm.done) return;
            confirm.done = true;
            // Fermer la confirmation sans choisir = annuler
            giveBack(player, confirm.items);
            player.sendMessage(plugin.msg("annule", null));
        }
    }

    // ------------------------------------------------------------------ ventes

    private void sellFromMenu(Player player, SellHolder sell) {
        long[] state = compute(sell.inventory);
        if (state[1] == 0) {
            player.sendMessage(plugin.msg("rien-a-vendre", null));
            return;
        }
        sell.done = true;
        List<ItemStack> items = takeItems(sell.inventory);
        sellItems(player, items);
        closeLater(player);
    }

    /** Vend les objets vendables, rend les autres au joueur. */
    private void sellItems(Player player, List<ItemStack> items) {
        long total = 0;
        int count = 0;
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack it : items) {
            int price = plugin.prices().get(it.getType());
            if (price > 0) {
                total += (long) price * it.getAmount();
                count += it.getAmount();
            } else {
                rest.add(it);
            }
        }

        if (count == 0) {
            giveBack(player, items);
            player.sendMessage(plugin.msg("rien-a-vendre", null));
            return;
        }
        if (total > Integer.MAX_VALUE) {
            giveBack(player, items);
            player.sendMessage(plugin.msg("vente-trop-grosse", null));
            return;
        }

        EconomyResponse response = plugin.economy().depositPlayer(player, total);
        if (!response.transactionSuccess()) {
            giveBack(player, items);
            player.sendMessage(plugin.msg("erreur-economie", null));
            return;
        }

        giveBack(player, rest);
        player.sendMessage(plugin.msg("vente",
                Map.of("items", String.valueOf(count), "total", String.valueOf(total))));
    }

    // ------------------------------------------------------------------ utilitaires

    /** Retourne {total, nombre d'objets vendables} de la zone de dépôt. */
    private long[] compute(Inventory inv) {
        long total = 0;
        long count = 0;
        for (int i = 0; i < ZONE; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            int price = plugin.prices().get(it.getType());
            if (price > 0) {
                total += (long) price * it.getAmount();
                count += it.getAmount();
            }
        }
        return new long[]{total, count};
    }

    /** Vide la zone de dépôt et retourne les objets (sans les lignes de prix). */
    private List<ItemStack> takeItems(Inventory inv) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < ZONE; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            plugin.lore().strip(it);
            list.add(it.clone());
            inv.setItem(i, null);
        }
        return list;
    }

    private void giveBack(Player player, Collection<ItemStack> items) {
        for (ItemStack it : items) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(it);
            for (ItemStack l : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), l);
            }
        }
    }

    private void scheduleRefresh(Player player, SellHolder sell) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!sell.done && player.isOnline()) refresh(player, sell);
        });
    }

    /** Met à jour les lignes de prix, le bouton "Vendre" et nettoie l'inventaire du joueur. */
    private void refresh(Player player, SellHolder sell) {
        Inventory inv = sell.inventory;
        long total = 0;
        long count = 0;
        for (int i = 0; i < ZONE; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            plugin.lore().apply(it);
            inv.setItem(i, it);
            int price = plugin.prices().get(it.getType());
            if (price > 0) {
                total += (long) price * it.getAmount();
                count += it.getAmount();
            }
        }
        inv.setItem(SELL_SLOT, sellButton(total, count));
        stripPlayer(player);
    }

    private void stripPlayerLater(Player player) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) stripPlayer(player);
        });
    }

    /** Retire les lignes de prix des objets qui ont quitté le menu. */
    private void stripPlayer(Player player) {
        PlayerInventory pi = player.getInventory();
        for (int i = 0; i < pi.getSize(); i++) {
            ItemStack it = pi.getItem(i);
            if (it != null && plugin.lore().strip(it)) {
                pi.setItem(i, it);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (plugin.lore().strip(cursor)) {
            player.setItemOnCursor(cursor);
        }
    }

    private void closeLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
    }

    /** Ferme tous les menus ouverts (arrêt du plugin) : les objets sont rendus. */
    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof SellHolder || holder instanceof ConfirmHolder) {
                p.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------------ items d'interface

    private ItemStack sellButton(long total, long count) {
        return button(Material.GOLD_INGOT, "menu.bouton-nom", "menu.bouton-lore",
                Map.of("total", String.valueOf(total), "items", String.valueOf(count)));
    }

    private ItemStack button(Material material, String namePath, String lorePath, Map<String, String> ph) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.format(plugin.getConfig().getString(namePath, ""), ph));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList(lorePath)) {
            lore.add(plugin.format(line, ph));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
