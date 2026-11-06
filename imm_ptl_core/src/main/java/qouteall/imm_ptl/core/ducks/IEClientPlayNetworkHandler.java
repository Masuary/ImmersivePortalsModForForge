package qouteall.imm_ptl.core.ducks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.RegistryAccess;

import java.util.Map;
import java.util.UUID;

public interface IEClientPlayNetworkHandler {
    void ip_setWorld(ClientLevel world);

    Map<UUID, PlayerInfo> getPlayerListEntries();

    void setPlayerListEntries(Map<UUID, PlayerInfo> value);

    void portal_setRegistryManager(RegistryAccess.Frozen arg);
}
