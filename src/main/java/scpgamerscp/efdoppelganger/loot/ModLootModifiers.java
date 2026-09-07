package scpgamerscp.efdoppelganger.loot;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import scpgamerscp.efdoppelganger.EFDoppelganger;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, EFDoppelganger.MOD_ID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> VILLAGER_MEAT =
            SERIALIZERS.register("villager_meat", () -> VillagerMeatModifier.CODEC);

    private ModLootModifiers() {}
}
