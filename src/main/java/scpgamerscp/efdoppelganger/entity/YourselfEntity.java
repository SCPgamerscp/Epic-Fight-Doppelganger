package scpgamerscp.efdoppelganger.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import scpgamerscp.efdoppelganger.config.DoppelConfig;
import scpgamerscp.efdoppelganger.item.ModItems;
import scpgamerscp.efdoppelganger.memory.WeaponMemory;
import scpgamerscp.efdoppelganger.memory.WeaponMemoryEvents;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class YourselfEntity extends Monster {
    private static final EntityDataAccessor<String> OWNER_UUID =
            SynchedEntityData.defineId(YourselfEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER_NAME =
            SynchedEntityData.defineId(YourselfEntity.class, EntityDataSerializers.STRING);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.efdoppelganger.yourself"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS);

    private UUID lockedPlayer;
    private int weaponSwitchTicks;
    private int healCooldown;
    private int eatingTicks;
    private int remainingHealCount = -1;
    private ItemStack eatingItem = ItemStack.EMPTY;
    private ItemStack savedOffhandItem = ItemStack.EMPTY;
    private ItemStack initialPlayerOffhand = ItemStack.EMPTY;
    private boolean hasMultipleSwords = false;
    private boolean hasMultipleDaggers = false;
    private int lastPhase;
    private final List<ItemStack> rememberedWeapons = new ArrayList<>();
    private final List<ItemStack> rememberedHeals = new ArrayList<>();
    private final List<String> rememberedSkills = new ArrayList<>();

    public YourselfEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = DoppelConfig.XP_REWARD.get();
        this.remainingHealCount = DoppelConfig.MAX_HEAL_COUNT.get();
        this.weaponSwitchTicks = DoppelConfig.WEAPON_SWITCH_INTERVAL_TICKS.get();
        this.setCustomName(Component.translatable("entity.efdoppelganger.yourself"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.38D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER_UUID, "");
        this.entityData.define(OWNER_NAME, "");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, true, (living) -> living != this));
    }

    public static boolean isWeaponItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof net.minecraft.world.item.TieredItem) {
            return true;
        }
        if (stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) {
            return true;
        }
        if (stack.getItem() instanceof net.minecraft.world.item.TridentItem) {
            return true;
        }
        var cap = yesman.epicfight.world.capabilities.EpicFightCapabilities.getItemStackCapabilityOr(stack, null);
        if (cap instanceof yesman.epicfight.world.capabilities.item.WeaponCapability) {
            return true;
        }
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }

    public void copyFrom(Player player) {
        this.entityData.set(OWNER_UUID, player.getUUID().toString());
        this.entityData.set(OWNER_NAME, player.getGameProfile().getName());
        this.lockedPlayer = player.getUUID();

        double health = DoppelConfig.BASE_HEALTH.get();
        if (DoppelConfig.SCALE_WITH_PLAYER_MAX_HEALTH.get()) {
            double extraHearts = Math.max(0.0, player.getMaxHealth() / 2.0 - 10.0);
            health += extraHearts * DoppelConfig.HEALTH_PER_PLAYER_HEART.get();
        }
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        this.setHealth((float) health);

        if (DoppelConfig.COPY_ARMOR.get()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                    this.setItemSlot(slot, player.getItemBySlot(slot).copy());
                }
            }
        }

        // プレイヤーのインベントリ全体から武器と回復アイテムを網羅的に走査
        int swordCount = 0;
        int daggerCount = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            if (isWeaponItem(stack)) {
                if (this.rememberedWeapons.stream().noneMatch(s -> ItemStack.isSameItem(s, stack))) {
                    this.rememberedWeapons.add(stack.copy());
                }
                CapabilityItem cap = EpicFightCapabilities.getItemStackCapabilityOr(stack, null);
                WeaponCategory cat = cap != null ? cap.getWeaponCategory() : null;
                if (cat == CapabilityItem.WeaponCategories.DAGGER) {
                    daggerCount += stack.getCount();
                } else if (cat == CapabilityItem.WeaponCategories.SWORD || stack.getItem() instanceof SwordItem) {
                    swordCount += stack.getCount();
                }
            } else if (WeaponMemoryEvents.isHealingItem(stack)) {
                if (this.rememberedHeals.stream().noneMatch(s -> ItemStack.isSameItem(s, stack))) {
                    this.rememberedHeals.add(stack.copy());
                }
            }
        }

        this.initialPlayerOffhand = player.getOffhandItem().copy();
        if (!this.initialPlayerOffhand.isEmpty()) {
            if (isWeaponItem(this.initialPlayerOffhand)) {
                if (this.rememberedWeapons.stream().noneMatch(s -> ItemStack.isSameItem(s, this.initialPlayerOffhand))) {
                    this.rememberedWeapons.add(this.initialPlayerOffhand.copy());
                }
                CapabilityItem cap = EpicFightCapabilities.getItemStackCapabilityOr(this.initialPlayerOffhand, null);
                WeaponCategory cat = cap != null ? cap.getWeaponCategory() : null;
                if (cat == CapabilityItem.WeaponCategories.DAGGER) {
                    daggerCount += this.initialPlayerOffhand.getCount();
                } else if (cat == CapabilityItem.WeaponCategories.SWORD || this.initialPlayerOffhand.getItem() instanceof SwordItem) {
                    swordCount += this.initialPlayerOffhand.getCount();
                }
            } else if (WeaponMemoryEvents.isHealingItem(this.initialPlayerOffhand)) {
                if (this.rememberedHeals.stream().noneMatch(s -> ItemStack.isSameItem(s, this.initialPlayerOffhand))) {
                    this.rememberedHeals.add(this.initialPlayerOffhand.copy());
                }
            }
        }

        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && isWeaponItem(held)) {
            if (this.rememberedWeapons.stream().noneMatch(s -> ItemStack.isSameItem(s, held))) {
                this.rememberedWeapons.add(0, held.copy());
            }
            CapabilityItem cap = EpicFightCapabilities.getItemStackCapabilityOr(held, null);
            WeaponCategory cat = cap != null ? cap.getWeaponCategory() : null;
            if (cat == CapabilityItem.WeaponCategories.DAGGER) {
                daggerCount += held.getCount();
            } else if (cat == CapabilityItem.WeaponCategories.SWORD || held.getItem() instanceof SwordItem) {
                swordCount += held.getCount();
            }
        }

        WeaponMemory memory = WeaponMemoryEvents.get(player);
        if (memory != null) {
            for (ItemStack stack : memory.weapons()) {
                if (this.rememberedWeapons.stream().noneMatch(s -> ItemStack.isSameItem(s, stack))) {
                    this.rememberedWeapons.add(stack.copy());
                }
                CapabilityItem cap = EpicFightCapabilities.getItemStackCapabilityOr(stack, null);
                WeaponCategory cat = cap != null ? cap.getWeaponCategory() : null;
                if (cat == CapabilityItem.WeaponCategories.DAGGER) {
                    daggerCount++;
                } else if (cat == CapabilityItem.WeaponCategories.SWORD || stack.getItem() instanceof SwordItem) {
                    swordCount++;
                }
            }
            for (ItemStack stack : memory.heals()) {
                if (this.rememberedHeals.stream().noneMatch(s -> ItemStack.isSameItem(s, stack))) {
                    this.rememberedHeals.add(stack.copy());
                }
            }
            this.rememberedSkills.addAll(memory.skills());
            WeaponMemoryEvents.snapshotSkills(player, memory);
        }

        this.hasMultipleSwords = swordCount >= 2;
        this.hasMultipleDaggers = daggerCount >= 2;

        // 初期メイン武器の決定と二刀流・オフハンド装備
        ItemStack initialMain = (!held.isEmpty() && isWeaponItem(held))
                ? held.copy()
                : (!this.rememberedWeapons.isEmpty() ? this.rememberedWeapons.get(0).copy() : ItemStack.EMPTY);

        if (!initialMain.isEmpty()) {
            this.equipWeapon(initialMain);
        } else {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.OFFHAND, this.initialPlayerOffhand.copy());
        }

        // 回復アイテムが何もない場合のフォールバック（金リンゴ）
        if (this.rememberedHeals.isEmpty()) {
            this.rememberedHeals.add(new ItemStack(Items.GOLDEN_APPLE));
        }

        if (this.remainingHealCount < 0) {
            this.remainingHealCount = DoppelConfig.MAX_HEAL_COUNT.get();
        }
        this.xpReward = DoppelConfig.XP_REWARD.get();
        this.applyPhaseEffects(1);
    }

    public UUID getOwnerUUID() {
        String raw = this.entityData.get(OWNER_UUID);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getOwnerName() {
        return this.entityData.get(OWNER_NAME);
    }

    public boolean hasGuardSkill() {
        return this.rememberedSkills.stream().anyMatch(s -> s.toLowerCase().contains("guard") || s.toLowerCase().contains("parry"));
    }

    public boolean hasDodgeSkill() {
        return this.rememberedSkills.stream().anyMatch(s -> s.toLowerCase().contains("dodge") || s.toLowerCase().contains("step") || s.toLowerCase().contains("roll"));
    }

    public int getPhase() {
        float ratio = this.getHealth() / this.getMaxHealth();
        if (ratio > 0.66F) {
            return 1;
        }
        if (ratio > 0.33F) {
            return 2;
        }
        return 3;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.lockedPlayer == null || this.getOwnerUUID() == null) {
            Player nearest = this.level().getNearestPlayer(this, 64.0D);
            if (nearest != null) {
                this.copyFrom(nearest);
                YourselfPatch patch = EpicFightCapabilities.getEntityPatch(this, YourselfPatch.class);
                if (patch != null) {
                    patch.rebuildCombatAi();
                }
            }
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        int phase = this.getPhase();
        if (this.tickCount % 100 == 0 || phase != this.lastPhase || !this.hasEffect(MobEffects.DAMAGE_BOOST)) {
            this.lastPhase = phase;
            this.applyPhaseEffects(phase);
        }

        // 飲食演出の進行
        if (this.eatingTicks > 0) {
            this.eatingTicks--;
            if (this.eatingTicks % 4 == 0) {
                boolean isDrink = this.eatingItem.getItem() instanceof PotionItem
                        || this.eatingItem.getUseAnimation() == UseAnim.DRINK;
                SoundEvent sound = isDrink ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT;
                this.playSound(sound, 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
                if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel && !this.eatingItem.isEmpty()) {
                    serverLevel.sendParticles(
                            new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, this.eatingItem),
                            this.getX(), this.getEyeY() - 0.1, this.getZ(),
                            5, 0.1, 0.1, 0.1, 0.05);
                }
            }
            if (this.eatingTicks == 0) {
                this.finishEating();
            }
        }

        // 武器の定期的な切り替え（デフォルト20秒 = 400 ticks）
        if (this.weaponSwitchTicks > 0) {
            this.weaponSwitchTicks--;
        } else if (this.eatingTicks <= 0) {
            this.switchWeapon();
            this.weaponSwitchTicks = DoppelConfig.WEAPON_SWITCH_INTERVAL_TICKS.get();
        }

        // 回復アイテムの使用判定（HP条件以下 & 回数残あり & クールダウン完了 & 飲食中でない）
        if (this.healCooldown > 0) {
            this.healCooldown--;
        } else if (this.eatingTicks <= 0 && this.remainingHealCount > 0 && !this.rememberedHeals.isEmpty()
                && this.getHealth() / this.getMaxHealth() * 100.0F <= DoppelConfig.HEAL_BELOW_PERCENT.get()) {
            this.startEating();
        }

        if (this.lockedPlayer != null) {
            Player locked = this.level().getPlayerByUUID(this.lockedPlayer);
            LivingEntity target = this.getTarget();
            if (locked != null && locked.isAlive() && (target == null || !target.isAlive() || (target instanceof Player && target != locked))) {
                if (!(target instanceof Monster)) {
                    this.setTarget(locked);
                }
            }
        }

        // ターゲットから20ブロック以上離れた場合の即時テレポート（クールダウンなし）＆スプリント猛ダッシュ
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null && currentTarget.isAlive()) {
            this.setSprinting(true);
            double distSq = this.distanceToSqr(currentTarget);
            if (distSq >= 400.0D) {
                this.teleportToTarget(currentTarget);
            }
        } else {
            this.setSprinting(false);
        }
    }

    private void teleportToTarget(LivingEntity target) {
        if (this.level().isClientSide || target == null) {
            return;
        }
        // ターゲットの背後約2.5ブロックの位置を基準にする
        Vec3 look = target.getLookAngle();
        double targetX = target.getX() - look.x * 2.5D;
        double targetY = target.getY();
        double targetZ = target.getZ() - look.z * 2.5D;

        BlockPos targetPos = BlockPos.containing(targetX, targetY, targetZ);
        boolean foundSafePos = false;
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos checkPos = targetPos.above(dy);
            if (this.level().getBlockState(checkPos.below()).isSolidRender(this.level(), checkPos.below())
                    && !this.level().getBlockState(checkPos).isSolidRender(this.level(), checkPos)
                    && !this.level().getBlockState(checkPos.above()).isSolidRender(this.level(), checkPos.above())) {
                targetX = checkPos.getX() + 0.5D;
                targetY = checkPos.getY();
                targetZ = checkPos.getZ() + 0.5D;
                foundSafePos = true;
                break;
            }
        }
        if (!foundSafePos) {
            targetX = target.getX();
            targetY = target.getY();
            targetZ = target.getZ();
        }

        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0D, this.getZ(), 35, 0.5D, 0.8D, 0.5D, 0.1D);
            this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);

            this.teleportTo(targetX, targetY, targetZ);

            serverLevel.sendParticles(ParticleTypes.PORTAL, targetX, targetY + 1.0D, targetZ, 35, 0.5D, 0.8D, 0.5D, 0.1D);
            this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
        } else {
            this.teleportTo(targetX, targetY, targetZ);
        }

        this.lookAt(target, 360.0F, 360.0F);
    }

    private void applyPhaseEffects(int phase) {
        this.removeEffect(MobEffects.DAMAGE_BOOST);
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60 * 60 * 1000, phase - 1, true, true));
    }

    public static boolean isDualWieldableWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CapabilityItem cap = EpicFightCapabilities.getItemStackCapabilityOr(stack, null);
        WeaponCategory cat = cap != null ? cap.getWeaponCategory() : null;
        if (cat == CapabilityItem.WeaponCategories.SWORD || cat == CapabilityItem.WeaponCategories.DAGGER || cat == CapabilityItem.WeaponCategories.UCHIGATANA) {
            return true;
        }
        if (cat == CapabilityItem.WeaponCategories.GREATSWORD || cat == CapabilityItem.WeaponCategories.TACHI
                || cat == CapabilityItem.WeaponCategories.LONGSWORD || cat == CapabilityItem.WeaponCategories.SPEAR
                || cat == CapabilityItem.WeaponCategories.AXE) {
            return false;
        }
        if (stack.getItem() instanceof SwordItem) {
            return true;
        }
        String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
        if (name.contains("sword") || name.contains("dagger") || name.contains("blade") || name.contains("knife")) {
            return !name.contains("great") && !name.contains("long") && !name.contains("heavy") && !name.contains("colossal");
        }
        return false;
    }

    public static boolean isDaggerType(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CapabilityItem cap = EpicFightCapabilities.getItemStackCapabilityOr(stack, null);
        WeaponCategory cat = cap != null ? cap.getWeaponCategory() : null;
        if (cat == CapabilityItem.WeaponCategories.DAGGER) return true;
        String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
        return name.contains("dagger") || name.contains("knife");
    }

    public static boolean isSwordType(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isDaggerType(stack)) return false;
        return isDualWieldableWeapon(stack);
    }

    public void equipWeapon(ItemStack mainWeapon) {
        this.setItemSlot(EquipmentSlot.MAINHAND, mainWeapon.copy());

        boolean canDual = isDualWieldableWeapon(mainWeapon);
        int chance = DoppelConfig.DUAL_WIELD_CHANCE_PERCENT.get();
        boolean rollSuccess = (chance >= 100) || (chance > 0 && this.random.nextInt(100) < chance);

        if (canDual && rollSuccess) {
            ItemStack dualPartner = this.findDualWieldPartner(mainWeapon);
            this.setItemSlot(EquipmentSlot.OFFHAND, dualPartner);
        } else {
            // 二刀流不採用（確率外れ）、または両手武器等の場合は元のオフハンド（盾等）を装備
            this.setItemSlot(EquipmentSlot.OFFHAND, this.initialPlayerOffhand.copy());
        }
    }

    private ItemStack findDualWieldPartner(ItemStack main) {
        if (main.isEmpty() || !isDualWieldableWeapon(main)) {
            return ItemStack.EMPTY;
        }

        boolean isDagger = isDaggerType(main);
        boolean isSword = isSwordType(main);

        // 1. rememberedWeapons から、main と異なる同カテゴリ武器を探索
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack weapon : this.rememberedWeapons) {
            if (weapon.isEmpty() || ItemStack.isSameItemSameTags(weapon, main)) {
                continue;
            }
            if (isDagger && isDaggerType(weapon)) {
                candidates.add(weapon);
            } else if (isSword && isSwordType(weapon)) {
                candidates.add(weapon);
            }
        }

        if (!candidates.isEmpty()) {
            return candidates.get(this.random.nextInt(candidates.size())).copy();
        }

        // 2. プレイヤーの初期オフハンドが同カテゴリ武器であれば、それをパートナーとして採用
        if (!this.initialPlayerOffhand.isEmpty()) {
            if (isDagger && isDaggerType(this.initialPlayerOffhand)) {
                return this.initialPlayerOffhand.copy();
            } else if (isSword && isSwordType(this.initialPlayerOffhand)) {
                return this.initialPlayerOffhand.copy();
            }
        }

        // 3. 剣が1本だけの場合でも、メイン武器を複製して両手二刀流を成立させる！
        return main.copy();
    }

    public void reapplyEquipment() {
        ItemStack main = this.getMainHandItem();
        if (main.isEmpty() && !this.rememberedWeapons.isEmpty()) {
            main = this.rememberedWeapons.get(0);
        }
        if (!main.isEmpty()) {
            this.equipWeapon(main);
        }
    }

    private void switchWeapon() {
        if (this.rememberedWeapons.isEmpty()) {
            return;
        }
        ItemStack current = this.getMainHandItem();
        List<ItemStack> candidates = this.rememberedWeapons.stream()
                .filter(s -> !ItemStack.isSameItem(s, current))
                .toList();
        ItemStack next = candidates.isEmpty()
                ? this.rememberedWeapons.get(0)
                : candidates.get(this.random.nextInt(candidates.size()));

        this.equipWeapon(next);
        this.playSound(SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 1.0F);

        YourselfPatch patch = EpicFightCapabilities.getEntityPatch(this, YourselfPatch.class);
        if (patch != null) {
            patch.rebuildCombatAi();
        }
    }

    private void startEating() {
        if (this.rememberedHeals.isEmpty()) {
            return;
        }
        this.eatingItem = this.rememberedHeals.get(this.random.nextInt(this.rememberedHeals.size())).copy();
        this.savedOffhandItem = this.getItemBySlot(EquipmentSlot.OFFHAND).copy();
        this.setItemSlot(EquipmentSlot.OFFHAND, this.eatingItem.copy());
        this.eatingTicks = 25; // 1.25秒間モグモグ食べる
        this.healCooldown = DoppelConfig.HEAL_COOLDOWN_TICKS.get();
    }

    private void finishEating() {
        float percent = DoppelConfig.HEAL_AMOUNT_PERCENT.get() / 100.0F;
        this.heal(Math.max(12.0F, this.getMaxHealth() * percent));
        this.playSound(SoundEvents.PLAYER_BURP, 0.9F, 1.0F);
        if (this.remainingHealCount > 0) {
            this.remainingHealCount--;
        }
        if (this.eatingItem.getItem().isEdible()) {
            var food = this.eatingItem.getItem().getFoodProperties(this.eatingItem, this);
            if (food != null) {
                food.getEffects().forEach(pair -> {
                    if (this.random.nextFloat() < pair.getSecond()) {
                        this.addEffect(new MobEffectInstance(pair.getFirst()));
                    }
                });
            }
        }
        // ポーション固有のエフェクト（即時回復・即時ダメージ・持続バフ/デバフ等、有害な効果も含む）
        List<MobEffectInstance> potionEffects = PotionUtils.getMobEffects(this.eatingItem);
        for (MobEffectInstance effect : potionEffects) {
            if (effect.getEffect().isInstantenous()) {
                effect.getEffect().applyInstantenousEffect(this, this, this, effect.getAmplifier(), 1.0D);
            } else {
                this.addEffect(new MobEffectInstance(effect));
            }
        }
        // オフハンドを元のアイテムに戻す
        this.setItemSlot(EquipmentSlot.OFFHAND, this.savedOffhandItem.copy());
        this.eatingItem = ItemStack.EMPTY;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && this.lockedPlayer == null) {
            this.lockedPlayer = player.getUUID();
        }
        return super.hurt(source, amount);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(name != null ? name : Component.translatable("entity.efdoppelganger.yourself"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("OwnerUUID", this.entityData.get(OWNER_UUID));
        tag.putString("OwnerName", this.entityData.get(OWNER_NAME));
        if (this.lockedPlayer != null) {
            tag.putUUID("LockedPlayer", this.lockedPlayer);
        }
        tag.putInt("RemainingHealCount", this.remainingHealCount);
        tag.put("Weapons", writeList(this.rememberedWeapons));
        tag.put("Heals", writeList(this.rememberedHeals));
        tag.putInt("SkillsCount", this.rememberedSkills.size());
        for (int i = 0; i < this.rememberedSkills.size(); i++) {
            tag.putString("Skill" + i, this.rememberedSkills.get(i));
        }
        if (!this.initialPlayerOffhand.isEmpty()) {
            tag.put("InitialPlayerOffhand", this.initialPlayerOffhand.save(new CompoundTag()));
        }
        tag.putBoolean("HasMultipleSwords", this.hasMultipleSwords);
        tag.putBoolean("HasMultipleDaggers", this.hasMultipleDaggers);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(OWNER_UUID, tag.getString("OwnerUUID"));
        this.entityData.set(OWNER_NAME, tag.getString("OwnerName"));
        if (tag.hasUUID("LockedPlayer")) {
            this.lockedPlayer = tag.getUUID("LockedPlayer");
        }
        if (tag.contains("RemainingHealCount")) {
            this.remainingHealCount = tag.getInt("RemainingHealCount");
        } else {
            this.remainingHealCount = DoppelConfig.MAX_HEAL_COUNT.get();
        }
        this.rememberedWeapons.clear();
        this.rememberedHeals.clear();
        this.rememberedSkills.clear();
        readList(tag.getList("Weapons", 10), this.rememberedWeapons);
        readList(tag.getList("Heals", 10), this.rememberedHeals);
        int count = tag.getInt("SkillsCount");
        for (int i = 0; i < count; i++) {
            this.rememberedSkills.add(tag.getString("Skill" + i));
        }
        if (tag.contains("InitialPlayerOffhand")) {
            this.initialPlayerOffhand = ItemStack.of(tag.getCompound("InitialPlayerOffhand"));
        } else {
            this.initialPlayerOffhand = ItemStack.EMPTY;
        }
        this.hasMultipleSwords = tag.getBoolean("HasMultipleSwords");
        this.hasMultipleDaggers = tag.getBoolean("HasMultipleDaggers");
        this.xpReward = DoppelConfig.XP_REWARD.get();
    }

    private static ListTag writeList(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            list.add(stack.save(new CompoundTag()));
        }
        return list;
    }

    private static void readList(ListTag list, List<ItemStack> into) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                into.add(stack);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        int elixirs = DoppelConfig.ELIXIRS.get();
        int netherite = DoppelConfig.NETHERITE_BLOCKS.get();
        int diamonds = DoppelConfig.DIAMOND_BLOCKS.get();
        if (elixirs > 0) {
            this.spawnAtLocation(new ItemStack(ModItems.MIRROR_ELIXIR.get(), elixirs));
        }
        if (netherite > 0) {
            this.spawnAtLocation(new ItemStack(Items.NETHERITE_BLOCK, netherite));
        }
        if (diamonds > 0) {
            this.spawnAtLocation(new ItemStack(Items.DIAMOND_BLOCK, diamonds));
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
