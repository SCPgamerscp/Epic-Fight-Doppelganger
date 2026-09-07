package scpgamerscp.efdoppelganger.item;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import scpgamerscp.efdoppelganger.EFDoppelganger;
import scpgamerscp.efdoppelganger.registry.ModEntities;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, EFDoppelganger.MOD_ID);

    public static final RegistryObject<Item> VILLAGER_MEAT = ITEMS.register("villager_meat", VillagerMeatItem::new);
    public static final RegistryObject<Item> MIRROR_ELIXIR = ITEMS.register("mirror_elixir", MirrorElixirItem::new);
    public static final RegistryObject<Item> YOURSELF_SPAWN_EGG = ITEMS.register("yourself_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.YOURSELF, 0x1A1A1A, 0x8FB8C0, new Item.Properties()));

    private ModItems() {}
}
