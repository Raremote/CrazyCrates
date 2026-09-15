package com.badbones69.crazycrates.paper.commands.crates.types.admin.keys;

import us.crazycrew.crazycrates.api.enums.messages.Message;
import com.badbones69.crazycrates.paper.api.PrizeManager;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.managers.events.EventManager;
import com.badbones69.crazycrates.paper.managers.events.enums.EventType;
import com.badbones69.crazycrates.paper.utils.MiscUtils;
import com.badbones69.crazycrates.paper.commands.crates.types.BaseCommand;
import com.ryderbelserion.fusion.core.api.enums.Level;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.annotations.ArgName;
import dev.triumphteam.cmd.core.annotations.Command;
import dev.triumphteam.cmd.core.annotations.Suggestion;
import dev.triumphteam.cmd.core.annotations.Syntax;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import us.crazycrew.crazycrates.api.enums.types.KeyType;
import com.badbones69.common.config.impl.ConfigKeys;
import java.util.Map;
import java.util.UUID;

public class CommandOpen extends BaseCommand {

    private boolean isCancelled(final Player player, final String crateName) {
        if (crateName == null || crateName.isBlank()) {
            Message.cannot_be_empty.sendMessage(player, "{value}", "crate name");

            return true;
        }

        // Check if player is in opening list first.
        if (this.crateManager.isInOpeningList(player)) {
            Message.crate_already_opened.sendMessage(player, "{crate}", crateName);

            return true;
        }

        if (MiscUtils.isInventoryFull(player)) {
            Message.inventory_not_empty.sendMessage(player, "{crate}", crateName);

            return true;
        }

        return false;
    }

    @Command("open")
    @Permission(value = "crazycrates.open", def = PermissionDefault.OP)
    @Syntax("/crazycrates open <crate_name> <key_type>")
    public void open(Player player, @ArgName("crate") @Suggestion("crates") String crateName, @ArgName("key_type") @Suggestion("keys") String type) {
        if (isCancelled(player, crateName)) return;

        // Get the crate.
        final Crate crate = getCrate(player, crateName, false);

        // If crate is null, return.
        if (crate == null) {
            Message.not_a_crate.sendMessage(player, "{crate}", crateName);

            return;
        }

        final CrateType crateType = crate.getCrateType();

        if (crateType == CrateType.menu) {
            Message.internal_error.sendMessage(player);

            this.fusion.log(Level.ERROR, "An error has occurred: The crate type is Menu for the crate named %s.", crateName);

            return;
        }

        final String fancyName = crate.getCrateName();
        final String fileName = crate.getFileName();

        // Prevent it from working with these crate types.
        if (crateType == CrateType.crate_on_the_go || crateType == CrateType.quick_crate || crateType == CrateType.fire_cracker || crateType == CrateType.quad_crate) {
            Message.not_physical_crate.sendMessage(player, Map.of(
                    "{cratetype}", crateType.getName(),
                    "{crate}", fancyName
            ));

            return;
        }

        final KeyType keyType = getKeyType(type);

        final boolean hasKey = this.config.getProperty(ConfigKeys.virtual_accepts_physical_keys) && keyType == KeyType.physical_key ? this.userManager.getTotalKeys(player.getUniqueId(), fileName) >= 1 : this.userManager.getVirtualKeys(player.getUniqueId(), fileName) >= 1;

        // If no key, run this.
        if (!hasKey) {
            if (this.config.getProperty(ConfigKeys.need_key_sound_toggle)) {
                player.playSound(Sound.sound(Key.key(this.config.getProperty(ConfigKeys.need_key_sound)), Sound.Source.MASTER, 1f, 1f));
            }

            Message.no_keys.sendMessage(player, Map.of(
                    "{key}", crate.getKeyName(),
                    "{crate}", fancyName
            ));

            return;
        }

        // They passed the check.
        this.crateManager.openCrate(player, crate, keyType, player.getLocation(), true, false, EventType.event_crate_opened);
    }

    @Command("open-others")
    @Permission(value = "crazycrates.open-others", def = PermissionDefault.OP)
    @Syntax("/crazycrates open-others <crate_name> <player_name> <key_type>")
    public void others(CommandSender sender, @ArgName("crate") @Suggestion("crates") String crateName, @ArgName("player") @Suggestion("players") Player player, @ArgName("key_type") @Suggestion("keys") String type) {
        // If the command is cancelled.
        if (isCancelled(player, crateName)) return;

        // Get the crate.
        final Crate crate = getCrate(player, crateName, false);

        // If crate is null, return.
        if (crate == null) {
            Message.not_a_crate.sendMessage(sender, "{crate}", crateName);

            return;
        }

        final CrateType crateType = crate.getCrateType();
        final String fancyName = crate.getCrateName();
        final String fileName = crate.getFileName();

        // Prevent it from working with these crate types.
        if (crateType == CrateType.crate_on_the_go || crateType == CrateType.quick_crate || crateType == CrateType.fire_cracker || crateType == CrateType.quad_crate) {
            Message.not_physical_crate.sendMessage(sender, Map.of(
                    "{cratetype}", crateType.getName(),
                    "{crate}", fancyName
            ));

            return;
        }

        final KeyType keyType = getKeyType(type);

        if (sender == player) {
            open(player, crateName, type);

            return;
        }

        final boolean hasKey = this.config.getProperty(ConfigKeys.virtual_accepts_physical_keys) && keyType == KeyType.physical_key ? this.userManager.getTotalKeys(player.getUniqueId(), fileName) >= 1 : this.userManager.getVirtualKeys(player.getUniqueId(), fileName) >= 1;

        if (!hasKey) {
            if (this.config.getProperty(ConfigKeys.need_key_sound_toggle)) {
                player.playSound(Sound.sound(Key.key(this.config.getProperty(ConfigKeys.need_key_sound)), Sound.Source.MASTER, 1f, 1f));
            }

            Message.no_keys.sendMessage(sender, Map.of(
                    "{key}", crate.getKeyName(),
                    "{crate}", fancyName
            ));

            return;
        }

        this.crateManager.openCrate(player, crate, keyType, player.getLocation(), true, false, EventType.event_crate_opened);

        Message.command_opened_crate.sendMessage(sender, Map.of(
                "{player}", player.getName(),
                "{crate}", fancyName
        ));
    }


    @Command("forceopen")
    @Permission(value = "crazycrates.forceopen", def = PermissionDefault.OP)
    @Syntax("/crazycrates forceopen <crate_name> <player_name>")
    public void forceopen(CommandSender sender, @ArgName("crate") @Suggestion("crates") String crateName, @ArgName("player") @Suggestion("players") Player player) {
        // If the command is cancelled.
        if (isCancelled(player, crateName)) return;

        // Get the crate.
        final Crate crate = getCrate(player, crateName, false);

        // If crate is null, return.
        if (crate == null) {
            Message.not_a_crate.sendMessage(sender, "{crate}", crateName);

            return;
        }

        final CrateType crateType = crate.getCrateType();
        final String fancyName = crate.getCrateName();

        // Prevent it from working with these crate types.
        if (crateType == CrateType.crate_on_the_go || crateType == CrateType.quick_crate || crateType == CrateType.fire_cracker || crateType == CrateType.quad_crate) {
            Message.not_physical_crate.sendMessage(sender, Map.of(
                    "{cratetype}", crateType.getName(),
                    "{crate}", fancyName
            ));

            return;
        }

        this.crateManager.openCrate(player, crate, KeyType.free_key, player.getLocation(), true, false, EventType.event_crate_force_opened);

        Message.command_opened_crate.sendMessage(sender, Map.of(
                "{player}", player.getName(),
                "{crate}", fancyName
        ));
    }

    @Command("mass-open")
    @Permission(value = "crazycrates.massopen", def = PermissionDefault.OP)
    @Syntax("/crazycrates mass-open <crate_name> <key_type> <amount>")
    public void mass(Player player, @ArgName("crate") @Suggestion("crates") String crateName, @ArgName("key_type") @Suggestion("keys") String type, @ArgName("amount") @Suggestion("numbers") int amount) {
        // If the command is cancelled.
        if (isCancelled(player, crateName)) return;

        if (amount <= 0) {
            Message.not_a_number.sendMessage(player, "{amount}", String.valueOf(amount));

            return;
        }

        // Get the crate.
        final Crate crate = getCrate(player, crateName, false);

        // If crate is null, return.
        if (crate == null) {
            Message.not_a_crate.sendMessage(player, "{crate}", crateName);

            return;
        }

        final String fancyName = crate.getCrateName();
        final String fileName = crate.getFileName();
        final String keyName = crate.getKeyName();

        final KeyType keyType = getKeyType(type);

        int keys = keyType == KeyType.physical_key ? this.userManager.getPhysicalKeys(player.getUniqueId(), fileName) : this.userManager.getVirtualKeys(player.getUniqueId(), fileName);
        int currentAmount = 0; // amount of keys to use when rolling

        if (keys == 0) {
            Message.no_keys.sendMessage(player, Map.of(
                    "{crate}", fancyName,
                    "{key}", keyName
            ));

            return;
        }

        final int requiredKeys = crate.getRequiredKeys();

        if (crate.useRequiredKeys() && keys < requiredKeys) {
            Message.not_enough_keys.sendMessage(player, Map.of(
                    "{required_amount}", String.valueOf(requiredKeys),
                    "{key_amount}", String.valueOf(requiredKeys),
                    "{amount}", String.valueOf(keys),
                    "{crate}", fancyName,
                    "{key}", keyName
            ));

            return;
        }

        this.crateManager.addPlayerToOpeningList(player, crate);

        for (;keys > 0; keys--) { // check keys first.
            if (currentAmount >= crate.getMaxMassOpen()) break;

            if (currentAmount >= amount) break;

            currentAmount++;
        }

        final UUID uuid = player.getUniqueId();

        if (!this.userManager.takeKeys(uuid, fileName, keyType, currentAmount, true)) { // take keys first.
            // End the crate.
            this.crateManager.endCrate(player);

            return;
        }

        int keysRefund = 0;
        int keysUsed = 0;

        boolean isLoopBroken = false;
        boolean isInventoryFull = false;

        for (;currentAmount > 0; currentAmount--) {
            if (MiscUtils.isInventoryFull(player)) {
                isLoopBroken = true;
                isInventoryFull = true;

                break;
            }

            if (!PrizeManager.giveRewards(player, crate)) {
                currentAmount--;
                keysRefund++;

                isLoopBroken = true;

                break;
            }

            keysUsed++;
        }

        if (isLoopBroken) {
            final int newAmount = currentAmount+keysRefund;

            if (isInventoryFull) {
                this.userManager.addVirtualKeys(uuid, fileName, newAmount);
            } else {
                this.userManager.addKeys(uuid, fileName, keyType, newAmount);
            }
        }

        this.userManager.addOpenedCrate(uuid, fileName, keysUsed);

        final String name = player.getName();

        EventManager.logEvent(EventType.event_crate_opened, name, player, crate, keyType, keysUsed);
        EventManager.logEvent(EventType.event_key_taken, name, player, crate, keyType, keysUsed);

        this.crateManager.removePlayerFromOpeningList(player);
    }
}