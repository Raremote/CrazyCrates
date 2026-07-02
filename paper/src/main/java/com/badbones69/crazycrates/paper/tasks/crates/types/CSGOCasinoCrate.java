package com.badbones69.crazycrates.paper.tasks.crates.types;

import com.badbones69.common.config.impl.ConfigKeys;
import com.badbones69.crazycrates.paper.api.PrizeManager;
import com.badbones69.crazycrates.paper.api.builders.CrateBuilder;
import com.badbones69.crazycrates.paper.api.builders.types.features.CrateSpinMenu;
import com.badbones69.crazycrates.paper.api.enums.other.keys.FileKeys;
import com.badbones69.crazycrates.paper.api.enums.other.keys.ItemKeys;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.Prize;
import com.badbones69.crazycrates.paper.api.objects.Tier;
import com.badbones69.crazycrates.paper.api.objects.gui.GuiSettings;
import com.badbones69.crazycrates.paper.managers.BukkitUserManager;
import com.badbones69.crazycrates.paper.managers.events.enums.EventType;
import com.badbones69.crazycrates.paper.tasks.crates.CrateManager;
import com.badbones69.crazycrates.paper.utils.MiscUtils;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import com.ryderbelserion.fusion.paper.builders.items.ItemBuilder;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import us.crazycrew.crazycrates.api.enums.types.KeyType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CSGOCasinoCrate extends CrateBuilder {

    public CSGOCasinoCrate(@NotNull final Crate crate, @NotNull final Player player, final int size) {
        super(crate, player, size);
    }

    private final Inventory inventory = getInventory();
    private final Player player = getPlayer();
    private final UUID uuid = this.player.getUniqueId();
    private final Crate crate = getCrate();

    @Override
    public void open(@NotNull final KeyType type, final boolean checkHand, final boolean isSilent, final int amount, @NotNull final EventType eventType) {
        final String fileName = this.crate.getFileName();

        // Crate event failed, so we return.
        if (isCrateEventValid(type, checkHand, isSilent, amount, eventType, event -> {
            final ConfigurationSection section = this.crate.getSection().getConfigurationSection("random");

            if (section != null) {
                final boolean isRandom = section.getBoolean("toggle", false);

                if (!isRandom) {
                    final Tier tier_uno = this.crate.getTier(section.getString("types.row-3", ""));
                    final Tier tier_dos = this.crate.getTier(section.getString("types.row-2", ""));
                    final Tier tier_tres = this.crate.getTier(section.getString("types.row-1", ""));

                    if (tier_uno == null || tier_dos == null || tier_tres == null) {
                        this.fusion.log(Level.WARNING, "One of your tiers in %s could not be found, or is empty. Search for row-1, row-2 or row-3", fileName);

                        this.crateManager.endCrate(this.crate, this.player);

                        this.player.closeInventory();

                        event.setCancelled(true);

                        return;
                    }
                }
            }

            if (!this.userManager.takeKeys(this.uuid, fileName, type, amount, checkHand)) {
                this.crateManager.endCrate(this.crate, this.player);

                event.setCancelled(true);
            }
        })) {
            return;
        }

        final ConfigurationSection section = this.crate.getSection().getConfigurationSection("random");

        if (section != null) {
            final boolean isRandom = section.getBoolean("toggle", false);

            if (!isRandom) {
                final @Nullable Tier tier_uno = this.crate.getTier(section.getString("types.row-3", ""));
                final @Nullable Tier tier_dos = this.crate.getTier(section.getString("types.row-2", ""));
                final @Nullable Tier tier_tres = this.crate.getTier(section.getString("types.row-1", ""));

                if (tier_uno == null || tier_dos == null || tier_tres == null) {
                    this.fusion.log(Level.WARNING, "One of your tiers in %s could not be found, or is empty. Search for row-1, row-2 or row-3", fileName);

                    this.crateManager.endCrate(this.player);

                    this.crateManager.removeCrateTask(this.player);

                    this.crateManager.removePlayerFromOpeningList(this.player);

                    this.player.closeInventory();

                    return;
                }
            }
        }

        final boolean keyCheck = this.userManager.takeKeys(this.uuid, fileName, type, this.crate.useRequiredKeys() ? this.crate.getRequiredKeys() : 1, checkHand);

        if (!keyCheck) {
            // Remove from opening list.
            this.crateManager.removePlayerFromOpeningList(this.player);

            return;
        }

        @Nullable final Tier tierUno = this.crate.getTier(section.getString("types.row-1", ""));
        @Nullable final Tier tierDos = this.crate.getTier(section.getString("types.row-2", ""));
        @Nullable final Tier tierTres = this.crate.getTier(section.getString("types.row-3", ""));

        final int timeAddition = this.crate.getSection().getInt("random-time-addition");

        // Set the glass/display items to the inventory.
        populate(tierUno, tierDos, tierTres);

        // Open the inventory.
        this.player.openInventory(this.inventory);

        // Adds to total end time of animation
        int randomTimeAddition = (int) (Math.random() * (timeAddition+1));

        addCrateTask(new FoliaScheduler(this.plugin, null, this.player) {
            int time = 1;

            int full = 0;

            int open = 0;

            @Override
            public void run() {
                if (this.full <= 50) { // When Spinning
                    moveItemsAndSetGlass(tierUno, tierDos, tierTres);

                    playSound("cycle-sound", Sound.Source.MASTER, "block.note_block.xylophone");
                }

                this.open++;

                if (this.open >= 5) {
                    player.openInventory(inventory);

                    this.open = 0;
                }

                this.full++;

                if (this.full > 51) {
                    if (MiscUtils.slowSpin(120, 15).contains(this.time)) { // When Slowing Down
                        moveItemsAndSetGlass(tierUno, tierDos, tierTres);

                        playSound("cycle-sound", Sound.Source.MASTER, "block.note_block.xylophone");
                    }

                    this.time++;

                    if (this.time == 60+randomTimeAddition) { // When done
                        playSound("stop-sound", Sound.Source.MASTER, "entity.player.levelup");

                        crateManager.endCrate(player);

                        PrizeManager.getPrize(crate, inventory, 13, player);
                        PrizeManager.getPrize(crate, inventory, 22, player);
                        PrizeManager.getPrize(crate, inventory, 31, player);

                        crateManager.removePlayerFromOpeningList(player);

                        new FoliaScheduler(plugin, null, player) {
                            @Override
                            public void run() { //todo() use inventory holders
                                if (player.getOpenInventory().getTopInventory().equals(inventory)) player.closeInventory();
                            }
                        }.runDelayed(40);

                        cancel();
                    }
                    if (this.time > 60+randomTimeAddition) { // Added this due reports of the prizes spamming when low tps.
                        cancel();
                    }
                }
            }
        }.runAtFixedRate(0, 1));
    }

    private void populate(Tier tierUno, Tier tierDos, Tier tierTres) {
        if (this.crate.isGlassBorderToggled()) {
            getBorder().forEach(this::setCustomGlassPane);
        }

        final String material = this.config.getProperty(ConfigKeys.crate_csgo_cycling_material);

        if (!material.isEmpty()) {
            final ItemStack itemStack = ItemBuilder.from(material).withDisplayName(" ").asItemStack();

            setItem(4, itemStack);
            setItem(40, itemStack);
        }

        // Set display items.
        for (int index = 9; index > 8 && index < 18; index++) {
            setItem(index, getDisplayItem(tierUno));
        }
        for (int index = 18; index > 17 && index < 27; index++) {
            setItem(index, getDisplayItem(tierDos));
        }
        for (int index = 27; index > 26 && index < 36; index++) {
            setItem(index, getDisplayItem(tierTres));
        }
    }

    private void moveItemsAndSetGlass(Tier tierUno, Tier tierDos, Tier tierTres) {
        final List<ItemStack> tierUnoitems = new ArrayList<>();
        final List<ItemStack> tierDositems = new ArrayList<>();
        final List<ItemStack> tierTresitems = new ArrayList<>();

        for (int i = 9; i > 8 && i < 17; i++) {
            tierUnoitems.add(this.inventory.getItem(i));
        }
        for (int i = 18; i > 17 && i < 26; i++) {
            tierDositems.add(this.inventory.getItem(i));
        }
        for (int i = 27; i > 26 && i < 35; i++) {
            tierTresitems.add(this.inventory.getItem(i));
        }

        setItem(9, getDisplayItem(tierUno));
        setItem(18, getDisplayItem(tierDos));
        setItem(27, getDisplayItem(tierTres));

        for (int i = 0; i < 8; i++) {
            setItem(i + 10, tierUnoitems.get(i));
            setItem(i+9 + 10, tierDositems.get(i));
            setItem(i+18 + 10, tierTresitems.get(i));
        }

        if (this.crate.isGlassBorderToggled()) {
            getBorder().forEach(this::setCustomGlassPane);
        }
    }

    private List<Integer> getBorder() {
        final String material = this.config.getProperty(ConfigKeys.crate_csgo_cycling_material);

        if (!material.isEmpty()) {
            return Arrays.asList(0, 1, 2, 3, 5, 6, 7, 8, 36, 37, 38, 39, 41, 42, 43, 44);
        }

        return Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 36, 37, 38, 39, 40, 41, 42, 43, 44);
    }

}