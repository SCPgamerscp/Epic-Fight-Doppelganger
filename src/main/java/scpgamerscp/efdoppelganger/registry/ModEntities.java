package scpgamerscp.efdoppelganger.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import scpgamerscp.efdoppelganger.EFDoppelganger;
import scpgamerscp.efdoppelganger.entity.YourselfEntity;

@Mod.EventBusSubscriber(modid = EFDoppelganger.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EFDoppelganger.MOD_ID);

    public static final RegistryObject<EntityType<YourselfEntity>> YOURSELF = ENTITIES.register("yourself",
            () -> EntityType.Builder.of(YourselfEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(EFDoppelganger.MOD_ID + ":yourself"));

    private ModEntities() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(YOURSELF.get(), YourselfEntity.createAttributes().build());
    }
}
