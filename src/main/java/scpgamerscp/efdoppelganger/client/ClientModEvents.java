package scpgamerscp.efdoppelganger.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import scpgamerscp.efdoppelganger.EFDoppelganger;
import scpgamerscp.efdoppelganger.registry.ModEntities;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.client.renderer.patched.entity.PHumanoidRenderer;

@Mod.EventBusSubscriber(modid = EFDoppelganger.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.YOURSELF.get(), YourselfRenderer::new);
    }

    @SubscribeEvent
    public static void patchedRenderers(PatchedRenderersEvent.Add event) {
        event.addPatchedEntityRenderer(ModEntities.YOURSELF.get(),
                entityType -> new PHumanoidRenderer<>(Meshes.BIPED, event.getContext(), entityType));
    }
}
