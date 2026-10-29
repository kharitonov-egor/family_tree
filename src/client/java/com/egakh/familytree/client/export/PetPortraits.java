package com.egakh.familytree.client.export;

import com.egakh.familytree.data.AnimalRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import javax.imageio.ImageIO;

public final class PetPortraits {
    private PetPortraits() {}

    public static BufferedImage read(AnimalRecord record) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        String[] horseTextures = horseTextures(record);
        if (horseTextures != null) return horseProfile(client, horseTextures,
                "minecraft:donkey".equals(record.speciesId()) || "minecraft:mule".equals(record.speciesId()));
        boolean cat = "minecraft:cat".equals(record.speciesId());
        boolean wolf = "minecraft:wolf".equals(record.speciesId());
        if (!cat && !wolf) return null;
        Identifier variant = Identifier.tryParse(record.variantId() == null
                ? (cat ? "minecraft:tabby" : "minecraft:pale") : record.variantId());
        if (variant == null) return null;
        try {
            Identifier texture;
            if (cat) {
                var entry = client.level.registryAccess().lookupOrThrow(Registries.CAT_VARIANT)
                        .get(ResourceKey.create(Registries.CAT_VARIANT, variant));
                if (entry.isEmpty()) return null;
                texture = entry.get().value().adultAssetInfo().texturePath();
            } else {
                var entry = client.level.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT)
                        .get(ResourceKey.create(Registries.WOLF_VARIANT, variant));
                if (entry.isEmpty()) return null;
                texture = entry.get().value().adultInfo().tame().texturePath();
            }
            var resource = client.getResourceManager().getResource(texture);
            if (resource.isEmpty()) return null;
            try (var stream = resource.get().open()) {
                BufferedImage skin = ImageIO.read(stream);
                if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) return null;
                int scale = skin.getWidth() / 64;
                return wolf ? wolfHead(skin, scale) : skin.getSubimage(5 * scale, 4 * scale, 5 * scale, 4 * scale);
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            return null;
        }
    }

    /** The coat texture and an optional markings overlay, or null for species without a horse model. */
    private static String[] horseTextures(AnimalRecord record) {
        String coat = switch (record.speciesId()) {
            case "minecraft:donkey" -> "donkey";
            case "minecraft:mule" -> "mule";
            case "minecraft:skeleton_horse" -> "horse_skeleton";
            case "minecraft:zombie_horse" -> "horse_zombie";
            case "minecraft:horse" -> null;
            default -> "";
        };
        if (coat != null) return coat.isEmpty() ? null : new String[]{"textures/entity/horse/" + coat + ".png"};
        String[] variant = record.variantId() == null ? new String[0] : record.variantId().split("/", 2);
        String color = variant.length > 0 && variant[0].matches("[a-z_]+") ? variant[0].replace("_", "") : "white";
        String markings = variant.length > 1 && variant[1].matches("[a-z_]+") ? variant[1].replace("_", "") : "none";
        return markings.equals("none")
                ? new String[]{"textures/entity/horse/horse_" + color + ".png"}
                : new String[]{"textures/entity/horse/horse_" + color + ".png",
                        "textures/entity/horse/horse_markings_" + markings + ".png"};
    }

    private static BufferedImage horseProfile(Minecraft client, String[] textures, boolean longEars) {
        BufferedImage portrait = null;
        for (String path : textures) {
            Identifier texture = Identifier.tryParse("minecraft:" + path);
            if (texture == null) return portrait;
            var resource = client.getResourceManager().getResource(texture);
            if (resource.isEmpty()) return portrait;
            try (var stream = resource.get().open()) {
                BufferedImage skin = ImageIO.read(stream);
                if (skin == null || skin.getWidth() < 64 || skin.getWidth() != skin.getHeight()) return portrait;
                int scale = skin.getWidth() / 64;
                if (portrait == null) portrait = new BufferedImage(16 * scale, 16 * scale, BufferedImage.TYPE_INT_ARGB);
                if (portrait.getWidth() != 16 * scale) return portrait;
                drawHorseProfile(portrait, skin, scale, longEars);
            } catch (java.io.IOException | RuntimeException ignored) {
                return portrait;
            }
        }
        return portrait;
    }

    /**
     * Copies the west faces of the equine model's neck, head, muzzle, mane, and ears into a side profile.
     * Columns follow the model's z axis with the muzzle on the right; rows follow its y axis.
     */
    private static void drawHorseProfile(BufferedImage portrait, BufferedImage skin, int scale, boolean longEars) {
        int top = longEars ? -16 : -13;
        var graphics = portrait.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (longEars) copy(graphics, skin, scale, 0, 13, 1, 7, 2, -17 - top);
            else copy(graphics, skin, scale, 19, 17, 1, 3, 2, -13 - top);
            copy(graphics, skin, scale, 56, 38, 2, 16, 0, -11 - top);
            copy(graphics, skin, scale, 0, 42, 7, 12, 2, -6 - top);
            copy(graphics, skin, scale, 0, 20, 7, 5, 2, -11 - top);
            copy(graphics, skin, scale, 0, 30, 5, 5, 9, -11 - top);
        } finally {
            graphics.dispose();
        }
    }

    private static void copy(java.awt.Graphics2D graphics, BufferedImage skin, int scale,
                             int u, int v, int width, int height, int x, int y) {
        graphics.drawImage(skin, (x + 1) * scale, y * scale, (x + 1 + width) * scale, (y + height) * scale,
                u * scale, v * scale, (u + width) * scale, (v + height) * scale, null);
    }

    private static BufferedImage wolfHead(BufferedImage skin, int scale) {
        BufferedImage portrait = new BufferedImage(16 * scale, 16 * scale, BufferedImage.TYPE_INT_ARGB);
        var graphics = portrait.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            // Match the front faces of the wolf model's head, ears, and muzzle.
            // Double the model coordinates to keep the muzzle's half-pixel position.
            graphics.drawImage(skin, 2 * scale, 4 * scale, 14 * scale, 16 * scale,
                    4 * scale, 4 * scale, 10 * scale, 10 * scale, null);
            graphics.drawImage(skin, 2 * scale, 0, 6 * scale, 4 * scale,
                    17 * scale, 15 * scale, 19 * scale, 17 * scale, null);
            graphics.drawImage(skin, 10 * scale, 0, 14 * scale, 4 * scale,
                    17 * scale, 15 * scale, 19 * scale, 17 * scale, null);
            graphics.drawImage(skin, 5 * scale, 10 * scale, 11 * scale, 16 * scale,
                    4 * scale, 14 * scale, 7 * scale, 17 * scale, null);
        } finally {
            graphics.dispose();
        }
        return portrait;
    }
}
