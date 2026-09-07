package scpgamerscp.efdoppelganger.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ProjectileWeaponItem;
import scpgamerscp.efdoppelganger.config.DoppelConfig;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.utils.AttackResult;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.MobCombatBehaviors;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;
import yesman.epicfight.world.entity.ai.goal.TargetChasingGoal;

public class YourselfPatch extends HumanoidMobPatch<YourselfEntity> {
    public YourselfPatch() {
        super(Factions.NEUTRAL);
    }

    public YourselfPatch(YourselfEntity entity) {
        super(Factions.NEUTRAL);
    }

    @Override
    public void onConstructed(YourselfEntity entity) {
        super.onConstructed(entity);
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
        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = super.getHoldingItemWeaponMotionBuilder();
        if (builder != null) {
            return builder;
        }
        return this.original.getMainHandItem().isEmpty()
                ? MobCombatBehaviors.HUMANOID_FIST
                : MobCombatBehaviors.HUMANOID_ONEHAND_TOOLS;
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
