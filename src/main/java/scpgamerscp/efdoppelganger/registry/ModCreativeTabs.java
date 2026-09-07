package scpgamerscp.efdoppelganger.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import scpgamerscp.efdoppelganger.EFDoppelganger;
import scpgamerscp.efdoppelganger.item.ModItems;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EFDoppelganger.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.efdoppelganger"))
            .icon(() -> new ItemStack(ModItems.VILLAGER_MEAT.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.VILLAGER_MEAT.get());
                output.accept(ModItems.MIRROR_ELIXIR.get());
                output.accept(ModItems.YOURSELF_SPAWN_EGG.get());
            })
            .build());

    private ModCreativeTabs() {}
}
