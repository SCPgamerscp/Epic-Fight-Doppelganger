package scpgamerscp.efdoppelganger.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import scpgamerscp.efdoppelganger.event.ModEvents;

import java.util.List;

public class VillagerMeatItem extends Item {
    public static final FoodProperties FOOD = new FoodProperties.Builder()
            .nutrition(4)
            .saturationMod(0.3F)
            .meat()
            .alwaysEat()
            .build();

    public VillagerMeatItem() {
        super(new Item.Properties().food(FOOD));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.efdoppelganger.villager_meat.desc").withStyle(ChatFormatting.RED));
    }
}
