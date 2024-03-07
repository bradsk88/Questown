package ca.bradj.questown.gui.villager.advancements;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

public class VillagerAdvancementsComponent extends GuiComponent {
    private final Minecraft minecraft;
    private final VillagerAdvancementsScreen screen;
    private final DisplayInfo display;
    private final ItemStack icon;
    private final Component title;
    private final VillagerAdvancementsWidget root;
    private final Map<ResourceLocation, VillagerAdvancementsWidget> widgets;
    private double scrollX;
    private double scrollY;
    private int minX;
    private int minY;
    private int maxX;
    private int maxY;
    private float fade;
    private boolean centered;

    public VillagerAdvancementsComponent(
            Minecraft minecraft,
            VillagerAdvancementsScreen screen,
            ResourceLocation advancement,
            DisplayInfo p_97150_
    ) {
        this.widgets = Maps.newLinkedHashMap();
        this.minX = Integer.MAX_VALUE;
        this.minY = Integer.MAX_VALUE;
        this.maxX = Integer.MIN_VALUE;
        this.maxY = Integer.MIN_VALUE;
        this.minecraft = minecraft;
        this.screen = screen;
        this.display = p_97150_;
        this.icon = p_97150_.getIcon();
        this.title = p_97150_.getTitle();
        this.root = new VillagerAdvancementsWidget(this, minecraft, p_97150_);
        this.addWidget(this.root, advancement);
    }

    public Component getTitle() {
        return this.title;
    }

    public DisplayInfo getDisplay() {
        return this.display;
    }

    public void drawContents(PoseStack p_97164_) {
        if (!this.centered) {
            this.scrollX = (double)(117 - (this.maxX + this.minX) / 2);
            this.scrollY = (double)(56 - (this.maxY + this.minY) / 2);
            this.centered = true;
        }

        p_97164_.pushPose();
        p_97164_.translate(0.0, 0.0, 950.0);
        RenderSystem.enableDepthTest();
        RenderSystem.colorMask(false, false, false, false);
        fill(p_97164_, 4680, 2260, -4680, -2260, -16777216);
        RenderSystem.colorMask(true, true, true, true);
        p_97164_.translate(0.0, 0.0, -950.0);
        RenderSystem.depthFunc(518);
        fill(p_97164_, 234, 113, 0, 0, -16777216);
        RenderSystem.depthFunc(515);
        ResourceLocation resourcelocation = this.display.getBackground();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        if (resourcelocation != null) {
            RenderSystem.setShaderTexture(0, resourcelocation);
        } else {
            RenderSystem.setShaderTexture(0, TextureManager.INTENTIONAL_MISSING_TEXTURE);
        }

        int i = Mth.floor(this.scrollX);
        int j = Mth.floor(this.scrollY);
        int k = i % 16;
        int l = j % 16;

        for(int i1 = -1; i1 <= 15; ++i1) {
            for(int j1 = -1; j1 <= 8; ++j1) {
                blit(p_97164_, k + 16 * i1, l + 16 * j1, 0.0F, 0.0F, 16, 16, 16, 16);
            }
        }

        this.root.drawConnectivity(p_97164_, i, j, true);
        this.root.drawConnectivity(p_97164_, i, j, false);
        this.root.draw(p_97164_, i, j);
        RenderSystem.depthFunc(518);
        p_97164_.translate(0.0, 0.0, -950.0);
        RenderSystem.colorMask(false, false, false, false);
        fill(p_97164_, 4680, 2260, -4680, -2260, -16777216);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(515);
        p_97164_.popPose();
    }

    public void drawTooltips(PoseStack p_97184_, int p_97185_, int p_97186_, int p_97187_, int p_97188_) {
        p_97184_.pushPose();
        p_97184_.translate(0.0, 0.0, -200.0);
        fill(p_97184_, 0, 0, 234, 113, Mth.floor(this.fade * 255.0F) << 24);
        boolean flag = false;
        int i = Mth.floor(this.scrollX);
        int j = Mth.floor(this.scrollY);
        if (p_97185_ > 0 && p_97185_ < 234 && p_97186_ > 0 && p_97186_ < 113) {
            Iterator var9 = this.widgets.values().iterator();

            while(var9.hasNext()) {
                VillagerAdvancementsWidget advancementwidget = (VillagerAdvancementsWidget)var9.next();
                if (advancementwidget.isMouseOver(i, j, p_97185_, p_97186_)) {
                    flag = true;
                    advancementwidget.drawHover(p_97184_, i, j, this.fade, p_97187_, p_97188_);
                    break;
                }
            }
        }

        p_97184_.popPose();
        if (flag) {
            this.fade = Mth.clamp(this.fade + 0.02F, 0.0F, 0.3F);
        } else {
            this.fade = Mth.clamp(this.fade - 0.04F, 0.0F, 1.0F);
        }

    }

    private void addWidget(VillagerAdvancementsWidget p_97176_, ResourceLocation advancement) {
        this.widgets.put(advancement, p_97176_);
        int i = p_97176_.getX();
        int j = i + 28;
        int k = p_97176_.getY();
        int l = k + 27;
        this.minX = Math.min(this.minX, i);
        this.maxX = Math.max(this.maxX, j);
        this.minY = Math.min(this.minY, k);
        this.maxY = Math.max(this.maxY, l);
        Iterator var7 = this.widgets.values().iterator();

        while(var7.hasNext()) {
            VillagerAdvancementsWidget advancementwidget = (VillagerAdvancementsWidget)var7.next();
            advancementwidget.attachToParent();
        }

    }

    @Nullable
    public VillagerAdvancementsWidget getWidget(Advancement p_97181_) {
        return (VillagerAdvancementsWidget)this.widgets.get(p_97181_);
    }

    public VillagerAdvancementsScreen getScreen() {
        return this.screen;
    }

    public void scroll(double p_97152_, double p_97153_) {
        if (this.maxX - this.minX > 234) {
            this.scrollX = Mth.clamp(this.scrollX + p_97152_, (double)(-(this.maxX - 234)), 0.0);
        }

        if (this.maxY - this.minY > 113) {
            this.scrollY = Mth.clamp(this.scrollY + p_97153_, (double)(-(this.maxY - 113)), 0.0);
        }

    }
}
