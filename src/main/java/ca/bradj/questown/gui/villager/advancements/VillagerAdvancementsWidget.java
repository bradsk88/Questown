package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class VillagerAdvancementsWidget extends GuiComponent {
    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("textures/gui/advancements/widgets.png");
    private static final int[] TEST_SPLIT_OFFSETS = new int[]{0, 10, -10, 25, -25};
    private static final int OBTAINED = 0;
    private static final int UNOBTAINED = 1;
    private final VillagerAdvancementsContent tab;
    final DisplayInfo display;
    private final FormattedCharSequence title;
    private final int width;
    private final List<FormattedCharSequence> description;
    private final Minecraft minecraft;
    public final JobID id;
    public final @Nullable JobID parentId;
    @Nullable
    private VillagerAdvancementsWidget parent;
    private final List<VillagerAdvancementsWidget> children = Lists.newArrayList();
    private final boolean active;
    private final int x;
    private final int y;

    public VillagerAdvancementsWidget(
            VillagerAdvancementsContent p_97255_,
            Minecraft p_97256_,
            DisplayInfo p_97258_,
            JobID id,
            boolean active,
            @Nullable JobID parentId
    ) {
        this.id = id;
        this.active = active;
        this.parentId = parentId;

        this.tab = p_97255_;
        this.display = p_97258_;
        this.minecraft = p_97256_;
        this.title = Language.getInstance().getVisualOrder(p_97256_.font.substrByWidth(p_97258_.getTitle(), 163));
        this.x = Mth.floor(p_97258_.getX() * 28.0F);
        this.y = Mth.floor(p_97258_.getY() * 27.0F);
        int l = 29 + p_97256_.font.width(this.title);
        this.description = Language.getInstance().getVisualOrder(this.findOptimalLines(ComponentUtils.mergeStyles(p_97258_.getDescription().copy(), Style.EMPTY.withColor(p_97258_.getFrame().getChatColor())), l));

        FormattedCharSequence formattedcharsequence;
        for(Iterator var9 = this.description.iterator(); var9.hasNext(); l = Math.max(l, p_97256_.font.width(formattedcharsequence))) {
            formattedcharsequence = (FormattedCharSequence)var9.next();
        }

        this.width = l + 3 + 5;
    }

    private static float getMaxWidth(StringSplitter p_97304_, List<FormattedText> p_97305_) {
        Stream<FormattedText> var10000 = p_97305_.stream();
        Objects.requireNonNull(p_97304_);
        return (float)var10000.mapToDouble(p_97304_::stringWidth).max().orElse(0.0);
    }

    private List<FormattedText> findOptimalLines(Component p_97309_, int p_97310_) {
        StringSplitter stringsplitter = this.minecraft.font.getSplitter();
        List<FormattedText> list = null;
        float f = Float.MAX_VALUE;
        int[] var6 = TEST_SPLIT_OFFSETS;
        int var7 = var6.length;

        for(int var8 = 0; var8 < var7; ++var8) {
            int i = var6[var8];
            List<FormattedText> list1 = stringsplitter.splitLines(p_97309_, p_97310_ - i, Style.EMPTY);
            float f1 = Math.abs(getMaxWidth(stringsplitter, list1) - (float)p_97310_);
            if (f1 <= 10.0F) {
                return list1;
            }

            if (f1 < f) {
                f = f1;
                list = list1;
            }
        }

        return list;
    }

    @Nullable
    private VillagerAdvancementsWidget getFirstVisibleParent(JobID p_97312_) {
        return this.tab.getWidget(p_97312_);
    }

    public void drawConnectivity(PoseStack p_97299_, int p_97300_, int p_97301_, boolean p_97302_) {
        if (this.parent != null) {
            int i = p_97300_ + this.parent.x + 13;
            int j = p_97300_ + this.parent.x + 26 + 4;
            int k = p_97301_ + this.parent.y + 13;
            int l = p_97300_ + this.x + 13;
            int i1 = p_97301_ + this.y + 13;
            int j1 = p_97302_ ? -16777216 : -1;
            if (p_97302_) {
                this.hLine(p_97299_, j, i, k - 1, j1);
                this.hLine(p_97299_, j + 1, i, k, j1);
                this.hLine(p_97299_, j, i, k + 1, j1);
                this.hLine(p_97299_, l, j - 1, i1 - 1, j1);
                this.hLine(p_97299_, l, j - 1, i1, j1);
                this.hLine(p_97299_, l, j - 1, i1 + 1, j1);
                this.vLine(p_97299_, j - 1, i1, k, j1);
                this.vLine(p_97299_, j + 1, i1, k, j1);
            } else {
                this.hLine(p_97299_, j, i, k, j1);
                this.hLine(p_97299_, l, j, i1, j1);
                this.vLine(p_97299_, j, i1, k, j1);
            }
        }

        Iterator var11 = this.children.iterator();

        while(var11.hasNext()) {
            VillagerAdvancementsWidget advancementwidget = (VillagerAdvancementsWidget)var11.next();
            advancementwidget.drawConnectivity(p_97299_, p_97300_, p_97301_, p_97302_);
        }

    }

    public void draw(PoseStack p_97267_, int p_97268_, int p_97269_) {
        if (!this.display.isHidden()) {
            int advancementwidgettype = active ? OBTAINED : UNOBTAINED;

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, WIDGETS_LOCATION);
            this.blit(p_97267_, p_97268_ + this.x + 3, p_97269_ + this.y, this.display.getFrame().getTexture(), 128 + advancementwidgettype * 26, 26, 26);
            this.minecraft.getItemRenderer().renderAndDecorateFakeItem(this.display.getIcon(), p_97268_ + this.x + 8, p_97269_ + this.y + 5);
        }

        Iterator var6 = this.children.iterator();

        while(var6.hasNext()) {
            VillagerAdvancementsWidget advancementwidget = (VillagerAdvancementsWidget)var6.next();
            advancementwidget.draw(p_97267_, p_97268_, p_97269_);
        }

    }

    public void addChild(VillagerAdvancementsWidget p_97307_) {
        this.children.add(p_97307_);
    }

    public void drawHover(PoseStack p_97271_, int p_97272_, int p_97273_, float p_97274_, int p_97275_, int p_97276_) {
        boolean flag = p_97275_ + p_97272_ + this.x + this.width + 26 >= this.tab.getScreen().width;
        boolean flag1 = 113 - p_97273_ - this.y - 26 <= 6 + this.description.size() * 9;
        int advancementwidgettype;
        int advancementwidgettype1;
        int advancementwidgettype2;
        advancementwidgettype = OBTAINED;
        advancementwidgettype1 = UNOBTAINED;
        advancementwidgettype2 = UNOBTAINED;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WIDGETS_LOCATION);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        int l = p_97273_ + this.y;
        int i1;
        if (flag) {
            i1 = p_97272_ + this.x - this.width + 26 + 6;
        } else {
            i1 = p_97272_ + this.x;
        }

        int j1 = 32 + this.description.size() * 9;
        if (!this.description.isEmpty()) {
            if (flag1) {
                this.render9Sprite(p_97271_, i1, l + 26 - j1, this.width, j1, 10, 200, 26, 0, 52);
            } else {
                this.render9Sprite(p_97271_, i1, l, this.width, j1, 10, 200, 26, 0, 52);
            }
        }

        this.blit(p_97271_, p_97272_ + this.x + 3, p_97273_ + this.y, this.display.getFrame().getTexture(), 128 + advancementwidgettype2 * 26, 26, 26);
        if (flag) {
            this.minecraft.font.drawShadow(p_97271_, this.title, (float)(i1 + 5), (float)(p_97273_ + this.y + 9), -1);
        } else {
            this.minecraft.font.drawShadow(p_97271_, this.title, (float)(p_97272_ + this.x + 32), (float)(p_97273_ + this.y + 9), -1);
        }

        int k1;
        if (flag1) {
            for(k1 = 0; k1 < this.description.size(); ++k1) {
                this.minecraft.font.draw(p_97271_, (FormattedCharSequence)this.description.get(k1), (float)(i1 + 5), (float)(l + 26 - j1 + 7 + k1 * 9), -5592406);
            }
        } else {
            for(k1 = 0; k1 < this.description.size(); ++k1) {
                this.minecraft.font.draw(p_97271_, (FormattedCharSequence)this.description.get(k1), (float)(i1 + 5), (float)(p_97273_ + this.y + 9 + 17 + k1 * 9), -5592406);
            }
        }

        this.minecraft.getItemRenderer().renderAndDecorateFakeItem(this.display.getIcon(), p_97272_ + this.x + 8, p_97273_ + this.y + 5);
    }

    protected void render9Sprite(PoseStack p_97288_, int p_97289_, int p_97290_, int p_97291_, int p_97292_, int p_97293_, int p_97294_, int p_97295_, int p_97296_, int p_97297_) {
        this.blit(p_97288_, p_97289_, p_97290_, p_97296_, p_97297_, p_97293_, p_97293_);
        this.renderRepeating(p_97288_, p_97289_ + p_97293_, p_97290_, p_97291_ - p_97293_ - p_97293_, p_97293_, p_97296_ + p_97293_, p_97297_, p_97294_ - p_97293_ - p_97293_, p_97295_);
        this.blit(p_97288_, p_97289_ + p_97291_ - p_97293_, p_97290_, p_97296_ + p_97294_ - p_97293_, p_97297_, p_97293_, p_97293_);
        this.blit(p_97288_, p_97289_, p_97290_ + p_97292_ - p_97293_, p_97296_, p_97297_ + p_97295_ - p_97293_, p_97293_, p_97293_);
        this.renderRepeating(p_97288_, p_97289_ + p_97293_, p_97290_ + p_97292_ - p_97293_, p_97291_ - p_97293_ - p_97293_, p_97293_, p_97296_ + p_97293_, p_97297_ + p_97295_ - p_97293_, p_97294_ - p_97293_ - p_97293_, p_97295_);
        this.blit(p_97288_, p_97289_ + p_97291_ - p_97293_, p_97290_ + p_97292_ - p_97293_, p_97296_ + p_97294_ - p_97293_, p_97297_ + p_97295_ - p_97293_, p_97293_, p_97293_);
        this.renderRepeating(p_97288_, p_97289_, p_97290_ + p_97293_, p_97293_, p_97292_ - p_97293_ - p_97293_, p_97296_, p_97297_ + p_97293_, p_97294_, p_97295_ - p_97293_ - p_97293_);
        this.renderRepeating(p_97288_, p_97289_ + p_97293_, p_97290_ + p_97293_, p_97291_ - p_97293_ - p_97293_, p_97292_ - p_97293_ - p_97293_, p_97296_ + p_97293_, p_97297_ + p_97293_, p_97294_ - p_97293_ - p_97293_, p_97295_ - p_97293_ - p_97293_);
        this.renderRepeating(p_97288_, p_97289_ + p_97291_ - p_97293_, p_97290_ + p_97293_, p_97293_, p_97292_ - p_97293_ - p_97293_, p_97296_ + p_97294_ - p_97293_, p_97297_ + p_97293_, p_97294_, p_97295_ - p_97293_ - p_97293_);
    }

    protected void renderRepeating(PoseStack p_97278_, int p_97279_, int p_97280_, int p_97281_, int p_97282_, int p_97283_, int p_97284_, int p_97285_, int p_97286_) {
        for(int i = 0; i < p_97281_; i += p_97285_) {
            int j = p_97279_ + i;
            int k = Math.min(p_97285_, p_97281_ - i);

            for(int l = 0; l < p_97282_; l += p_97286_) {
                int i1 = p_97280_ + l;
                int j1 = Math.min(p_97286_, p_97282_ - l);
                this.blit(p_97278_, j, i1, p_97283_, p_97284_, k, j1);
            }
        }

    }

    public boolean isMouseOver(int p_97260_, int p_97261_, int p_97262_, int p_97263_) {
        if (this.display.isHidden()) {
            return false;
        } else {
            int i = p_97260_ + this.x;
            int j = i + 26;
            int k = p_97261_ + this.y;
            int l = k + 26;
            return p_97262_ >= i && p_97262_ <= j && p_97263_ >= k && p_97263_ <= l;
        }
    }

    public void attachToParent() {
        if (this.parent == null && this.parentId != null) {
            // TODO: Can we link a child to multiple parents?
            this.parent = this.getFirstVisibleParent(this.parentId);
            if (this.parent != null) {
                this.parent.addChild(this);
            }
        }

    }

    public int getY() {
        return this.y;
    }

    public int getX() {
        return this.x;
    }
}
