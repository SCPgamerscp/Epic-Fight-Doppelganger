package scpgamerscp.efdoppelganger.util;

import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.property.AnimationEvent;
import yesman.epicfight.api.animation.property.AnimationParameters;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.lang.reflect.Field;
import java.util.*;

public final class AnimationSanitizer {
    private static final Set<Object> SANITIZED_OBJECTS = Collections.synchronizedSet(new HashSet<>());
    private static Field eventField;
    private static Field staticPropertiesField;
    private static Field attackPhasesField;
    private static Field phasePropertiesField;

    static {
        try {
            eventField = AnimationEvent.class.getDeclaredField("event");
            eventField.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            staticPropertiesField = StaticAnimation.class.getDeclaredField("properties");
            staticPropertiesField.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            attackPhasesField = AttackAnimation.class.getDeclaredField("phases");
            attackPhasesField.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            phasePropertiesField = AttackAnimation.Phase.class.getDeclaredField("properties");
            phasePropertiesField.setAccessible(true);
        } catch (Throwable ignored) {}
    }

    private AnimationSanitizer() {}

    public static void sanitizeAccessor(AnimationAccessor<?> accessor) {
        if (accessor == null || !SANITIZED_OBJECTS.add(accessor)) {
            return;
        }
        try {
            Object anim = accessor.get();
            if (anim instanceof StaticAnimation staticAnim) {
                sanitizeAnimation(staticAnim);
            }
        } catch (Throwable ignored) {}
    }

    public static void sanitizeAccessors(Collection<? extends AnimationAccessor<?>> accessors) {
        if (accessors == null) return;
        for (AnimationAccessor<?> acc : accessors) {
            sanitizeAccessor(acc);
        }
    }

    public static void sanitizeAnimation(StaticAnimation animation) {
        if (animation == null || !SANITIZED_OBJECTS.add(animation)) {
            return;
        }

        try {
            if (staticPropertiesField != null) {
                Map<?, ?> props = (Map<?, ?>) staticPropertiesField.get(animation);
                if (props != null && !props.isEmpty()) {
                    for (Object val : props.values()) {
                        sanitizeValue(val);
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (animation instanceof AttackAnimation attackAnim && attackPhasesField != null && phasePropertiesField != null) {
            try {
                AttackAnimation.Phase[] phases = (AttackAnimation.Phase[]) attackPhasesField.get(attackAnim);
                if (phases != null) {
                    for (AttackAnimation.Phase phase : phases) {
                        if (phase != null) {
                            Map<?, ?> phaseProps = (Map<?, ?>) phasePropertiesField.get(phase);
                            if (phaseProps != null && !phaseProps.isEmpty()) {
                                for (Object val : phaseProps.values()) {
                                    sanitizeValue(val);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void sanitizeValue(Object val) {
        if (val == null) return;

        if (val instanceof AnimationEvent<?, ?> animEvent) {
            wrapEvent(animEvent);
        } else if (val instanceof Object[] array) {
            for (Object item : array) {
                sanitizeValue(item);
            }
        } else if (val instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                sanitizeValue(item);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void wrapEvent(AnimationEvent<?, ?> animEvent) {
        if (eventField == null || !SANITIZED_OBJECTS.add(animEvent)) {
            return;
        }

        try {
            Object orig = eventField.get(animEvent);
            if (orig instanceof AnimationEvent.Event origEvent) {
                if (origEvent instanceof SafeEventWrapper) {
                    return;
                }
                eventField.set(animEvent, new SafeEventWrapper(origEvent));
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private record SafeEventWrapper(AnimationEvent.Event orig) implements AnimationEvent.Event {
        @Override
        public void fire(LivingEntityPatch patch, AssetAccessor accessor, AnimationParameters params) {
            try {
                orig.fire(patch, accessor, params);
            } catch (ClassCastException ignored) {
                // YourselfEntity cannot be cast to Player などのアドオンMODの不正キャストを安全に吸収
            } catch (Throwable ignored) {
                // その他の潜在的な例外も吸収してクラッシュを完全防止
            }
        }
    }
}
