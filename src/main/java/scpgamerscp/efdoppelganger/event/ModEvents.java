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
import net.minecraftforge.fml.common.Mod;
import scpgamerscp.efdoppelganger.EFDoppelganger;
import scpgamerscp.efdoppelganger.config.DoppelConfig;
import scpgamerscp.efdoppelganger.entity.YourselfEntity;
import scpgamerscp.efdoppelganger.entity.YourselfPatch;
import scpgamerscp.efdoppelganger.item.ModItems;
import scpgamerscp.efdoppelganger.registry.ModEntities;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

@Mod.EventBusSubscriber(modid = EFDoppelganger.MOD_ID)
public final class ModEvents {
    private static final int POISON_II = 1;

    private ModEvents() {}

    private static final java.util.List<PendingSummon> PENDING_SUMMONS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private record PendingSummon(
            java.util.UUID playerUUID,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            double x, double y, double z,
            float yRot,
            int[] ticksRemaining
    ) {}

    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!stack.is(ModItems.VILLAGER_MEAT.get())) {
            return;
        }
        scheduleSummon(player);
    }

    public static void scheduleSummon(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // プレイヤーの視線前方 2.5 ブロックの位置を出現位置とする
        double yawRad = Math.toRadians(player.getYRot());
        double spawnX = player.getX() - Math.sin(yawRad) * 2.5;
        double spawnY = player.getY();
        double spawnZ = player.getZ() + Math.cos(yawRad) * 2.5;
        float faceYaw = player.getYRot() + 180.0F;

        PENDING_SUMMONS.add(new PendingSummon(
                player.getUUID(),
                player.level().dimension(),
                spawnX, spawnY, spawnZ,
                faceYaw,
                new int[]{100} // 5 seconds (100 ticks)
        ));

        // 開始音
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.2F, 0.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.0F, 0.5F);
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (PENDING_SUMMONS.isEmpty()) {
            return;
        }
        for (PendingSummon summon : PENDING_SUMMONS) {
            summon.ticksRemaining[0]--;
            int remaining = summon.ticksRemaining[0];

            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) continue;
            ServerLevel level = server.getLevel(summon.dimension);
            if (level == null) continue;

            Player player = level.getPlayerByUUID(summon.playerUUID);

            // カウントダウン中の演出
            if (remaining > 0) {
                // ソウル炎とスモークの予兆エフェクト
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, summon.x, summon.y + 0.1, summon.z, 3, 0.3, 0.2, 0.3, 0.02);
                level.sendParticles(ParticleTypes.SMOKE, summon.x, summon.y + 0.1, summon.z, 2, 0.2, 0.4, 0.2, 0.01);

                // 20 ticks（1秒）ごとにチャージ音（徐々に高音に）
                if (remaining % 20 == 0) {
                    float pitch = 0.6F + (float) (100 - remaining) / 100.0F * 0.8F;
                    level.playSound(null, summon.x, summon.y, summon.z, SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.HOSTILE, 1.0F, pitch);
                    level.playSound(null, summon.x, summon.y, summon.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, pitch);
                }
            } else {
                // 5秒経過：ボス出現！
                PENDING_SUMMONS.remove(summon);
                spawnBoss(level, summon, player);
            }
        }
    }

    private static void spawnBoss(ServerLevel level, PendingSummon summon, Player player) {
        YourselfEntity boss = ModEntities.YOURSELF.get().create(level);
        if (boss == null) {
            return;
        }
        boss.moveTo(summon.x, summon.y, summon.z, summon.yRot, 0.0F);

        if (player != null && player.isAlive()) {
            boss.copyFrom(player);
        } else {
            Player nearest = level.getNearestPlayer(summon.x, summon.y, summon.z, 64.0D, false);
            if (nearest != null) {
                boss.copyFrom(nearest);
            }
        }

        spawnSoulPillar(level, summon.x, summon.y, summon.z);
        level.addFreshEntity(boss);
        level.playSound(null, summon.x, summon.y, summon.z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.5F, 0.6F);
        level.playSound(null, summon.x, summon.y, summon.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.3F);
        level.playSound(null, summon.x, summon.y, summon.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.8F, 1.0F);

        YourselfPatch patch = EpicFightCapabilities.getEntityPatch(boss, YourselfPatch.class);
        if (patch != null) {
            patch.rebuildCombatAi();
        }
    }

    public static void summonYourself(Player player) {
        scheduleSummon(player);
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
