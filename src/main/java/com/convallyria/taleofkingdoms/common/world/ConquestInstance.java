package com.convallyria.taleofkingdoms.common.world;

import com.convallyria.taleofkingdoms.TaleOfKingdoms;
import com.convallyria.taleofkingdoms.TaleOfKingdomsAPI;
import com.convallyria.taleofkingdoms.client.translation.Translations;
import com.convallyria.taleofkingdoms.common.entity.EntityTypes;
import com.convallyria.taleofkingdoms.common.entity.generic.HunterEntity;
import com.convallyria.taleofkingdoms.common.entity.generic.LoneVillagerEntity;
import com.convallyria.taleofkingdoms.common.entity.guild.GuildMasterEntity;
import com.convallyria.taleofkingdoms.common.generator.processor.GatewayStructureProcessor;
import com.convallyria.taleofkingdoms.common.kingdom.PlayerKingdom;
import com.convallyria.taleofkingdoms.common.schematic.Schematic;
import com.convallyria.taleofkingdoms.common.schematic.SchematicOptions;
import com.convallyria.taleofkingdoms.common.utils.EntityUtils;
import com.google.gson.Gson;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.processor.BlockIgnoreStructureProcessor;
import net.minecraft.structure.processor.JigsawReplacementStructureProcessor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ConquestInstance {

    public static final String CURRENT_VERSION = TaleOfKingdoms.VERSION;

    private String version;
    private final String world;
    private final String name;
    private boolean hasLoaded;
    private BlockPos start;
    private BlockPos end;
    private final BlockPos origin;
    private List<UUID> loneVillagersWithRooms;
    private boolean underAttack;
    private final List<BlockPos> reficuleAttackLocations;
    private final List<UUID> reficuleAttackers;
    private boolean hasRebuilt;

    private final Map<UUID, Integer> playerCoins;
    private final Map<UUID, Integer> playerBankerCoins;
    private final Map<UUID, Long> playerFarmerLastBread;
    private final Map<UUID, Boolean> playerHasContract;
    private final Map<UUID, Integer> playerWorthiness;
    private final Map<UUID, PlayerKingdom> playerKingdoms;
    private Map<UUID, List<UUID>> hunterUUIDs;

    //todo: do we need concurrency?
    public ConquestInstance(String world, String name, BlockPos start, BlockPos end, BlockPos origin) {
        Optional<ConquestInstance> instance = Optional.ofNullable(TaleOfKingdoms.getAPI())
                .map(TaleOfKingdomsAPI::getConquestInstanceStorage)
                .orElseThrow(() -> new IllegalArgumentException("API not present"))
                .getConquestInstance(world);
        if (instance.isPresent() && instance.get().isLoaded()) throw new IllegalArgumentException("World already registered");
        this.version = CURRENT_VERSION;
        this.world = world;
        this.name = name;
        this.start = start;
        this.end = end;
        this.origin = origin;
        this.loneVillagersWithRooms = new ArrayList<>();
        this.reficuleAttackLocations = new ArrayList<>();
        this.reficuleAttackers = new ArrayList<>();
        this.playerCoins = new ConcurrentHashMap<>();
        this.playerBankerCoins = new ConcurrentHashMap<>();
        this.playerFarmerLastBread = new ConcurrentHashMap<>();
        this.playerHasContract = new ConcurrentHashMap<>();
        this.playerWorthiness = new ConcurrentHashMap<>();
        this.playerKingdoms = new ConcurrentHashMap<>();
        this.hunterUUIDs = new ConcurrentHashMap<>();
    }

    public boolean isOld() {
        return !Objects.equals(this.version, CURRENT_VERSION);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getWorld() {
        return world;
    }

    public String getName() {
        return name;
    }

    public boolean isLoaded() {
        return hasLoaded;
    }

    public void setLoaded(boolean loaded) {
        this.hasLoaded = loaded;
    }

    public BlockPos getStart() {
        return start;
    }

    public void setStart(BlockPos start) {
        this.start = start;
    }

    public BlockPos getEnd() {
        return end;
    }

    public void setEnd(BlockPos end) {
        this.end = end;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public Vec3d getCentre() {
        return new Box(start, end).getCenter();
    }

    public boolean canAttack(UUID uuid) {
        return getWorthiness(uuid) >= (1500.0F / 2) && !isUnderAttack() && !hasRebuilt;
    }
    
    /**
     * Returns true if and only if the guild is not currently under attack and the worthiness of the player is greater than 750
     * @param uuid player uuid to check, nullable
     * @return If the guild has been attacked
     */
    public boolean hasAttacked(UUID uuid) {
        return !isUnderAttack() && getWorthiness(uuid) > 750;
    }

    public void attack(PlayerEntity player, ServerWorldAccess world) {
        if (canAttack(player.getUuid())) {
            TaleOfKingdoms.LOGGER.info("Initiating guild attack for player " + player.getName());
            EntityUtils.spawnEntity(EntityTypes.GUILDMASTER_DEFENDER, world, player.getBlockPos());
            this.underAttack = true;
            Translations.GUILDMASTER_HELP.send(player);

            Identifier gateway = new Identifier(TaleOfKingdoms.MODID, "gateway/gateway");
            world.toServerWorld().getStructureTemplateManager().getTemplate(gateway).ifPresent(structure -> {
                for (BlockPos reficuleAttackLocation : reficuleAttackLocations) {
                    StructurePlacementData structurePlacementData = new StructurePlacementData();
                    structurePlacementData.addProcessor(GatewayStructureProcessor.INSTANCE);
                    structurePlacementData.addProcessor(JigsawReplacementStructureProcessor.INSTANCE);
                    structurePlacementData.addProcessor(BlockIgnoreStructureProcessor.IGNORE_AIR);
                    BlockPos newPos = reficuleAttackLocation.subtract(new Vec3i(6, 1, 6));
                    structure.place(world, newPos, newPos, structurePlacementData, Random.create(), Block.NOTIFY_ALL);
                }
            });
        }
    }

    /**
     * @return If the guild is currently under attack
     */
    public boolean isUnderAttack() {
        return underAttack;
    }

    public void setUnderAttack(boolean underAttack) {
        this.underAttack = underAttack;
    }

    public List<UUID> getLoneVillagersWithRooms() {
        if (loneVillagersWithRooms == null) this.loneVillagersWithRooms = new ArrayList<>();
        return loneVillagersWithRooms;
    }

    public void addLoneVillagerWithRoom(LoneVillagerEntity entity) {
        if (loneVillagersWithRooms == null) this.loneVillagersWithRooms = new ArrayList<>();
        loneVillagersWithRooms.add(entity.getUuid());
    }

    public List<BlockPos> getReficuleAttackLocations() {
        return reficuleAttackLocations;
    }

    public List<UUID> getReficuleAttackers() {
        return reficuleAttackers;
    }

    /**
     * @return If the guild has been rebuilt
     */
    public boolean hasRebuilt() {
        return hasRebuilt;
    }

    public void setRebuilt(boolean hasRebuilt) {
        this.hasRebuilt = hasRebuilt;
    }


    public boolean hasPlayer(UUID playerUuid) {
        return playerCoins.containsKey(playerUuid)
                && playerBankerCoins.containsKey(playerUuid)
                && playerFarmerLastBread.containsKey(playerUuid)
                && playerHasContract.containsKey(playerUuid)
                && playerWorthiness.containsKey(playerUuid);
    }

    public int getCoins(UUID uuid) {
        return playerCoins.getOrDefault(uuid, 0);
    }

    public int getBankerCoins(UUID uuid) { return playerBankerCoins.getOrDefault(uuid, 0); }

    public void setBankerCoins(UUID uuid, int bankerCoins) { this.playerBankerCoins.put(uuid, bankerCoins); }

    public void setCoins(UUID uuid, int coins) {
        this.playerCoins.put(uuid, coins);
    }

    public void addCoins(UUID uuid, int coins) {
        this.playerCoins.put(uuid, getCoins(uuid) + coins);
    }

    public long getFarmerLastBread(UUID uuid) {
        return playerFarmerLastBread.getOrDefault(uuid, 0L);
    }

    public void setFarmerLastBread(UUID uuid, long day) {
        this.playerFarmerLastBread.put(uuid, day);
    }

    public boolean hasContract(UUID uuid) {
        return playerHasContract.getOrDefault(uuid, false);
    }

    public void setHasContract(UUID uuid, boolean hasContract) {
        this.playerHasContract.put(uuid, hasContract);
    }

    public int getWorthiness(UUID playerUuid) {
        return playerWorthiness.getOrDefault(playerUuid, 0);
    }

    public void setWorthiness(UUID playerUuid, int worthiness) {
        this.playerWorthiness.put(playerUuid, worthiness);
    }

    public void addWorthiness(UUID playerUuid, int worthiness) {
        this.playerWorthiness.put(playerUuid, getWorthiness(playerUuid) + worthiness);
    }

    @Nullable
    public PlayerKingdom getKingdom(UUID uuid) {
        return playerKingdoms.getOrDefault(uuid, null);
    }

    public Collection<PlayerKingdom> getKingdoms() {
        return playerKingdoms.values();
    }

    public PlayerKingdom addKingdom(UUID uuid, @NotNull PlayerKingdom kingdom) {
        if (playerKingdoms.containsKey(uuid)) {
            throw new IllegalArgumentException("Kingdom already exists for player " + uuid + "!");
        }
        return playerKingdoms.put(uuid, kingdom);
    }

    public Map<UUID, List<UUID>> getHunterUUIDs() {
        if (hunterUUIDs == null) hunterUUIDs = new ConcurrentHashMap<>();
        return hunterUUIDs;
    }

    public void addHunter(UUID playerUuid, HunterEntity hunterEntity) {
        List<UUID> uuids = hunterUUIDs.getOrDefault(playerUuid, new ArrayList<>());
        uuids.add(hunterEntity.getUuid());
        hunterUUIDs.put(playerUuid, uuids);
    }

    public void removeHunter(UUID playerUuid, UUID hunterUuid) {
        List<UUID> uuids = hunterUUIDs.getOrDefault(playerUuid, new ArrayList<>());
        uuids.remove(hunterUuid);
        hunterUUIDs.put(playerUuid, uuids);
    }

    public void reset(@NotNull PlayerEntity player) {
        this.setBankerCoins(player.getUuid(), 0);
        this.setCoins(player.getUuid(), 0);
        this.setFarmerLastBread(player.getUuid(), 0);
        this.setHasContract(player.getUuid(), false);
        this.setWorthiness(player.getUuid(), 0);
    }

    public Optional<GuildMasterEntity> getGuildMaster(World world) {
        if (start == null || end == null) return Optional.empty();
        Box box = new Box(getStart(), getEnd());
        return world.getEntitiesByType(EntityTypes.GUILDMASTER, box, guildMaster -> !guildMaster.isFireImmune()).stream().findFirst();
    }

    public <T extends Entity> Optional<T> getGuildEntity(World world, EntityType<T> type) {
        if (start == null || end == null) return Optional.empty();
        Box box = new Box(getStart(), getEnd());
        return world.getEntitiesByType(type, box, entity -> true).stream().findFirst();
    }

    private List<BlockPos> validRest;

    /**
     * Gets valid sleep area locations. This gets the sign, not the bed head.
     * @param player the player
     * @return list of signs where sleeping is allowed
     */
    @NotNull
    public List<BlockPos> getSleepLocations(PlayerEntity player) {
        if (validRest == null) validRest = new ArrayList<>();
        if (validRest.isEmpty()) { // Find a valid resting place. This will only run if validRest is empty, which is also saved to file.
            int topBlockX = (Math.max(start.getX(), end.getX()));
            int bottomBlockX = (Math.min(start.getX(), end.getX()));

            int topBlockY = (Math.max(start.getY(), end.getY()));
            int bottomBlockY = (Math.min(start.getY(), end.getY()));

            int topBlockZ = (Math.max(start.getZ(), end.getZ()));
            int bottomBlockZ = (Math.min(start.getZ(), end.getZ()));

            for (int x = bottomBlockX; x <= topBlockX; x++) {
                for (int z = bottomBlockZ; z <= topBlockZ; z++) {
                    for (int y = bottomBlockY; y <= topBlockY; y++) {
                        BlockPos blockPos = new BlockPos(x, y, z);
                        BlockEntity tileEntity = player.getEntityWorld().getChunk(blockPos).getBlockEntity(blockPos);
                        if (tileEntity instanceof BedBlockEntity) {
                            validRest.add(blockPos);
                        }
                    }
                }
            }
        }

        return validRest;
    }

    public List<BlockPos> getValidRest() {
        return validRest;
    }

    /**
     * Checks if an entity is in the guild.
     * @param entity the entity
     * @return true if player is in guild, false if not
     */
    public boolean isInGuild(Entity entity) {
        return isInGuild(entity.getBlockPos());
    }

    /**
     * Checks if a location is in the guild.
     * @param pos the {@link BlockPos}
     * @return true if position is in guild, false if not
     */
    public boolean isInGuild(BlockPos pos) {
        if (start == null || end == null) return false; // Probably still pasting.
        BlockBox blockBox = new BlockBox(end.getX(), end.getY(), end.getZ(), start.getX(), start.getY(), start.getZ());
        return blockBox.contains(pos);
    }

    public boolean isInKingdom(Entity player) {
        final PlayerKingdom kingdom = getKingdom(player.getUuid());
        if (kingdom == null) return false;
        BlockPos start = kingdom.getStart();
        BlockPos end = kingdom.getEnd();
        BlockBox blockBox = new BlockBox(end.getX(), end.getY(), end.getZ(), start.getX(), start.getY(), start.getZ());
        return blockBox.contains(player.getBlockPos());
    }

    /**
     * Searches the player's current location (guild or kingdom) for an entity
     * @param world
     * @param type
     * @return
     * @param <T>
     */
    public <T extends Entity> Optional<T> search(PlayerEntity player, World world, EntityType<T> type) {
        if (start == null || end == null) return Optional.empty();
        if (isInKingdom(player)) {
            final PlayerKingdom kingdom = getKingdom(player.getUuid());
            if (kingdom == null) return Optional.empty();
            return kingdom.getKingdomEntity(world, type);
        } else if (isInGuild(player)) {
            return getGuildEntity(world, type);
        }
        return Optional.empty();
    }

    public CompletableFuture<BlockBox> rebuild(ServerPlayerEntity serverPlayerEntity, TaleOfKingdomsAPI api, SchematicOptions... options) {
        return api.getSchematicHandler().pasteSchematic(Schematic.GUILD_CASTLE, serverPlayerEntity, getOrigin().subtract(new Vec3i(0, 21, 0)), options);
    }

    public void save(TaleOfKingdomsAPI api) {
        File file = new File(api.getDataFolder() + "worlds" + File.separator + world + ".conquestworld");
        try (Writer writer = new FileWriter(file)) {
            Gson gson = api.getMod().getGson();
            gson.toJson(this, writer);
            TaleOfKingdoms.LOGGER.info("Saved data");
        } catch (IOException e) {
            TaleOfKingdoms.LOGGER.error("Error saving data: ", e);
            e.printStackTrace();
        }
    }
}