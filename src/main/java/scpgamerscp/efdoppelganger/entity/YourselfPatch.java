package scpgamerscp.efdoppelganger.entity;

import com.google.common.collect.Maps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import scpgamerscp.efdoppelganger.config.DoppelConfig;
import scpgamerscp.efdoppelganger.util.AnimationSanitizer;
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
    protected void setWeaponMotions() {
        // 親クラス HumanoidMobPatch のモブ用アニメーション (BIPED_MOB_*) を完全遮断し、常に空にする
        this.weaponLivingMotions = Maps.newHashMap();
        this.weaponAttackMotions = Maps.newHashMap();
    }

    @Override
    public void updateHeldItem(CapabilityItem fromCap, CapabilityItem toCap, ItemStack from, ItemStack to, InteractionHand hand) {
        try {
            super.updateHeldItem(fromCap, toCap, from, to, hand);
        } catch (Throwable ignored) {
            // Epic Fightのモブ用オフハンドアトリビュート重複例外 (Modifier is already applied on this attribute!) などを完全吸収
        }
        if (this.original != null) {
            this.rebuildCombatAi();
        }
    }

    @Override
    public void onJoinWorld(YourselfEntity entity, EntityJoinLevelEvent event) {
        super.onJoinWorld(entity, event);
        entity.reapplyEquipment();
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
            return buildFistBehaviors();
        }

        ItemStack mainhand = entity.getMainHandItem();
        if (mainhand.isEmpty()) {
            return buildFistBehaviors();
        }

        CapabilityItem cap = EpicFightCapabilities.getItemStackCapabilityOr(mainhand, null);
        if (cap == null || cap.isEmpty()) {
            return buildFallbackBehaviors(mainhand);
        }

        WeaponCategory cat = cap.getWeaponCategory();
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mainhand.getItem());
        boolean isStandardMod = id == null || id.getNamespace().equals("minecraft") || id.getNamespace().equals("epicfight");

        // 二刀流状態（オフハンドにも武器を構えている）の場合はアドオン武器でも二刀流モーションを最優先適用
        ItemStack offhand = entity.getOffhandItem();
        boolean isDualWield = YourselfEntity.isDualWieldableWeapon(mainhand) && YourselfEntity.isDualWieldableWeapon(offhand);
        if (isDualWield) {
            return buildCategoryBehaviors(cap, mainhand, entity);
        }

        // 標準武器種（剣、短剣、太刀、大剣、ロングソード、槍、斧）かつバニラ/EpicFight標準武器は、最優先で完全なプレイヤーモーションを適用
        if (isStandardMod && isStandardWeaponCategory(cat)) {
            return buildCategoryBehaviors(cap, mainhand, entity);
        }

        // アドオン武器（WOM等）の動的取得（ダッシュ・独自コンボ・特殊技/アルティメット技）
        CombatBehaviors.Builder<HumanoidMobPatch<?>> dynamicBuilder = tryBuildDynamicAddonBehaviors(cap, mainhand, entity);
        if (dynamicBuilder != null) {
            return dynamicBuilder;
        }

        // 動的取得できなかった場合のカテゴリ別プレイヤーコンボ
        return buildCategoryBehaviors(cap, mainhand, entity);
    }

    private static boolean isStandardWeaponCategory(WeaponCategory cat) {
        return cat == CapabilityItem.WeaponCategories.SWORD
                || cat == CapabilityItem.WeaponCategories.DAGGER
                || cat == CapabilityItem.WeaponCategories.TACHI
                || cat == CapabilityItem.WeaponCategories.UCHIGATANA
                || cat == CapabilityItem.WeaponCategories.GREATSWORD
                || cat == CapabilityItem.WeaponCategories.LONGSWORD
                || cat == CapabilityItem.WeaponCategories.SPEAR
                || cat == CapabilityItem.WeaponCategories.AXE;
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
                        List<AnimationAccessor<? extends AttackAnimation>> filtered = new ArrayList<>();
                        for (Object o : list) {
                            if (o instanceof AnimationAccessor<?> acc) {
                                String name = acc.toString().toUpperCase();
                                // 空中攻撃や騎乗攻撃、ダッシュ攻撃を地上通常コンボから除外
                                if (!name.contains("AIR") && !name.contains("MOUNT") && !name.contains("DASH")) {
                                    filtered.add((AnimationAccessor<? extends AttackAnimation>) acc);
                                }
                            }
                        }
                        if (!filtered.isEmpty()) {
                            comboAnims = filtered;
                            break;
                        }
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

        // アドオン（WOM等）の特殊技（左右クリック技、アルティメット技、ダッシュ技）の動的抽出
        List<AnimationAccessor<? extends AttackAnimation>> addonDashAnims = new ArrayList<>();
        List<AnimationAccessor<? extends AttackAnimation>> addonSpecialAnims = new ArrayList<>();
        extractWomWeaponAnimations(mainhand, addonDashAnims, addonSpecialAnims);

        AnimationAccessor<? extends AttackAnimation> dashAnim = !addonDashAnims.isEmpty() ? addonDashAnims.get(0) : getCategoryDashAnimation(cap.getWeaponCategory());
        List<AnimationAccessor<? extends AttackAnimation>> finishers = new ArrayList<>(skillAnims);
        finishers.addAll(addonSpecialAnims);
        if (finishers.isEmpty()) {
            finishers.addAll(getCategorySkillAnimations(cap.getWeaponCategory()));
        }

        // アドオンMOD（WOM等）のPlayerキャストによるClassCastExceptionクラッシュを全アニメーションで完全防止
        AnimationSanitizer.sanitizeAccessors(comboAnims);
        AnimationSanitizer.sanitizeAccessors(skillAnims);
        AnimationSanitizer.sanitizeAccessors(addonDashAnims);
        AnimationSanitizer.sanitizeAccessors(addonSpecialAnims);
        AnimationSanitizer.sanitizeAccessor(dashAnim);
        AnimationSanitizer.sanitizeAccessors(finishers);

        int phase = entity.getPhase();
        int surpriseCooldown = (phase == 3) ? 20 : (phase == 2 ? 30 : 45);

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        double reach = Math.max(2.6D, weaponCap.getReach());
        if (YourselfEntity.isGunType(mainhand)) {
            reach = Math.max(12.0D, reach);
        }

        // 1. 中距離からの単発強襲必殺技シリーズ
        if (!finishers.isEmpty()) {
            builder.newBehaviorSeries(createSurpriseSkillSeries(finishers, reach, surpriseCooldown));
        }

        // 2. 「ダッシュ急接近 → 通常連撃 → 締め特殊必殺技」の流れるようなフルコンボ（隙ゼロ・完走保証）
        builder.newBehaviorSeries(createFullComboSeries(dashAnim, comboAnims, finishers, reach));

        return builder;
    }

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> buildCategoryBehaviors(
            CapabilityItem cap, ItemStack mainhand, YourselfEntity entity) {
        WeaponCategory cat = cap.getWeaponCategory();
        int phase = entity.getPhase();
        int surpriseCooldown = (phase == 3) ? 20 : (phase == 2 ? 30 : 45);

        ItemStack offhand = entity.getOffhandItem();
        boolean isDualFist = YourselfEntity.isFistType(mainhand) && YourselfEntity.isFistType(offhand);
        boolean isDualGun = YourselfEntity.isGunType(mainhand) && YourselfEntity.isGunType(offhand);
        boolean isDualDagger = YourselfEntity.isDaggerType(mainhand) && YourselfEntity.isDaggerType(offhand);
        boolean isDualSword = YourselfEntity.isSwordType(mainhand) && YourselfEntity.isSwordType(offhand);

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();

        // WOMなどのアドオン武器の必殺技・特殊技があれば二刀流フィニッシャーに組み込む
        List<AnimationAccessor<? extends AttackAnimation>> addonDash = new ArrayList<>();
        List<AnimationAccessor<? extends AttackAnimation>> addonSpecials = new ArrayList<>();
        extractWomWeaponAnimations(mainhand, addonDash, addonSpecials);
        AnimationSanitizer.sanitizeAccessors(addonSpecials);

        if (isDualGun) {
            // 二丁拳銃（エンダーブラスター等）: 動的アドオン射撃コンボ + レーザー必殺技
            CombatBehaviors.Builder<HumanoidMobPatch<?>> gunBehaviors = tryBuildDynamicAddonBehaviors(cap, mainhand, entity);
            if (gunBehaviors != null) {
                return gunBehaviors;
            }
        }

        if (isDualFist) {
            // 格闘両手スタイル（グローブ、ジャバウォッキー等）: 左右パンチラッシュ + アドオン必殺技
            List<AnimationAccessor<? extends AttackAnimation>> fFinishers = new ArrayList<>();
            if (!addonSpecials.isEmpty()) {
                fFinishers.addAll(addonSpecials);
            }
            AnimationAccessor<? extends AttackAnimation> fDash = !addonDash.isEmpty() ? addonDash.get(0) : null;
            builder.newBehaviorSeries(createFullComboSeries(
                    fDash,
                    List.of(Animations.FIST_AUTO1, Animations.FIST_AUTO2, Animations.FIST_AUTO3),
                    fFinishers,
                    2.2D));
            return builder;
        }

        if (isDualDagger) {
            // 短剣二刀流: 4連撃高速乱舞 + フィニッシャー
            List<AnimationAccessor<? extends AttackAnimation>> dFinishers = new ArrayList<>(List.of(Animations.EVISCERATE_FIRST, Animations.EVISCERATE_SECOND));
            if (!addonSpecials.isEmpty()) {
                dFinishers.addAll(addonSpecials);
            }
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.BLADE_RUSH_COMBO1), 2.2D, surpriseCooldown));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.DAGGER_DUAL_DASH,
                    List.of(Animations.DAGGER_DUAL_AUTO1, Animations.DAGGER_DUAL_AUTO2, Animations.DAGGER_DUAL_AUTO3, Animations.DAGGER_DUAL_AUTO4),
                    dFinishers,
                    2.2D));
            return builder;
        }

        if (isDualSword) {
            // 片手剣二刀流: 二刀流3連撃 + 円舞/アドオン必殺技
            List<AnimationAccessor<? extends AttackAnimation>> sFinishers = new ArrayList<>();
            if (!addonSpecials.isEmpty()) {
                sFinishers.addAll(addonSpecials);
            } else {
                sFinishers.add(Animations.DANCING_EDGE);
            }
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.SWEEPING_EDGE), 2.6D, surpriseCooldown));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.SWORD_DUAL_DASH,
                    List.of(Animations.SWORD_DUAL_AUTO1, Animations.SWORD_DUAL_AUTO2, Animations.SWORD_DUAL_AUTO3),
                    sFinishers,
                    2.6D));
            return builder;
        }

        if (cat == CapabilityItem.WeaponCategories.TACHI || cat == CapabilityItem.WeaponCategories.UCHIGATANA) {
            // 太刀: 抜刀ダッシュ急襲、および ダッシュ→通常3連→抜刀術フルコンボ
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.BATTOJUTSU_DASH), 3.2D, surpriseCooldown));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.TACHI_DASH,
                    List.of(Animations.TACHI_AUTO1, Animations.TACHI_AUTO2, Animations.TACHI_AUTO3),
                    List.of(Animations.BATTOJUTSU),
                    3.2D));
        } else if (cat == CapabilityItem.WeaponCategories.GREATSWORD) {
            // 大剣: 薙ぎ払い急襲、および ダッシュ→重撃2連→旋風撃フルコンボ
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.STEEL_WHIRLWIND), 3.4D, surpriseCooldown));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.GREATSWORD_DASH,
                    List.of(Animations.GREATSWORD_AUTO1, Animations.GREATSWORD_AUTO2),
                    List.of(Animations.STEEL_WHIRLWIND),
                    3.4D));
        } else if (cat == CapabilityItem.WeaponCategories.LONGSWORD) {
            // ロングソード: ラッシング急襲、および ダッシュ→通常3連→ラッシングテンポ2連突きフルコンボ
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.RUSHING_TEMPO1), 3.0D, surpriseCooldown));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.LONGSWORD_DASH,
                    List.of(Animations.LONGSWORD_AUTO1, Animations.LONGSWORD_AUTO2, Animations.LONGSWORD_AUTO3),
                    List.of(Animations.RUSHING_TEMPO1, Animations.RUSHING_TEMPO2),
                    3.0D));
        } else if (cat == CapabilityItem.WeaponCategories.DAGGER) {
            // 短剣（片手スタイル）: ダッシュ→通常連撃→裂傷連撃フルコンボ
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.BLADE_RUSH_COMBO1), 2.2D, surpriseCooldown));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.DAGGER_DASH,
                    List.of(Animations.DAGGER_AUTO1, Animations.DAGGER_AUTO2, Animations.DAGGER_AUTO3),
                    List.of(Animations.EVISCERATE_FIRST, Animations.EVISCERATE_SECOND),
                    2.2D));
        } else if (cat == CapabilityItem.WeaponCategories.AXE) {
            // 斧: ギロチン強襲、および ダッシュ→通常2連→断頭台叩きつけフルコンボ
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.THE_GUILLOTINE), 2.6D, surpriseCooldown));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.SWORD_DASH,
                    List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2),
                    List.of(Animations.THE_GUILLOTINE),
                    2.6D));
        } else if (cat == CapabilityItem.WeaponCategories.SPEAR) {
            // 槍: ハートピアサー強襲、および ダッシュ→両手2連→雷撃突きフルコンボ
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.HEARTPIERCER), 3.6D, surpriseCooldown));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.SPEAR_DASH,
                    List.of(Animations.SPEAR_TWOHAND_AUTO1, Animations.SPEAR_TWOHAND_AUTO2),
                    List.of(Animations.WRATHFUL_LIGHTING),
                    3.6D));
        } else {
            // 片手剣（片手スタイル）: ダッシュ→通常3連→円舞フルコンボ
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.SWEEPING_EDGE), 2.6D, surpriseCooldown));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.SWORD_DASH,
                    List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3),
                    List.of(Animations.DANCING_EDGE),
                    2.6D));
        }

        return builder;
    }

    private static AnimationAccessor<? extends AttackAnimation> getCategoryDashAnimation(WeaponCategory cat) {
        if (cat == CapabilityItem.WeaponCategories.TACHI || cat == CapabilityItem.WeaponCategories.UCHIGATANA) {
            return Animations.TACHI_DASH;
        } else if (cat == CapabilityItem.WeaponCategories.GREATSWORD) {
            return Animations.GREATSWORD_DASH;
        } else if (cat == CapabilityItem.WeaponCategories.LONGSWORD) {
            return Animations.LONGSWORD_DASH;
        } else if (cat == CapabilityItem.WeaponCategories.DAGGER) {
            return Animations.DAGGER_DASH;
        } else if (cat == CapabilityItem.WeaponCategories.SPEAR) {
            return Animations.SPEAR_DASH;
        } else {
            return Animations.SWORD_DASH;
        }
    }

    private static List<AnimationAccessor<? extends AttackAnimation>> getCategorySkillAnimations(WeaponCategory cat) {
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
            return List.of(Animations.DANCING_EDGE);
        }
    }

    @SuppressWarnings("unchecked")
    private static void extractWomWeaponAnimations(
            ItemStack stack,
            List<AnimationAccessor<? extends AttackAnimation>> dashOut,
            List<AnimationAccessor<? extends AttackAnimation>> specialOut) {
        try {
            net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null || !id.getNamespace().equals("wom")) {
                return;
            }
            String path = id.getPath().toLowerCase();
            String simpleName = path.replaceAll("[^a-z0-9]", "");
            String capitalized = Character.toUpperCase(simpleName.charAt(0)) + simpleName.substring(1);
            String className = "reascer.wom.gameasset.animations.weapons.Anims" + capitalized;

            Class<?> animClass = null;
            try {
                animClass = Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                try {
                    animClass = Class.forName("reascer.wom.gameasset.WOMAnimations");
                } catch (ClassNotFoundException e) {
                    return;
                }
            }

            for (Field field : animClass.getDeclaredFields()) {
                if (AnimationAccessor.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object val = field.get(null);
                    if (val instanceof AnimationAccessor<?> acc) {
                        AnimationSanitizer.sanitizeAccessor(acc);
                        String name = field.getName().toUpperCase();
                        if (name.contains("DASH")) {
                            dashOut.add((AnimationAccessor<? extends AttackAnimation>) acc);
                        } else if (name.contains("SPECIAL") || name.contains("ULTIMATE") || name.contains("RELEASE")
                                || name.contains("COUNTER") || name.contains("SLASH") || name.contains("EXECUTE")) {
                            specialOut.add((AnimationAccessor<? extends AttackAnimation>) acc);
                        }
                    }
                }
            }

            try {
                Class<?> globalWom = Class.forName("reascer.wom.gameasset.WOMAnimations");
                for (Field field : globalWom.getDeclaredFields()) {
                    if (AnimationAccessor.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object val = field.get(null);
                        if (val instanceof AnimationAccessor<?> acc) {
                            AnimationSanitizer.sanitizeAccessor(acc);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> buildFistBehaviors() {
        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        builder.newBehaviorSeries(createFullComboSeries(
                null,
                List.of(Animations.FIST_AUTO1, Animations.FIST_AUTO2, Animations.FIST_AUTO3),
                List.of(),
                2.0D));
        return builder;
    }

    private CombatBehaviors.Builder<HumanoidMobPatch<?>> buildFallbackBehaviors(ItemStack stack) {
        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        if (stack.getItem() instanceof net.minecraft.world.item.AxeItem) {
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.THE_GUILLOTINE), 2.6D, 30));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.SWORD_DASH,
                    List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2),
                    List.of(Animations.THE_GUILLOTINE),
                    2.6D));
        } else {
            builder.newBehaviorSeries(createSurpriseSkillSeries(List.of(Animations.SWEEPING_EDGE), 2.6D, 30));
            builder.newBehaviorSeries(createFullComboSeries(
                    Animations.SWORD_DASH,
                    List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3),
                    List.of(Animations.DANCING_EDGE),
                    2.6D));
        }
        return builder;
    }

    private CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> createFullComboSeries(
            AnimationAccessor<? extends AttackAnimation> dashAnim,
            List<? extends AnimationAccessor<? extends AttackAnimation>> autoAnims,
            List<? extends AnimationAccessor<? extends AttackAnimation>> finishers,
            double reach) {
        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> series = CombatBehaviors.BehaviorSeries.builder();
        series.weight(160.0F);
        series.canBeInterrupted(false); // コンボ完走保証（途切れ防止）
        series.looping(false);
        series.cooldown(0); // コンボ終了後の隙（クールダウン）完全撤廃

        // 1. ダッシュ攻撃（届く距離 1.8D 〜 reach + 3.0D でのみ飛び込み発動。届かない遠距離では走って詰める）
        if (dashAnim != null) {
            series.nextBehavior(CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                    .animationBehavior(dashAnim)
                    .withinDistance(1.8D, reach + 3.0D));
        }

        // 2. 通常コンボ（プレイヤーがバックステップしても途切れないよう範囲を広げて確実に完走）
        double comboReach = Math.max(reach + 2.0D, 4.8D);
        for (AnimationAccessor<? extends AttackAnimation> anim : autoAnims) {
            series.nextBehavior(CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                    .animationBehavior(anim)
                    .withinDistance(0.0D, comboReach));
        }

        // 3. 締めのフィニッシャー・特殊技（確実に大技を叩き込む）
        if (finishers != null && !finishers.isEmpty()) {
            double finisherReach = Math.max(reach + 2.5D, 5.2D);
            for (AnimationAccessor<? extends AttackAnimation> finisher : finishers) {
                series.nextBehavior(CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                        .animationBehavior(finisher)
                        .withinDistance(0.0D, finisherReach));
            }
        }

        return series;
    }

    private CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> createSurpriseSkillSeries(
            List<? extends AnimationAccessor<? extends AttackAnimation>> skills,
            double reach,
            int cooldownTicks) {
        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> series = CombatBehaviors.BehaviorSeries.builder();
        series.weight(220.0F);
        series.canBeInterrupted(false);
        series.looping(false);
        series.cooldown(cooldownTicks);

        for (AnimationAccessor<? extends AttackAnimation> anim : skills) {
            series.nextBehavior(CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder()
                    .animationBehavior(anim)
                    .withinDistance(2.0D, reach + 3.0D));
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
