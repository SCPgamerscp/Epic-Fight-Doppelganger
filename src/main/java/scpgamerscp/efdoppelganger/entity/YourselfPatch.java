package scpgamerscp.efdoppelganger.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import scpgamerscp.efdoppelganger.config.DoppelConfig;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.utils.AttackResult;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.MobCombatBehaviors;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponCategory;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;
import yesman.epicfight.world.entity.ai.goal.TargetChasingGoal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YourselfPatch extends HumanoidMobPatch<YourselfEntity> {
    public YourselfPatch() {
        super(Factions.NEUTRAL);
    }

    public YourselfPatch(YourselfEntity entity) {
        super(Factions.NEUTRAL);
    }

    @Override
    public void onJoinWorld(YourselfEntity entity, EntityJoinLevelEvent event) {
        super.onJoinWorld(entity, event);
        AttributeInstance stun = entity.getAttribute(EpicFightAttributes.STUN_ARMOR.get());
        if (stun != null) {
            stun.setBaseValue(DoppelConfig.STUN_ARMOR.get());
        }
        AttributeInstance impact = entity.getAttribute(EpicFightAttributes.IMPACT.get());
        if (impact != null) {
            impact.setBaseValue(2.4D);
        }
        AttributeInstance negation = entity.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get());
        if (negation != null) {
            negation.setBaseValue(10.0D);
        }
    }

    @Override
    public void initAnimator(Animator animator) {
        super.initAnimator(animator);
        animator.addLivingAnimation(LivingMotions.IDLE, Animations.BIPED_IDLE);
        animator.addLivingAnimation(LivingMotions.WALK, Animations.BIPED_WALK);
        animator.addLivingAnimation(LivingMotions.CHASE, Animations.BIPED_RUN);
        animator.addLivingAnimation(LivingMotions.RUN, Animations.BIPED_RUN);
        animator.addLivingAnimation(LivingMotions.FALL, Animations.BIPED_FALL);
        animator.addLivingAnimation(LivingMotions.DEATH, Animations.BIPED_DEATH);
        animator.addLivingAnimation(LivingMotions.JUMP, Animations.BIPED_JUMP);
        animator.addLivingAnimation(LivingMotions.KNEEL, Animations.BIPED_KNEEL);
        animator.addLivingAnimation(LivingMotions.SNEAK, Animations.BIPED_SNEAK);
        animator.addLivingAnimation(LivingMotions.SWIM, Animations.BIPED_SWIM);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        super.commonMobUpdateMotion(considerInaction);
    }

    @Override
    protected CombatBehaviors.Builder<HumanoidMobPatch<?>> getHoldingItemWeaponMotionBuilder() {
        YourselfEntity entity = this.getOriginal();
        if (entity == null) {
            return MobCombatBehaviors.HUMANOID_FIST;
        }

        ItemStack mainhand = entity.getMainHandItem();
        if (mainhand.isEmpty()) {
            return buildFistBehaviors();
        }

        CapabilityItem cap = EpicFightCapabilities.getItemStackCapabilityOr(mainhand, null);
        if (cap == null || cap.isEmpty()) {
            return buildFallbackBehaviors(mainhand);
        }

        // 1. アドオン武器等の動的取得（独自コンボ & 独自スキル）
        CombatBehaviors.Builder<HumanoidMobPatch<?>> dynamicBuilder = tryBuildDynamicAddonBehaviors(cap, mainhand, entity);
        if (dynamicBuilder != null) {
            return dynamicBuilder;
        }

        // 2. 武器カテゴリ別のプレイヤーコンボ & 必殺技（フォールバック）
        return buildCategoryBehaviors(cap, mainhand, entity);
    }

    @SuppressWarnings("unchecked")
    private CombatBehaviors.Builder<HumanoidMobPatch<?>> tryBuildDynamicAddonBehaviors(
            CapabilityItem cap, ItemStack mainhand, YourselfEntity entity) {
        if (!(cap instanceof WeaponCapability weaponCap)) {
            return null;
        }

        List<AnimationAccessor<? extends AttackAnimation>> comboAnims = null;
        try {
            Field autoAttackField = WeaponCapability.class.getDeclaredField("autoAttackMotions");
            autoAttackField.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) autoAttackField.get(weaponCap);
            if (map != null && !map.isEmpty()) {
                for (Object value : map.values()) {
                    if (value instanceof List<?> list && !list.isEmpty()) {
                        comboAnims = (List<AnimationAccessor<? extends AttackAnimation>>) list;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (comboAnims == null || comboAnims.isEmpty()) {
            return null;
        }

        // 必殺技（Innate Skill）アニメーションの動的取得
        List<AnimationAccessor<? extends AttackAnimation>> skillAnims = new ArrayList<>();
        try {
            Field innateSkillField = WeaponCapability.class.getDeclaredField("innateSkill");
            innateSkillField.setAccessible(true);
            Map<?, ?> skillMap = (Map<?, ?>) innateSkillField.get(weaponCap);
            if (skillMap != null && !skillMap.isEmpty()) {
                for (Object funcObj : skillMap.values()) {
                    if (funcObj instanceof java.util.function.Function) {
                        java.util.function.Function<ItemStack, Object> fn = (java.util.function.Function<ItemStack, Object>) funcObj;
                        Object skillObj = fn.apply(mainhand);
                        if (skillObj != null) {
                            Class<?> skillCls = skillObj.getClass();
                            while (skillCls != null && skillCls != Object.class) {
                                try {
                                    Field attackAnimField = skillCls.getDeclaredField("attackAnimation");
                                    attackAnimField.setAccessible(true);
                                    Object animVal = attackAnimField.get(skillObj);
                                    if (animVal instanceof AnimationAccessor<?> acc) {
                                        skillAnims.add((AnimationAccessor<? extends AttackAnimation>) acc);
                                        break;
                                    }
                                } catch (NoSuchFieldException e) {
                                    skillCls = skillCls.getSuperclass();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        int phase = entity.getPhase();
        int skillCooldown = (phase == 3) ? 70 : (phase == 2 ? 110 : 150);

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        double reach = Math.max(2.6D, weaponCap.getReach());

        // スキルが動的取得できた場合、必殺技シリーズを先頭に登録
        if (!skillAnims.isEmpty()) {
            builder.newBehaviorSeries(createSkillSeries(skillAnims, reach + 1.5D, skillCooldown));
        } else {
            // スキルが動的取得できなかった場合はカテゴリ別スキルで補填
            List<AnimationAccessor<? extends AttackAnimation>> catSkill = getCategorySkillAnimations(cap.getWeaponCategory());
            if (!catSkill.isEmpty()) {
                builder.newBehaviorSeries(createSkillSeries(catSkill, reach + 1.5D, skillCooldown));
            }
        }

        // アドオン独自のプレイヤーコンボ攻撃シリーズを登録
        builder.newBehaviorSeries(createComboSeries(comboAnims, reach));

        return builder;
    }

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> buildCategoryBehaviors(
            CapabilityItem cap, ItemStack mainhand, YourselfEntity entity) {
        WeaponCategory cat = cap.getWeaponCategory();
        int phase = entity.getPhase();
        int skillCooldown = (phase == 3) ? 70 : (phase == 2 ? 110 : 150);

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();

        if (cat == CapabilityItem.WeaponCategories.TACHI || cat == CapabilityItem.WeaponCategories.UCHIGATANA) {
            builder.newBehaviorSeries(createSkillSeries(List.of(Animations.BATTOJUTSU), 5.5D, skillCooldown));
            builder.newBehaviorSeries(createComboSeries(List.of(Animations.TACHI_AUTO1, Animations.TACHI_AUTO2, Animations.TACHI_AUTO3), 3.2D));
        } else if (cat == CapabilityItem.WeaponCategories.GREATSWORD) {
            builder.newBehaviorSeries(createSkillSeries(List.of(Animations.STEEL_WHIRLWIND), 4.5D, skillCooldown));
            builder.newBehaviorSeries(createComboSeries(List.of(Animations.GREATSWORD_AUTO1, Animations.GREATSWORD_AUTO2), 3.4D));
        } else if (cat == CapabilityItem.WeaponCategories.LONGSWORD) {
            builder.newBehaviorSeries(createSkillSeries(List.of(Animations.RUSHING_TEMPO1, Animations.RUSHING_TEMPO2), 4.8D, skillCooldown));
            builder.newBehaviorSeries(createComboSeries(List.of(Animations.LONGSWORD_AUTO1, Animations.LONGSWORD_AUTO2, Animations.LONGSWORD_AUTO3), 3.0D));
        } else if (cat == CapabilityItem.WeaponCategories.DAGGER) {
            builder.newBehaviorSeries(createSkillSeries(List.of(Animations.EVISCERATE_FIRST, Animations.EVISCERATE_SECOND), 3.5D, skillCooldown));
            builder.newBehaviorSeries(createComboSeries(List.of(Animations.DAGGER_AUTO1, Animations.DAGGER_AUTO2, Animations.DAGGER_AUTO3), 2.2D));
        } else if (cat == CapabilityItem.WeaponCategories.AXE) {
            builder.newBehaviorSeries(createSkillSeries(List.of(Animations.THE_GUILLOTINE), 4.0D, skillCooldown));
            builder.newBehaviorSeries(createComboSeries(List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2), 2.6D));
        } else if (cat == CapabilityItem.WeaponCategories.SPEAR) {
            builder.newBehaviorSeries(createSkillSeries(List.of(Animations.WRATHFUL_LIGHTING), 5.5D, skillCooldown));
            builder.newBehaviorSeries(createComboSeries(List.of(Animations.SPEAR_TWOHAND_AUTO1, Animations.SPEAR_TWOHAND_AUTO2), 3.6D));
        } else {
            // SWORD 及び通常片手武器
            builder.newBehaviorSeries(createSkillSeries(List.of(Animations.RUSHING_TEMPO1, Animations.RUSHING_TEMPO2, Animations.RUSHING_TEMPO3), 4.5D, skillCooldown));
            builder.newBehaviorSeries(createComboSeries(List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3), 2.6D));
        }

        return builder;
    }

    private List<AnimationAccessor<? extends AttackAnimation>> getCategorySkillAnimations(WeaponCategory cat) {
        if (cat == CapabilityItem.WeaponCategories.TACHI || cat == CapabilityItem.WeaponCategories.UCHIGATANA) {
            return List.of(Animations.BATTOJUTSU);
        } else if (cat == CapabilityItem.WeaponCategories.GREATSWORD) {
            return List.of(Animations.STEEL_WHIRLWIND);
        } else if (cat == CapabilityItem.WeaponCategories.LONGSWORD) {
            return List.of(Animations.RUSHING_TEMPO1, Animations.RUSHING_TEMPO2);
        } else if (cat == CapabilityItem.WeaponCategories.DAGGER) {
            return List.of(Animations.EVISCERATE_FIRST, Animations.EVISCERATE_SECOND);
        } else if (cat == CapabilityItem.WeaponCategories.AXE) {
            return List.of(Animations.THE_GUILLOTINE);
        } else if (cat == CapabilityItem.WeaponCategories.SPEAR) {
            return List.of(Animations.WRATHFUL_LIGHTING);
        } else {
            return List.of(Animations.RUSHING_TEMPO1, Animations.RUSHING_TEMPO2);
        }
    }

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> buildFistBehaviors() {
        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        builder.newBehaviorSeries(createComboSeries(List.of(Animations.FIST_AUTO1, Animations.FIST_AUTO2, Animations.FIST_AUTO3), 2.0D));
        return builder;
    }

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> buildFallbackBehaviors(ItemStack stack) {
        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        if (stack.getItem() instanceof net.minecraft.world.item.AxeItem) {
            builder.newBehaviorSeries(createSkillSeries(List.of(Animations.THE_GUILLOTINE), 4.0D, 120));
            builder.newBehaviorSeries(createComboSeries(List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2), 2.6D));
        } else {
            builder.newBehaviorSeries(createSkillSeries(List.of(Animations.RUSHING_TEMPO1, Animations.RUSHING_TEMPO2), 4.5D, 120));
            builder.newBehaviorSeries(createComboSeries(List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3), 2.6D));
        }
        return builder;
    }

    private CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> createComboSeries(
            List<? extends AnimationAccessor<? extends AttackAnimation>> animations,
            double reach) {
        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> series = CombatBehaviors.BehaviorSeries.builder();
        series.weight(100.0F);
        series.canBeInterrupted(true);
        series.looping(false);
        series.cooldown(10);
        for (AnimationAccessor<? extends AttackAnimation> anim : animations) {
            series.nextBehavior(CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                    .animationBehavior(anim)
                    .withinDistance(0.0D, reach));
        }
        return series;
    }

    private CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> createSkillSeries(
            List<? extends AnimationAccessor<? extends AttackAnimation>> animations,
            double reach,
            int cooldownTicks) {
        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> series = CombatBehaviors.BehaviorSeries.builder();
        series.weight(250.0F);
        series.canBeInterrupted(false);
        series.looping(false);
        series.cooldown(cooldownTicks);
        for (AnimationAccessor<? extends AttackAnimation> anim : animations) {
            series.nextBehavior(CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                    .animationBehavior(anim)
                    .withinDistance(0.0D, reach));
        }
        return series;
    }

    public void rebuildCombatAi() {
        this.original.goalSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(g -> g instanceof AnimatedAttackGoal || g instanceof TargetChasingGoal)
                .toList()
                .forEach(g -> this.original.goalSelector.removeGoal(g));
        this.setAIAsInfantry(this.original.getMainHandItem().getItem() instanceof ProjectileWeaponItem);
        this.modifyLivingMotionByCurrentItem(true);
    }

    @Override
    public AttackResult tryHurt(DamageSource damageSource, float amount) {
        YourselfEntity entity = this.getOriginal();
        if (entity != null && entity.hasDodgeSkill() && entity.getRandom().nextFloat() < 0.18F) {
            this.playAnimationSynchronized(Animations.BIPED_STEP_BACKWARD, 0.0F);
            return AttackResult.missed(amount);
        }
        return super.tryHurt(damageSource, amount);
    }
}
