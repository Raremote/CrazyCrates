package com.badbones69.crazycrates.paper.api;

import us.crazycrew.crazycrates.api.enums.messages.Message;
import com.badbones69.crazycrates.paper.api.enums.other.keys.FileKeys;
import com.badbones69.crazycrates.paper.api.objects.Tier;
import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.events.PlayerPrizeEvent;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.Prize;
import com.badbones69.crazycrates.paper.managers.BukkitUserManager;
import com.badbones69.crazycrates.paper.tasks.crates.other.CosmicCrateManager;
import com.ryderbelserion.fusion.core.api.enums.Level;
import com.ryderbelserion.fusion.core.utils.StringUtils;
import com.ryderbelserion.fusion.paper.FusionPaper;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import com.badbones69.crazycrates.paper.utils.MiscUtils;
import org.jetbrains.annotations.Nullable;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class PrizeManager {
    
    private static final CrazyCrates plugin = CrazyCrates.getPlugin();
    private static final CrazyCratesPaper platform = plugin.getPlatform();
    private static final FusionPaper fusion = platform.getFusion();
    private static final Server server = plugin.getServer();
    private static final PluginManager pluginManager = server.getPluginManager();
    private static final BukkitUserManager userManager = platform.getUserManager();

    public static int getCap(@NotNull final Crate crate, @NotNull final Player player) {
        final String format = "crazycrates.respin." + crate.getFileName() + ".";
        final String lowerCase = format.toLowerCase();

        final int cycleCap = crate.getCyclePermissionCap();

        int cap = 0;

        for (final PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
            String node = permission.getPermission();

            if (node.startsWith(lowerCase)) {
                final Number origin = StringUtils.tryParseInt(node.replace(lowerCase, "")).orElseThrow();

                final int number = origin.intValue();

                if (number > cap && cap < cycleCap) {
                    cap = number;
                }
            }
        }

        return cap;
    }

    public static boolean isCapped(@NotNull final Crate crate, @NotNull final Player player) {
        boolean isCapped = false;

        if (!crate.isCyclePermissionToggle() || crate.getCyclePermissionCap() < 1 || player.isOp()) {
            return false;
        }

        final int wins = userManager.getCrateRespin(player.getUniqueId(), crate.getFileName());

        int cap = getCap(crate, player);

        final String format = "crazycrates.respin." + crate.getFileName() + ".";
        final String node = format + cap;

        if (player.hasPermission(node)) {
            if (wins >= cap) {
                isCapped = true;
            }
        } else {
            isCapped = true;
        }

        return isCapped;
    }

    /**
     * Gets the prize for the player with an offset location.
     *
     * @param player who the prize is for
     * @param crate the player is opening
     * @param prize the player is being given
     *
     * @deprecated
     */
    @Deprecated(forRemoval = true, since = "4.2.1")
    public static void givePrize(@NotNull final Player player, @Nullable final Prize prize, @NotNull final Crate crate) {
        givePrize(player, crate, prize);
    }

    /**
     * Gets the prize for the player with an offset location.
     *
     * @param player who the prize is for
     * @param crate the player is opening
     * @param prize the player is being given
     */
    public static void givePrize(@NotNull final Player player, @NotNull final Crate crate, @Nullable final Prize prize) {
        if (prize != null) {
            givePrize(player, player.getLocation().clone().add(0, 1, 0), crate, prize);
        } else {
            Message.prize_error.sendMessage(player, Map.of(
                    "{crate}", crate.getCrateName(),
                    "{player}", player.getName()
            ));
        }
    }

    /**
     * Gets the prize for the player.
     *
     * @param player who the prize is for
     * @param location the location
     * @param crate the player is opening
     * @param prize the player is being given
     */
    public static void givePrize(@NotNull final Player player, @NotNull final Location location, @NotNull final Crate crate, @Nullable Prize prize) {
        if (prize == null) {
            fusion.log(Level.WARNING, "No prize was found when giving %s a prize.", player.getName());

            return;
        }

        pluginManager.callEvent(new PlayerPrizeEvent(player, crate, prize));

        if (prize.useFireworks()) {
            MiscUtils.spawnFirework(location, null);
        }

        prize = prize.hasPermission(player) ? prize.getAlternativePrize() : prize;

        if (!player.isOp()) {
            final int pulls = getCurrentPulls(prize, crate);

            if (pulls != -1 && pulls < prize.getMaxPulls()) {
                YamlConfiguration configuration = FileKeys.data.getConfiguration();

                configuration.set("Prizes." + crate.getFileName()  + "." + prize.getSectionName() + ".Pulls", pulls + 1);

                // save to file!
                FileKeys.data.save();
            }
        }

        MiscUtils.dropItems(prize.getEditorItems(), player); // drops any leftover editor items.

        MiscUtils.dropBuilders(prize.getItems(), player);

        for (final String command : crate.getPrizeCommands()) {
            runCommands(player, prize, crate, command);
        }

        for (final String command : prize.getCommands()) {
            runCommands(player, prize, crate, command);
        }

        prize.broadcast(player, crate);

        final List<String> cratePrizeMessages = crate.getPrizeMessage();
        final List<String> prizeMessages = prize.getMessages();

        if (!cratePrizeMessages.isEmpty() && prizeMessages.isEmpty()) {
            for (final String message : cratePrizeMessages) {
                sendMessage(player, prize, crate, message);
            }

            return;
        }

        for (final String message : prizeMessages) {
            sendMessage(player, prize, crate, message);
        }
    }

    private static void runCommands(@NotNull final Player player, @NotNull final Prize prize, @NotNull final Crate crate, @NotNull String command) {
        String origin = command;

        if (origin.contains("%random%:")) {
            final StringBuilder commandBuilder = new StringBuilder();

            for (String word : origin.split(" ")) {
                if (word.startsWith("%random%:")) {// /give %player% iron %random%:1-64
                    word = word.replace("%random%:", "");

                    try {
                        long min = Long.parseLong(word.split("-")[0]);
                        long max = Long.parseLong(word.split("-")[1]);

                        commandBuilder.append(MiscUtils.pickNumber(min, max)).append(" ");
                    } catch (final Exception exception) {
                        commandBuilder.append("1 ");

                        fusion.log(Level.WARNING, "The prize %s in the %s crate has caused an error when trying to run a command %s", prize.getPrizeName(), prize.getCrateName(), origin);
                    }
                } else {
                    commandBuilder.append(word).append(" ");
                }
            }

            origin = commandBuilder.toString();
            origin = origin.substring(0, origin.length() - 1);
        }

        final String maxPulls = String.valueOf(prize.getMaxPulls());
        final String pulls = String.valueOf(getCurrentPulls(prize, crate));
        final String prizeName = fusion.replacePlaceholders(prize.getPrizeName(), Map.of(
                "%maxpulls%", maxPulls,
                "%pulls%", pulls
        ));

        MiscUtils.sendCommand(origin, Map.of(
                "%player%", player.getName(),
                "%reward%", prizeName,
                "%reward_stripped%", prize.getStrippedName(),
                "%crate_fancy%", crate.getCrateName(),
                "%crate%", crate.getFileName(),
                "%maxpulls%", maxPulls,
                "%pulls%", pulls,
                "%weight%", String.valueOf(prize.getWeight())
        ));
    }

    private static void sendMessage(@NotNull final Player player, @NotNull final Prize prize, @NotNull final Crate crate, @NotNull final String message) {
        if (message.isEmpty()) return;

        final String maxPulls = String.valueOf(prize.getMaxPulls());
        final String pulls = String.valueOf(getCurrentPulls(prize, crate));
        final String prizeName = fusion.replacePlaceholders(prize.getPrizeName(), Map.of(
                "%maxpulls%", maxPulls,
                "%pulls%", pulls
        ));

        final CrateType crateType = crate.getCrateType();

        final String weight = crateType != CrateType.casino && crateType != CrateType.cosmic ? StringUtils.format(crate.getChance(prize.getWeight())) : StringUtils.format(crate.getTierChance(prize.getWeight()));

        final Map<String, String> placeholders = Map.of(
                "%player%", player.getName(),
                "%reward%", prizeName,
                "%reward_stripped%", prize.getStrippedName(),
                "%crate%", crate.getCrateName(),
                "%maxpulls%", maxPulls,
                "%pulls%", pulls,
                "%chance%", weight,
                "%weight%", String.valueOf(prize.getWeight())
        );

        player.sendMessage(fusion.asComponent(player, message, placeholders));
    }

    public static int getCurrentPulls(final Prize prize, final Crate crate) {
        if (prize.getMaxPulls() == -1) return 0;

        final YamlConfiguration configuration = FileKeys.data.getConfiguration();

        final ConfigurationSection section = configuration.getConfigurationSection("Prizes." + crate.getFileName()  + "." + prize.getSectionName());

        if (section == null) return 0;

        return section.getInt("Pulls", 0);
    }

    public static void getPrize(@NotNull final Crate crate, @NotNull final Inventory inventory, final int slot, @NotNull final Player player) {
        final ItemStack item = inventory.getItem(slot);

        if (item == null) return;

        givePrize(player, player.getLocation().clone().add(0, 1, 0), crate, crate.getPrize(item));
    }

    /**
     * Grants a single roll of rewards for the crate type without any animation.
     *
     * @param player who the prizes are for
     * @param crate the crate being opened
     * @return true if the rewards were given, false if the crate type is misconfigured.
     */
    public static boolean giveRewards(@NotNull final Player player, @NotNull final Crate crate) {
        final ConfigurationSection configuration = crate.getSection();
        final String fileName = crate.getFileName();

        switch (crate.getCrateType()) {
            case csgo_casino, casino -> {
                final ConfigurationSection section = configuration.getConfigurationSection("random");

                if (section != null) {
                    final boolean isRandom = section.getBoolean("toggle", false);

                    if (isRandom) {
                        final List<Tier> tiers = crate.getTiers();
                        final int size = tiers.size();
                        final ThreadLocalRandom random = ThreadLocalRandom.current();
                        final Tier tier = tiers.get(random.nextInt(size));

                        givePrize(player, crate, crate.pickPrize(player, tier));
                        givePrize(player, crate, crate.pickPrize(player, tier));
                        givePrize(player, crate, crate.pickPrize(player, tier));
                    } else {
                        @Nullable final Tier row_uno = crate.getTier(section.getString("types.row-1", ""));
                        @Nullable final Tier row_dos = crate.getTier(section.getString("types.row-2", ""));
                        @Nullable final Tier row_tres = crate.getTier(section.getString("types.row-3", ""));

                        if (row_uno == null || row_dos == null || row_tres == null) {
                            if (fusion.isVerbose()) {
                                fusion.log(Level.WARNING, "One of your rows has a tier that doesn't exist supplied in %s. You can find this in your crate config, search for row-1, row-2, and row-3", fileName);
                            }

                            return false;
                        }

                        givePrize(player, crate, crate.pickPrize(player, row_uno));
                        givePrize(player, crate, crate.pickPrize(player, row_dos));
                        givePrize(player, crate, crate.pickPrize(player, row_tres));
                    }
                }
            }

            case cosmic -> {
                final List<Tier> tiers = crate.getTiers();

                if (tiers.isEmpty()) {
                    return false;
                }

                final int size = tiers.size();
                final ThreadLocalRandom random = ThreadLocalRandom.current();
                final CosmicCrateManager cosmicCrateManager = (CosmicCrateManager) crate.getManager();
                final int totalPrizes = cosmicCrateManager.getTotalPrizes();

                for (int i = 0; i < totalPrizes; i++) {
                    final Tier tier = tiers.get(random.nextInt(size));
                    final Prize prize = crate.pickPrize(player, tier);

                    givePrize(player, crate, prize);
                }
            }

            case quad_crate -> {
                for (int i = 0; i < 4; i++) {
                    givePrize(player, crate, crate.pickPrize(player));
                }
            }

            default -> {
                final Prize prize = crate.pickPrize(player);

                givePrize(player, crate, prize);
            }
        }

        return true;
    }

    public static @Nullable Tier getTier(@NotNull final Crate crate) {
        final List<Tier> tiers = crate.getTiers();

        if (tiers.isEmpty()) return null;

        final Random random = MiscUtils.getRandom();

        double weight = 0.0;

        for (final Tier tier : tiers) {
            weight += tier.getWeight();
        }

        int index = 0;

        for (double value = random.nextDouble() * weight; index < tiers.size() - 1; index++) {
            value -= tiers.get(index).getWeight();

            if (value <= 0.0) break;
        }

        return tiers.get(index);
    }
}