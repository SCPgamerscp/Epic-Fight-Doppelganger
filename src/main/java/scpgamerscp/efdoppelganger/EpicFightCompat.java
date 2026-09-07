package scpgamerscp.efdoppelganger;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import scpgamerscp.efdoppelganger.entity.YourselfPatch;
import scpgamerscp.efdoppelganger.registry.ModEntities;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

@Mod.EventBusSubscriber(modid = EFDoppelganger.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EpicFightCompat {
    private EpicFightCompat() {}

    @SubscribeEvent
    public static void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        event.getTypeEntry().put(ModEntities.YOURSELF.get(), entity -> YourselfPatch::new);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeModificationEvent event) {
        EntityType<? extends LivingEntity> type = ModEntities.YOURSELF.get();
        event.add(type, EpicFightAttributes.WEIGHT.get());
        event.add(type, EpicFightAttributes.ARMOR_NEGATION.get(), 10.0D);
        event.add(type, EpicFightAttributes.IMPACT.get(), 2.4D);
        event.add(type, EpicFightAttributes.MAX_STRIKES.get());
        event.add(type, EpicFightAttributes.STUN_ARMOR.get(), 24.0D);
        event.add(type, EpicFightAttributes.OFFHAND_ATTACK_SPEED.get());
        event.add(type, EpicFightAttributes.OFFHAND_MAX_STRIKES.get());
        event.add(type, EpicFightAttributes.OFFHAND_ARMOR_NEGATION.get());
        event.add(type, EpicFightAttributes.OFFHAND_IMPACT.get());
    }
}
