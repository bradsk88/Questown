package ca.bradj.questown.loader.neo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.net.URL;

/**
 * Temporary migration diagnostic (2026-09-29): prints, at atlas-stitch time and again
 * after the async texture load window, how each questown GUI texture resolves in the
 * 1.20.6 TextureManager. Remove once the villager-menu textures are confirmed good.
 */
public class TexDiag {

    public static void run(String label) {
        try {
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            String[] ids = {
                    "questown:textures/menu/gatherer/get_supplies",
                    "questown:textures/menu/tabs",
                    "questown:textures/menu/gatherer/menu",
                    "questown:textures/gui/bars",
                    "questown:textures/gui/advancements/widgets",
                    "questown:textures/entity/1",
            };
            for (String id : ids) {
                ResourceLocation rl = new ResourceLocation(id);
                AbstractTexture tex = tm.getTexture(rl);
                URL u1 = TexDiag.class.getResource("/assets/" + id.replaceFirst("^[^:]+:", ""));
                URL u2 = TexDiag.class.getClassLoader().getResource("assets/" + id.replaceFirst("^[^:]+:", ""));
                System.out.println(
                        "[tex-diag " + label + "] " + id
                                + " -> tex=" + tex.getClass().getSimpleName()
                                + " cl1=" + u1
                                + " cl2=" + u2
                );
            }
        } catch (Throwable t) {
            System.out.println("[tex-diag " + label + "] failed: " + t);
        }
    }
}
