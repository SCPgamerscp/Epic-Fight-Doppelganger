package scpgamerscp.efdoppelganger.entity;

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
import net.minecraft.world.level.Level;
import scpgamerscp.efdoppelganger.config.DoppelConfig;
import scpgamerscp.efdoppelganger.item.ModItems;
import scpgamerscp.efdoppelganger.memory.WeaponMemory;
import scpgamerscp.efdoppelganger.memory.WeaponMemoryEvents;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

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
    private ItemStack eatingItem = ItemStack.EMPTY;
    private ItemStack savedOffhandItem = ItemStack.EMPTY;
    private int lastPhase;
    private final List<ItemStack> rememberedWeapons = new ArrayList<>();
    private final List<ItemStack> rememberedHeals = new ArrayList<>();
    private final List<String> rememberedSkills = new ArrayList<>();

    public YourselfEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = DoppelConfig.XP_REWARD.get();
        this.setCustomName(Component.translatable("entity.efdoppelganger.yourself"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
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
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            if (isWeaponItem(stack)) {
                if (this.rememberedWeapons.stream().noneMatch(s -> ItemStack.isSameItem(s, stack))) {
                    this.rememberedWeapons.add(stack.copy());
                }
            } else if (WeaponMemoryEvents.isHealingItem(stack)) {
                if (this.rememberedHeals.stream().noneMatch(s -> ItemStack.isSameItem(s, stack))) {
                    this.rememberedHeals.add(stack.copy());
                }
            }
        }

        // プレイヤーのメインハンドとオフハンドのコピー
        ItemStack held = player.getMainHandItem();
        if (isWeaponItem(held)) {
            if (this.rememberedWeapons.stream().noneMatch(s -> ItemStack.isSameItem(s, held))) {
                this.rememberedWeapons.add(0, held.copy());
            }
            this.setItemSlot(EquipmentSlot.MAINHAND, held.copy());
        } else if (!this.rememberedWeapons.isEmpty()) {
            // メインハンドが武器でない場合（肉や空手など）、記憶した武器の先頭を構える
            this.setItemSlot(EquipmentSlot.MAINHAND, this.rememberedWeapons.get(0).copy());
        }

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            this.setItemSlot(EquipmentSlot.OFFHAND, offhand.copy());
            if (isWeaponItem(offhand)) {
                if (this.rememberedWeapons.stream().noneMatch(s -> ItemStack.isSameItem(s, offhand))) {
                    this.rememberedWeapons.add(offhand.copy());
                }
            } else if (WeaponMemoryEvents.isHealingItem(offhand)) {
                if (this.rememberedHeals.stream().noneMatch(s -> ItemStack.isSameItem(s, offhand))) {
                    this.rememberedHeals.add(offhand.copy());
                }
            }
        }

        WeaponMemory memory = WeaponMemoryEvents.get(player);
        if (memory != null) {
            for (ItemStack stack : memory.weapons()) {
                if (this.rememberedWeapons.stream().noneMatch(s -> ItemStack.isSameItem(s, stack))) {
                    this.rememberedWeapons.add(stack.copy());
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

        // 回復アイテムが何もない場合のフォールバック（金リンゴ）
        if (this.rememberedHeals.isEmpty()) {
            this.rememberedHeals.add(new ItemStack(Items.GOLDEN_APPLE));
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
        if (phase != this.lastPhase || !this.hasEffect(MobEffects.DAMAGE_BOOST)) {
            this.lastPhase = phase;
            this.applyPhaseEffects(phase);
        }

        // 飲食演出の進行
        if (this.eatingTicks > 0) {
            this.eatingTicks--;
            if (this.eatingTicks % 4 == 0) {
                this.playSound(SoundEvents.GENERIC_EAT, 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
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

        // 武器の定期的な切り替え（3〜4秒 = 60〜80 ticks）
        if (this.weaponSwitchTicks > 0) {
            this.weaponSwitchTicks--;
        } else {
            this.switchWeapon();
            this.weaponSwitchTicks = 60 + this.random.nextInt(20);
        }

        // 回復アイテムの使用判定（HP50%以下 & クールダウン完了 & 飲食中でない）
        if (this.healCooldown > 0) {
            this.healCooldown--;
        } else if (this.eatingTicks <= 0 && !this.rememberedHeals.isEmpty()
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
    }

    private void applyPhaseEffects(int phase) {
        this.removeEffect(MobEffects.DAMAGE_BOOST);
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60 * 60, phase - 1, true, true));
    }

    private void switchWeapon() {
        if (this.rememberedWeapons.size() <= 1 && this.rememberedWeapons.isEmpty()) {
            return;
        }
        ItemStack current = this.getMainHandItem();
        List<ItemStack> candidates = this.rememberedWeapons.stream()
                .filter(s -> !ItemStack.isSameItem(s, current))
                .toList();
        ItemStack next = candidates.isEmpty()
                ? this.rememberedWeapons.get(0)
                : candidates.get(this.random.nextInt(candidates.size()));

        this.setItemSlot(EquipmentSlot.MAINHAND, next.copy());
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
        this.healCooldown = 220; // 次の回復まで11秒
    }

    private void finishEating() {
        this.heal(Math.max(12.0F, this.getMaxHealth() * 0.15F));
        this.playSound(SoundEvents.PLAYER_BURP, 0.9F, 1.0F);
        if (this.eatingItem.getItem().isEdible()) {
            var food = this.eatingItem.getItem().getFoodProperties(this.eatingItem, this);
            if (food != null) {
                food.getEffects().forEach(pair -> {
                    if (this.random.nextFloat() < pair.getSecond()) {
                        this.addEffect(pair.getFirst());
                    }
                });
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
        tag.put("Weapons", writeList(this.rememberedWeapons));
        tag.put("Heals", writeList(this.rememberedHeals));
        tag.putInt("SkillsCount", this.rememberedSkills.size());
        for (int i = 0; i < this.rememberedSkills.size(); i++) {
            tag.putString("Skill" + i, this.rememberedSkills.get(i));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(OWNER_UUID, tag.getString("OwnerUUID"));
        this.entityData.set(OWNER_NAME, tag.getString("OwnerName"));
        if (tag.hasUUID("LockedPlayer")) {
            this.lockedPlayer = tag.getUUID("LockedPlayer");
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
