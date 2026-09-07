package scpgamerscp.efdoppelganger.memory;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import scpgamerscp.efdoppelganger.EFDoppelganger;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

@Mod.EventBusSubscriber(modid = EFDoppelganger.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WeaponMemoryEvents {
    private WeaponMemoryEvents() {}

    @SubscribeEvent
    public static void registerCaps(RegisterCapabilitiesEvent event) {
        event.register(WeaponMemory.class);
    }

    public static WeaponMemory get(Player player) {
        return player.getCapability(WeaponMemoryProvider.CAPABILITY).orElse(null);
    }

    public static void snapshotSkills(Player player, WeaponMemory memory) {
        try {
            PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
            if (patch == null) {
                return;
            }
            var cap = patch.getSkillCapability();
            if (cap == null) {
                return;
            }
            cap.getSkillContainers().forEach(container -> {
                if (container != null && container.getSkill() != null) {
                    memory.rememberSkill(String.valueOf(container.getSkill()));
                }
            });
        } catch (Throwable t) {
            EFDoppelganger.LOGGER.debug("Skill snapshot skipped: {}", t.toString());
        }
    }

    public static boolean isHealingItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem().getFoodProperties(stack, null) != null) {
            return true;
        }
        if (stack.getItem() instanceof PotionItem) {
            return true;
        }
        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    @Mod.EventBusSubscriber(modid = EFDoppelganger.MOD_ID)
    public static final class ForgeEvents {
        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(WeaponMemoryProvider.ID, new WeaponMemoryProvider());
            }
        }

        @SubscribeEvent
        public static void clone(PlayerEvent.Clone event) {
            event.getOriginal().reviveCaps();
            WeaponMemory oldMem = get(event.getOriginal());
            WeaponMemory newMem = get(event.getEntity());
            if (oldMem != null && newMem != null) {
                newMem.copyFrom(oldMem);
            }
            event.getOriginal().invalidateCaps();
        }

        @SubscribeEvent
        public static void attack(AttackEntityEvent event) {
            Player player = event.getEntity();
            WeaponMemory memory = get(player);
            if (memory == null) {
                return;
            }
            memory.rememberWeapon(player.getMainHandItem());
            if (!player.getOffhandItem().isEmpty() && !isHealingItem(player.getOffhandItem())) {
                memory.rememberWeapon(player.getOffhandItem());
            }
            snapshotSkills(player, memory);
        }

        @SubscribeEvent
        public static void hurt(LivingHurtEvent event) {
            if (event.getSource().getEntity() instanceof Player player) {
                WeaponMemory memory = get(player);
                if (memory != null) {
                    memory.rememberWeapon(player.getMainHandItem());
                    snapshotSkills(player, memory);
                }
            }
        }

        @SubscribeEvent
        public static void usedItem(LivingEntityUseItemEvent.Finish event) {
            LivingEntity entity = event.getEntity();
            if (!(entity instanceof Player player)) {
                return;
            }
            WeaponMemory memory = get(player);
            if (memory == null) {
                return;
            }
            ItemStack stack = event.getItem();
            if (isHealingItem(stack)) {
                memory.rememberHeal(stack);
            } else {
                memory.rememberWeapon(stack);
            }
        }
    }
}
