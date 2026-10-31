package com.egakh.familytree.client.relatives;

import com.egakh.familytree.client.keybind.FamilyTreeKeybinds;
import com.egakh.familytree.client.platform.ClientTransport;
import com.egakh.familytree.client.settings.FamilyTreeClientSettings;
import com.egakh.familytree.network.NearbyRelatives;
import com.egakh.familytree.network.payloads.OpenFamilyTreeRequest;
import com.egakh.familytree.network.payloads.RelativesRequest;
import com.egakh.familytree.network.payloads.RelativesResult;
import com.egakh.familytree.util.CloseRelation;
import com.egakh.familytree.util.RelationText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
//? if <1.21.2 {
/*import org.joml.Vector3f;
*///?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * While the player holds the key, the tracked animal they look at and its relatives loaded nearby glow on this client
 * only, with a label over each and a line of particles to each parent and child. The server decides who is related
 * and what the player may see. Replies are cached until the key is released.
 */
public final class WorldRelatives {
    public static final double LABEL_RANGE_SQ = 64.0 * 64.0;
    private static final double PICK_RANGE = 48.0;
    private static final double LINE_RANGE = 48.0;
    private static final double LINE_STEP = 0.3;
    private static final float LINE_SIZE = 0.8F;
    private static final int REQUEST_INTERVAL = 5;
    private static final int REPLY_TIMEOUT = 40;
    private static final int REFRESH_AFTER = 100;
    private static final int HINT_AFTER = 20;

    private record Highlight(int color, Component label, CloseRelation relation) {}
    private record Cached(RelativesResult result, Map<UUID, Highlight> highlights, long tick) {}
    private record Pending(UUID target, long tick) {}
    private record Link(Entity from, Entity to, int color) {}

    private static final Map<Entity, Highlight> SHOWN = new IdentityHashMap<>();
    private static final Map<UUID, Cached> CACHE = new HashMap<>();
    private static final Map<UUID, Pending> IN_FLIGHT = new HashMap<>();

    private static boolean held;
    private static boolean unavailable;
    private static long ticks;
    private static long heldSince;
    private static long lastRequest;
    private static UUID focus;
    private static UUID lastNotice;

    private WorldRelatives() {}

    /** True while the key is held and relatives are outlined, which is when an overlay would cover their labels. */
    public static boolean showing() {
        return held && !SHOWN.isEmpty();
    }

    public static int outlineColor(Entity entity) {
        if (SHOWN.isEmpty()) return 0;
        Highlight highlight = SHOWN.get(entity);
        return highlight == null ? 0 : highlight.color();
    }

    public static Component label(Entity entity) {
        if (SHOWN.isEmpty()) return null;
        Highlight highlight = SHOWN.get(entity);
        return highlight == null ? null : highlight.label();
    }

    public static void receive(RelativesResult result) {
        Pending pending = IN_FLIGHT.remove(result.requestId());
        if (pending == null || !held || result.statusValue() == RelativesResult.Status.BUSY) return;
        CACHE.put(pending.target(), new Cached(result, highlights(result), ticks));
    }

    public static void tick(Minecraft client) {
        ticks++;
        boolean down = client.level != null && client.player != null && FamilyTreeClientSettings.showRelatives()
                && FamilyTreeKeybinds.SHOW_RELATIVES.isDown();
        if (!down) {
            if (held) clear();
            return;
        }
        if (!held) start(client);
        if (unavailable) return;
        IN_FLIGHT.values().removeIf(pending -> ticks - pending.tick() > REPLY_TIMEOUT);
        Entity looked = pick(client);
        if (looked != null) look(client, looked.getUUID());
        else if (focus == null && ticks - heldSince == HINT_AFTER) overlay(client, Component.translatable("familytree.relatives.look"));
        if (focus != null && needsRequest(focus)) request(focus);
        show(client);
    }

    public static void clear() {
        held = false;
        unavailable = false;
        focus = null;
        lastNotice = null;
        SHOWN.clear();
        CACHE.clear();
        IN_FLIGHT.clear();
    }

    private static void start(Minecraft client) {
        held = true;
        heldSince = ticks;
        lastRequest = ticks - REQUEST_INTERVAL;
        if (!ClientTransport.canSend(OpenFamilyTreeRequest.TYPE)) {
            unavailable = true;
            overlay(client, Component.translatable("familytree.relatives.no_server"));
        } else if (!ClientTransport.canSend(RelativesRequest.TYPE)) {
            unavailable = true;
            overlay(client, Component.translatable("familytree.relatives.outdated"));
        }
    }

    private static void look(Minecraft client, UUID looked) {
        Cached cached = CACHE.get(looked);
        if (cached == null) {
            request(looked);
            return;
        }
        RelativesResult result = cached.result();
        if (result.statusValue() == RelativesResult.Status.FOUND) {
            if (!looked.equals(focus)) {
                focus = looked;
                lastNotice = looked;
                overlay(client, NearbyRelatives.summary(result));
            }
        } else if (!looked.equals(lastNotice)) {
            lastNotice = looked;
            overlay(client, switch (result.statusValue()) {
                case UNAVAILABLE -> Component.translatable("familytree.relatives.unavailable");
                case NOT_ALLOWED -> Component.translatable("familytree.permission.denied", "familytree.relatives");
                default -> Component.translatable("familytree.relatives.not_tracked");
            });
        }
    }

    private static boolean needsRequest(UUID target) {
        Cached cached = CACHE.get(target);
        return cached == null || ticks - cached.tick() >= REFRESH_AFTER;
    }

    private static void request(UUID target) {
        if (ticks - lastRequest < REQUEST_INTERVAL) return;
        for (Pending pending : IN_FLIGHT.values()) if (pending.target().equals(target)) return;
        UUID requestId = UUID.randomUUID();
        lastRequest = ticks;
        IN_FLIGHT.put(requestId, new Pending(target, ticks));
        ClientTransport.send(new RelativesRequest(requestId, target));
    }

    private static Map<UUID, Highlight> highlights(RelativesResult result) {
        Map<UUID, Highlight> highlights = new HashMap<>();
        if (result.statusValue() != RelativesResult.Status.FOUND) return highlights;
        for (RelativesResult.Relative relative : result.relatives()) {
            CloseRelation relation = CloseRelation.byOrdinal(relative.relation());
            if (relation != null) highlights.put(relative.id(),
                    new Highlight(relation.color(), RelationText.label(relation, relative.name()), relation));
        }
        highlights.put(result.target(), new Highlight(RelationStyle.FOCUS, RelationStyle.focusLabel(result.targetName()), null));
        return highlights;
    }

    private static void show(Minecraft client) {
        SHOWN.clear();
        Cached cached = focus == null ? null : CACHE.get(focus);
        if (cached == null) return;
        Entity center = null;
        List<Entity> parents = new ArrayList<>();
        List<Entity> children = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            Highlight highlight = cached.highlights().get(entity.getUUID());
            if (highlight == null) continue;
            SHOWN.put(entity, highlight);
            if (highlight.relation() == null) center = entity;
            else if (highlight.relation() == CloseRelation.PARENT) parents.add(entity);
            else if (highlight.relation() == CloseRelation.CHILD) children.add(entity);
        }
        if (center == null || !FamilyTreeClientSettings.relativeLines()) return;
        for (Entity parent : parents) drawLine(client.level, new Link(parent, center, CloseRelation.PARENT.color()));
        for (Entity child : children) drawLine(client.level, new Link(center, child, CloseRelation.CHILD.color()));
    }

    /** Particles flow from parent to child along a low arch, so the line shows the direction, clears fences, and stays under the labels. */
    private static void drawLine(ClientLevel level, Link link) {
        Vec3 start = anchor(link.from());
        Vec3 end = anchor(link.to());
        double distance = start.distanceTo(end);
        if (distance < 0.5 || distance > LINE_RANGE) return;
        int steps = (int) Math.ceil(distance / LINE_STEP);
        double arch = Math.min(1.2, 0.1 * distance);
        //? if >=1.21.2 {
        DustParticleOptions dust = new DustParticleOptions(link.color(), LINE_SIZE);
        //?} else {
        /*DustParticleOptions dust = new DustParticleOptions(new Vector3f(((link.color() >> 16) & 0xFF) / 255F,
                ((link.color() >> 8) & 0xFF) / 255F, (link.color() & 0xFF) / 255F), LINE_SIZE);
        *///?}
        for (int i = 0; i <= steps; i++) {
            if (Math.floorMod(i - ticks, 3) != 0) continue;
            double t = i / (double) steps;
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t + arch * 4 * t * (1 - t);
            double z = start.z + (end.z - start.z) * t;
            //? if >=1.21.2 {
            level.addParticle(dust, true, false, x, y, z, 0, 0, 0);
            //?} else {
            /*level.addParticle(dust, true, x, y, z, 0, 0, 0);
            *///?}
        }
    }

    private static Vec3 anchor(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
    }

    private static Entity pick(Minecraft client) {
        Entity camera = client.getCameraEntity();
        if (camera == null) return null;
        Vec3 eye = camera.getEyePosition(1.0F);
        Vec3 look = camera.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(PICK_RANGE));
        HitResult block = client.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
        double limit = block.getType() == HitResult.Type.MISS ? PICK_RANGE * PICK_RANGE : eye.distanceToSqr(block.getLocation());
        AABB area = camera.getBoundingBox().expandTowards(look.scale(PICK_RANGE)).inflate(1.0);
        Entity mount = camera.getRootVehicle();
        Entity best = null;
        double bestDistance = limit;
        for (Entity entity : client.level.getEntities(camera, area,
                candidate -> candidate instanceof Animal && candidate.isAlive() && candidate.getRootVehicle() != mount)) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.25).clip(eye, end);
            if (hit.isEmpty()) continue;
            double distance = eye.distanceToSqr(hit.get());
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void overlay(Minecraft client, Component message) {
        if (client.player == null) return;
        //? if >=26.2 {
        /*client.player.sendOverlayMessage(message);
        *///?} else {
        client.gui.setOverlayMessage(message, false);
        //?}
    }
}
