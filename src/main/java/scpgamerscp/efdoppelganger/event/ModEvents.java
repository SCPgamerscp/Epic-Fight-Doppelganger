package scpgamerscp.efdoppelganger.event;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import scpgamerscp.efdoppelganger.config.DoppelConfig;
import scpgamerscp.efdoppelganger.entity.YourselfEntity;
import scpgamerscp.efdoppelganger.entity.YourselfPatch;
import scpgamerscp.efdoppelganger.item.ModItems;
import scpgamerscp.efdoppelganger.registry.ModEntities;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

public final class ModEvents {
    private static final int POISON_II = 1;

    private ModEvents() {}

    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!stack.is(ModItems.VILLAGER_MEAT.get())) {
            return;
        }
        summonYourself(player);
    }

    public static void summonYourself(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        YourselfEntity boss = ModEntities.YOURSELF.get().create(level);
        if (boss == null) {
            return;
        }
        boss.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
        boss.copyFrom(player);
        spawnSoulPillar(level, player.getX(), player.getY(), player.getZ());
        level.addFreshEntity(boss);
        level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.4F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.45F, 1.4F);

        YourselfPatch patch = EpicFightCapabilities.getEntityPatch(boss, YourselfPatch.class);
        if (patch != null) {
            patch.rebuildCombatAi();
        }
    }

    public static void spawnSoulPillar(ServerLevel level, double x, double y, double z) {
        for (int i = 0; i < 90; i++) {
            double oy = i * 0.12;
            double ox = (level.random.nextDouble() - 0.5) * 0.55;
            double oz = (level.random.nextDouble() - 0.5) * 0.55;
            level.sendParticles(ParticleTypes.SOUL, x + ox, y + oy, z + oz, 2, 0.08, 0.12, 0.08, 0.01);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x + ox * 0.6, y + oy, z + oz * 0.6, 1, 0.04, 0.08, 0.04, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.SOUL, x, y + 1.0, z, 40, 0.25, 1.6, 0.25, 0.02);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof YourselfEntity) {
            int duration = DoppelConfig.POISON_DURATION_TICKS.get();
            if (duration > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, duration, POISON_II, false, true));
            }
            return;
        }
        if (event.getEntity() instanceof YourselfEntity self
                && event.getSource().getEntity() instanceof LivingEntity living
                && !(living instanceof Player)) {
            self.setTarget(living);
        }
    }
}
