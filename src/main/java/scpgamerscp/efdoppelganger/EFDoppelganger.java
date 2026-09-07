package scpgamerscp.efdoppelganger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scpgamerscp.efdoppelganger.config.DoppelConfig;
import scpgamerscp.efdoppelganger.event.ModEvents;
import scpgamerscp.efdoppelganger.item.ModItems;
import scpgamerscp.efdoppelganger.loot.ModLootModifiers;
import scpgamerscp.efdoppelganger.registry.ModCreativeTabs;
import scpgamerscp.efdoppelganger.registry.ModEntities;
import yesman.epicfight.gameasset.Armatures;

@Mod(EFDoppelganger.MOD_ID)
public class EFDoppelganger {
    public static final String MOD_ID = "efdoppelganger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public EFDoppelganger() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DoppelConfig.SPEC);

        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModLootModifiers.SERIALIZERS.register(modBus);

        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(ModEvents.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> Armatures.registerEntityTypeArmature(ModEntities.YOURSELF.get(), Armatures.BIPED));
    }
}
