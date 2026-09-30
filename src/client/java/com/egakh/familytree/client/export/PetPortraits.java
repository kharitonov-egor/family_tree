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
