package com.egakh.familytree.neoforge;

import com.egakh.familytree.client.FamilyTreeClient;
import com.egakh.familytree.network.payloads.*;

final class LegacyClientPackets {
    static void receive(SnapshotPagePayload packet) { FamilyTreeClient.receive(packet); }
    static void receive(RenamePetResult packet) { FamilyTreeClient.receive(packet); }
    static void receive(LinkParentsResult packet) { FamilyTreeClient.receive(packet); }
}
