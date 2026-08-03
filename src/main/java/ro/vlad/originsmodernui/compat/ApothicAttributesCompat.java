package ro.vlad.originsmodernui.compat;

import dev.shadowsoffire.apothic_attributes.client.ModifierSource;
import dev.shadowsoffire.apothic_attributes.client.ModifierSourceType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import ro.vlad.originsmodernui.OriginsModernUI;
import ro.vlad.originsmodernui.client.ArchitectIcons;

import java.util.Comparator;
import java.util.function.BiConsumer;

/**
 * Optional Apothic Attributes integration.
 *
 * <p>The Attributes GUI normally displays a question-mark icon for persistent
 * modifiers whose source it cannot identify. This source type claims only
 * modifiers from Origin Architect and renders the matching stat icon.</p>
 */
public final class ApothicAttributesCompat {
    private static boolean initialized;

    private ApothicAttributesCompat() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        // Touching the field registers the source type with Apothic Attributes.
        SourceTypes.ORIGIN_ARCHITECT.toString();
    }

    private static final class SourceTypes {
        private static final ModifierSourceType<AttributeModifier> ORIGIN_ARCHITECT =
                ModifierSourceType.register(new ModifierSourceType<>() {
                    @Override
                    public void extract(LivingEntity entity,
                                        BiConsumer<AttributeModifier, ModifierSource<?>> output) {
                        entity.getAttributes().getSyncableAttributes().forEach(instance -> {
                            for (AttributeModifier modifier : instance.getModifiers()) {
                                if (OriginsModernUI.MOD_ID.equals(modifier.id().getNamespace())) {
                                    output.accept(modifier, new ArchitectModifierSource(modifier));
                                }
                            }
                        });
                    }

                    @Override
                    public int getPriority() {
                        // Run before the generic unknown-source fallback.
                        return 100;
                    }
                });
    }

    private static final class ArchitectModifierSource extends ModifierSource<AttributeModifier> {
        private ArchitectModifierSource(AttributeModifier modifier) {
            super(SourceTypes.ORIGIN_ARCHITECT,
                    Comparator.comparing(mod -> mod.id().toString()), modifier);
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int x, int y) {
            ResourceLocation texture = iconFor(this.data.id());
            graphics.pose().pushPose();
            // Apothic's source slot is eight pixels wide. Draw the 16x16 icon at
            // half scale so it remains crisp and aligned with vanilla sources.
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            ArchitectIcons.draw(graphics, texture, x * 2, y * 2, 16);
            graphics.pose().popPose();
        }
    }

    private static ResourceLocation iconFor(ResourceLocation modifierId) {
        String path = modifierId.getPath();
        if (path.startsWith("offense_")) return ArchitectIcons.OFFENSE;
        if (path.startsWith("defense_")) return ArchitectIcons.DEFENSE;
        if (path.startsWith("utility_")) return ArchitectIcons.UTILITY;
        if (path.startsWith("survival_")) return ArchitectIcons.SURVIVAL;
        return ArchitectIcons.LOGO;
    }
}
