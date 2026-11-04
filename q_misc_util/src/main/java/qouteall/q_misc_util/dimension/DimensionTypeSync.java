package qouteall.q_misc_util.dimension;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class DimensionTypeSync {

    @OnlyIn(Dist.CLIENT)
    public static Map<ResourceKey<Level>, ResourceKey<DimensionType>> clientTypeMap;

    private static Map<ResourceKey<Level>, DimensionType> clientInlineTypeMap = new HashMap<>();

    @OnlyIn(Dist.CLIENT)
    private static RegistryAccess currentDimensionTypeTracker;

    @OnlyIn(Dist.CLIENT)
    public static void onGameJoinPacketReceived(RegistryAccess tracker) {
        currentDimensionTypeTracker = tracker;
    }

    @OnlyIn(Dist.CLIENT)
    public static void acceptTypeMapData(CompoundTag tag) {
        Map<ResourceKey<Level>, ResourceKey<DimensionType>> registeredTypes = new HashMap<>();
        Map<ResourceKey<Level>, DimensionType> inlineTypes = new HashMap<>();
        tag.getAllKeys().forEach(key -> {
            ResourceKey<Level> worldKey = DimId.idToKey(key);
            Tag typeTag = tag.get(key);
            if (typeTag instanceof CompoundTag inlineTypeTag) {
                inlineTypes.put(worldKey, decodeInlineType(worldKey, inlineTypeTag));
            }
            else {
                registeredTypes.put(worldKey, idToDimType(new ResourceLocation(typeTag.getAsString())));
            }
        });
        clientTypeMap = registeredTypes;
        clientInlineTypeMap = inlineTypes;
        
        Helper.log("Received Dimension Type Sync");
        Helper.log("\n" + Helper.myToString(Stream.concat(
            registeredTypes.entrySet().stream().map(
                e -> e.getKey().location().toString() + " -> " + e.getValue().location()
            ),
            inlineTypes.keySet().stream().map(
                worldKey -> worldKey.location().toString() + " -> (unregistered type, synced by value)"
            )
        )));
    }
    
    @OnlyIn(Dist.CLIENT)
    private static DimensionType decodeInlineType(ResourceKey<Level> worldKey, CompoundTag inlineTypeTag) {
        return DimensionType.DIRECT_CODEC.parse(NbtOps.INSTANCE, inlineTypeTag).getOrThrow(
            false,
            error -> Helper.err("Cannot decode the dimension type of " + worldKey.location() + ": " + error)
        );
    }
    
    public static CompoundTag createTagFromServerWorldInfo() {
        Registry<DimensionType> dimensionTypes =
            MiscHelper.getServer().registryAccess().registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY);
        CompoundTag tag = new CompoundTag();
        for (ServerLevel world : MiscHelper.getServer().getAllLevels()) {
            tag.put(world.dimension().location().toString(), createTypeTag(world, dimensionTypes));
        }
        return tag;
    }
    
    // Mods like Vault Hunters create levels with an unregistered Holder.direct dimension type,
    // which the client cannot look up by id, so those types are sent by value.
    private static Tag createTypeTag(ServerLevel world, Registry<DimensionType> dimensionTypes) {
        Holder<DimensionType> typeHolder = world.dimensionTypeRegistration();
        ResourceLocation id = typeHolder.unwrapKey()
            .map(ResourceKey::location)
            .orElseGet(() -> dimensionTypes.getKey(typeHolder.value()));
        if (id != null) {
            return StringTag.valueOf(id.toString());
        }
        return DimensionType.DIRECT_CODEC.encodeStart(NbtOps.INSTANCE, typeHolder.value()).getOrThrow(
            false,
            error -> Helper.err("Cannot encode the dimension type of " + world.dimension().location() + ": " + error)
        );
    }
    
    public static ResourceKey<DimensionType> idToDimType(ResourceLocation id) {
        return ResourceKey.create(Registry.DIMENSION_TYPE_REGISTRY, id);
    }

    @OnlyIn(Dist.CLIENT)
    public static Holder<DimensionType> getDimensionTypeHolder(
        ResourceKey<Level> worldKey, RegistryAccess registryAccess
    ) {
        DimensionType inlineType = clientInlineTypeMap.get(worldKey);
        if (inlineType != null) {
            return Holder.direct(inlineType);
        }
        return registryAccess.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY)
            .getHolderOrThrow(getDimensionTypeKey(worldKey));
    }

    @OnlyIn(Dist.CLIENT)
    public static ResourceKey<DimensionType> getDimensionTypeKey(ResourceKey<Level> worldKey) {
        if (worldKey == Level.OVERWORLD) {
            return DimensionType.OVERWORLD_LOCATION;
        }
        
        if (worldKey == Level.NETHER) {
            return DimensionType.NETHER_LOCATION;
        }
        
        if (worldKey == Level.END) {
            return DimensionType.END_LOCATION;
        }
        
        ResourceKey<DimensionType> obj = clientTypeMap.get(worldKey);
        
        if (obj == null) {
            Helper.err("Missing Dimension Type For " + worldKey);
            return DimensionType.OVERWORLD_LOCATION;
        }
        
        return obj;
    }

    @OnlyIn(Dist.CLIENT)
    public static DimensionType getDimensionType(ResourceKey<DimensionType> registryKey) {
        return currentDimensionTypeTracker.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).get(registryKey);
    }
    
}
