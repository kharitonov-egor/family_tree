package com.egakh.familytree.mixin;

//? if <1.21 {
/*import com.egakh.familytree.text.ServerText;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Minecraft 1.20.1 reads the client's language and then discards it. The build lists this mixin only for 1.20.1.
@Mixin(ServerPlayer.class)
public abstract class ClientLanguageMixin {
    @Inject(method = "updateOptions", at = @At("HEAD"))
    private void familytree$rememberLanguage(ServerboundClientInformationPacket packet, CallbackInfo info) {
        ServerText.rememberLanguage(((ServerPlayer) (Object) this).getUUID(), packet.language());
    }
}
*///?}
