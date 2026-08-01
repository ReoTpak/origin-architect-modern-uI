package ro.vlad.originsmodernui.client;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.screen.OriginSelectionPresenter;
import com.cyberday1.neoorigins.screen.OriginSelectionScreen;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import com.cyberday1.neoorigins.screen.model.OriginListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Full-screen selection experience for NeoOrigins 2.2.21.
 *
 * The presenter remains the single source of truth, so addon origins and custom
 * selection layers are picked up automatically without hard-coded compatibility.
 */
public final class ModernOriginSelectionScreen extends Screen {
    private static final int BG_TOP = 0xFF080B12;
    private static final int BG_BOTTOM = 0xFF111927;
    private static final int PANEL = 0xFF141A24;
    private static final int PANEL_RAISED = 0xFF1A2230;
    private static final int PANEL_HOVER = 0xFF222D3D;
    private static final int PANEL_SELECTED = 0xFF203A4E;
    private static final int BORDER = 0xFF2B3748;
    private static final int BORDER_SOFT = 0xFF202A38;
    private static final int ACCENT = 0xFF75C8FF;
    private static final int ACCENT_STRONG = 0xFFA6DEFF;
    private static final int TEXT = 0xFFF4F7FA;
    private static final int TEXT_SOFT = 0xFFD3DAE4;
    private static final int MUTED = 0xFF8F9DAF;
    private static final int MUTED_DARK = 0xFF596679;
    private static final int SUCCESS = 0xFF74D6A0;

    private final boolean isOrb;
    private final boolean forceReselect;
    private final List<ResourceLocation> scopedLayers;
    private final OriginSelectionPresenter presenter = new OriginSelectionPresenter();

    private EditBox search;
    private Button confirm;
    private Button random;
    private Button sort;
    private Button filter;
    private Button favorite;
    private Button powersToggle;
    private Button back;
    private int listScroll;
    private int detailScroll;
    private float smoothListScroll;
    private float targetListScroll;
    private float smoothDetailScroll;
    private float targetDetailScroll;
    private long selectionChangedAt;
    private static final long TRANSITION_OUT_MS = 220L;
    private static final long TRANSITION_IN_MS = 380L;
    private TransitionStage transitionStage = TransitionStage.NONE;
    private long transitionStartedAt;
    private int transitionDirection = 1;
    private int maxDetailScroll;
    private OriginDetailViewModel detail = OriginDetailViewModel.EMPTY;
    private long openedAt;
    private SortMode sortMode = SortMode.DEFAULT;
    private FilterMode filterMode = FilterMode.ALL;
    private boolean powersExpanded = true;
    private static final Set<ResourceLocation> FAVORITES = new HashSet<>();
    private final Map<ResourceLocation, EntryMeta> entryMetaCache = new HashMap<>();
    private final Map<ResourceLocation, List<String>> tagCache = new HashMap<>();
    private final Map<ResourceLocation, String> searchTextCache = new HashMap<>();
    /** Persists the choices while NeoOrigins advances through separate layer screens. */
    private static final Map<ResourceLocation, SelectedChoice> SESSION_CHOICES = new LinkedHashMap<>();

    private int shellX;
    private int shellTop;
    private int shellBottom;
    private int leftW;
    private int gap;
    private int rightX;
    private int rightW;

    private ModernOriginSelectionScreen(boolean isOrb, boolean forceReselect, List<ResourceLocation> scopedLayers) {
        super(Component.translatable("screen.originsmodernui.title"));
        this.isOrb = isOrb;
        this.forceReselect = forceReselect;
        this.scopedLayers = List.copyOf(scopedLayers);
    }

    public static ModernOriginSelectionScreen from(OriginSelectionScreen original) {
        return new ModernOriginSelectionScreen(
                read(original, "isOrb", false),
                read(original, "forceReselect", false),
                read(original, "scopedLayers", List.of())
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Object object, String name, T fallback) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(object);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        presenter.setForceReselect(forceReselect);
        presenter.setScopedLayers(scopedLayers);
        if (!presenter.init()) {
            onClose();
            return;
        }
        presenter.buildRows();
        calculateLayout();

        // Keep search on its own row so its hint/value can never collide with
        // Sort or Filter on narrow windows. The second row is dedicated to controls.
        int filterW = 72;
        int sortW = Math.max(112, leftW - filterW - 34);
        int searchW = leftW - 28;
        search = new EditBox(font, shellX + 14, shellTop + 47, searchW, 20,
                Component.translatable("screen.originsmodernui.search"));
        search.setHint(Component.translatable("screen.originsmodernui.search_hint"));
        search.setValue(presenter.searchText());
        search.setResponder(value -> {
            presenter.setSearch("");
            listScroll = 0;
            targetListScroll = 0;
        });
        addRenderableWidget(search);

        sort = Button.builder(sortLabel(), b -> cycleSort())
                .bounds(shellX + 14, shellTop + 72, sortW, 20)
                .build();
        addRenderableWidget(sort);

        filter = Button.builder(filterLabel(), b -> cycleFilter())
                .bounds(shellX + leftW - filterW - 14, shellTop + 72, filterW, 20)
                .build();
        addRenderableWidget(filter);

        // Two distinct button rows prevent Powers/Favorite/Back from colliding
        // with Random/Select at common 1024x768 and narrower resolutions.
        int actionY = height - 43;
        int utilityY = actionY - 29;
        int actionGap = 8;
        int actionW = Math.max(92, Math.min(150, (rightW - 36 - actionGap) / 2));
        int actionStartX = rightX + rightW - actionW * 2 - actionGap;
        random = Button.builder(Component.translatable("screen.originsmodernui.random"), b -> randomSelection())
                .bounds(actionStartX, actionY, actionW, 24)
                .build();
        confirm = Button.builder(Component.translatable("screen.originsmodernui.select"), b -> confirmSelection())
                .bounds(actionStartX + actionW + actionGap, actionY, actionW, 24)
                .build();
        addRenderableWidget(random);
        addRenderableWidget(confirm);

        int utilityX = rightX + 18;
        int utilityGap = 6;
        int backW = 74;
        int favoriteW = 96;
        int powersW = Math.max(112, Math.min(150, rightX + rightW - 18 - (utilityX + backW + utilityGap + favoriteW + utilityGap)));
        back = Button.builder(Component.literal("← Back"), b -> backSelection())
                .bounds(utilityX, utilityY, backW, 24).build();
        favorite = Button.builder(Component.literal("☆ Favorite"), b -> toggleFavorite())
                .bounds(utilityX + backW + utilityGap, utilityY, favoriteW, 24).build();
        powersToggle = Button.builder(Component.literal("Powers: All"), b -> {
                    powersExpanded = !powersExpanded;
                    b.setMessage(Component.literal(powersExpanded ? "Powers: All" : "Powers: Collapsed"));
                    detailScroll = 0;
                }).bounds(utilityX + backW + utilityGap + favoriteW + utilityGap, utilityY, powersW, 24).build();
        addRenderableWidget(back);
        addRenderableWidget(favorite);
        addRenderableWidget(powersToggle);

        updateDetail();
    }

    private void calculateLayout() {
        int margin = width < 620 ? 10 : 18;
        shellTop = 58;
        shellBottom = height - 58;
        shellX = margin;
        int available = width - margin * 2;
        gap = width < 620 ? 8 : 12;
        leftW = Mth.clamp((int) (available * 0.39F), 196, 270);
        rightX = shellX + leftW + gap;
        rightW = width - margin - rightX;
    }

    private void cycleSort() {
        sortMode = sortMode.next();
        if (sort != null) sort.setMessage(sortLabel());
        listScroll = 0;
        targetListScroll = 0;
        smoothListScroll = 0;
    }

    private Component sortLabel() {
        return Component.literal("Sort: " + sortMode.shortLabel);
    }

    private void cycleFilter() {
        filterMode = filterMode.next();
        if (filter != null) filter.setMessage(filterLabel());
        listScroll = 0;
        targetListScroll = 0;
        smoothListScroll = 0;
    }

    private Component filterLabel() {
        return Component.literal(filterMode.shortLabel);
    }

    private void toggleFavorite() {
        ResourceLocation id = presenter.selectedOriginId();
        if (id == null) return;
        if (!FAVORITES.add(id)) FAVORITES.remove(id);
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        if (favorite == null) return;
        ResourceLocation id = presenter.selectedOriginId();
        favorite.active = id != null;
        favorite.setMessage(Component.literal(id != null && FAVORITES.contains(id) ? "★ Favorite" : "☆ Favorite"));
    }

    private List<OriginListEntry> visibleRows() {
        List<OriginListEntry> source = rowsForCurrentLayer();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        ArrayList<OriginListEntry> rows = new ArrayList<>();
        for (OriginListEntry row : source) {
            if (row.isSectionHeader()) {
                if (sortMode == SortMode.DEFAULT && filterMode == FilterMode.ALL) rows.add(row);
                continue;
            }
            EntryMeta meta = entryMeta(row);
            String rating = estimatedRating(meta);
            boolean matchesSearch = query.isEmpty() || searchableText(row).contains(query);
            boolean keep = matchesSearch && switch (filterMode) {
                case ALL -> true;
                case FAVORITES -> FAVORITES.contains(row.id());
                case TOP_RATED -> rating.equals("S") || rating.equals("A");
                case BEGINNER -> difficultyScore(meta) <= 2;
                case CHALLENGE -> difficultyScore(meta) >= 4;
            };
            if (keep) rows.add(row);
        }
        if (sortMode == SortMode.DEFAULT) return rows;
        Comparator<OriginListEntry> comparator = switch (sortMode) {
            case NAME_ASC -> Comparator.comparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            case NAME_DESC -> Comparator.comparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER).reversed();
            case RATING_DESC -> Comparator
                    .comparingInt((OriginListEntry row) -> ratingScore(entryMeta(row))).reversed()
                    .thenComparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            case RATING_ASC -> Comparator
                    .comparingInt((OriginListEntry row) -> ratingScore(entryMeta(row)))
                    .thenComparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            case IMPACT_DESC -> Comparator
                    .comparingInt((OriginListEntry row) -> entryMeta(row).impact().getDotCount()).reversed()
                    .thenComparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            case IMPACT_ASC -> Comparator
                    .comparingInt((OriginListEntry row) -> entryMeta(row).impact().getDotCount())
                    .thenComparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            case POWERS_DESC -> Comparator
                    .comparingInt((OriginListEntry row) -> entryMeta(row).powerCount()).reversed()
                    .thenComparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            case POWERS_ASC -> Comparator
                    .comparingInt((OriginListEntry row) -> entryMeta(row).powerCount())
                    .thenComparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            case DIFFICULTY_DESC -> Comparator
                    .comparingInt((OriginListEntry row) -> difficultyScore(entryMeta(row))).reversed()
                    .thenComparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            case DIFFICULTY_ASC -> Comparator
                    .comparingInt((OriginListEntry row) -> difficultyScore(entryMeta(row)))
                    .thenComparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            case NAMESPACE -> Comparator
                    .comparing(OriginListEntry::namespace, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(OriginListEntry::displayName, String.CASE_INSENSITIVE_ORDER);
        };
        rows.sort(comparator);
        return rows;
    }

    private EntryMeta entryMeta(OriginListEntry row) {
        return entryMetaCache.computeIfAbsent(row.id(), id -> {
            OriginDetailViewModel vm = OriginDetailViewModel.compute(id, isOrb);
            Impact impact = vm != OriginDetailViewModel.EMPTY && vm.origin() != null
                    ? vm.origin().impact() : Impact.NONE;
            int powers = vm != OriginDetailViewModel.EMPTY ? vm.powerNames().size() : 0;
            return new EntryMeta(impact, powers);
        });
    }

    private void randomSelection() {
        ResourceLocation id = presenter.randomId();
        if (id != null) select(id);
    }

    private void select(ResourceLocation id) {
        presenter.select(id);
        detailScroll = 0;
        targetDetailScroll = 0;
        smoothDetailScroll = 0;
        selectionChangedAt = System.currentTimeMillis();
        updateDetail();
    }

    private void backSelection() {
        if (transitionStage != TransitionStage.NONE || presenter.currentLayerIndex() <= 0) return;
        startLayerTransition(-1);
    }

    private void confirmSelection() {
        if (transitionStage != TransitionStage.NONE || presenter.selectedOriginId() == null || presenter.isDone()) return;
        rememberCurrentChoice();
        startLayerTransition(1);
    }

    private void startLayerTransition(int direction) {
        transitionDirection = direction < 0 ? -1 : 1;
        transitionStage = TransitionStage.OUT;
        transitionStartedAt = System.currentTimeMillis();
        setControlsEnabled(false);
    }

    private void performLayerChange() {
        if (transitionDirection < 0) {
            if (!presenter.back()) {
                finishLayerTransition();
                return;
            }
            if (!SESSION_CHOICES.isEmpty()) {
                ResourceLocation last = null;
                for (ResourceLocation id : SESSION_CHOICES.keySet()) last = id;
                if (last != null) SESSION_CHOICES.remove(last);
            }
        } else {
            presenter.confirm();
            if (presenter.isDone()) {
                Minecraft.getInstance().setScreen(null);
                return;
            }
        }

        presenter.buildRows();
        entryMetaCache.clear();
        tagCache.clear();
        searchTextCache.clear();
        listScroll = 0;
        targetListScroll = 0;
        smoothListScroll = 0;
        detailScroll = 0;
        targetDetailScroll = 0;
        smoothDetailScroll = 0;
        if (search != null) search.setValue("");
        updateDetail();
    }

    private void updateLayerTransition() {
        if (transitionStage == TransitionStage.NONE) return;
        long elapsed = System.currentTimeMillis() - transitionStartedAt;
        if (transitionStage == TransitionStage.OUT && elapsed >= TRANSITION_OUT_MS) {
            performLayerChange();
            if (Minecraft.getInstance().screen != this) return;
            transitionStage = TransitionStage.IN;
            transitionStartedAt = System.currentTimeMillis();
        } else if (transitionStage == TransitionStage.IN && elapsed >= TRANSITION_IN_MS) {
            finishLayerTransition();
        }
    }

    private void finishLayerTransition() {
        transitionStage = TransitionStage.NONE;
        transitionStartedAt = 0L;
        setControlsEnabled(true);
        updateDetail();
    }

    private void setControlsEnabled(boolean enabled) {
        if (search != null) search.active = enabled;
        if (sort != null) sort.active = enabled;
        if (filter != null) filter.active = enabled;
        if (random != null) random.active = enabled;
        if (favorite != null) favorite.active = enabled;
        if (powersToggle != null) powersToggle.active = enabled;
        if (confirm != null) confirm.active = enabled && presenter.selectedOriginId() != null;
        if (back != null) back.active = enabled && presenter.currentLayerIndex() > 0;
    }

    private float transitionProgress() {
        if (transitionStage == TransitionStage.NONE) return 1.0F;
        long duration = transitionStage == TransitionStage.OUT ? TRANSITION_OUT_MS : TRANSITION_IN_MS;
        return Mth.clamp((System.currentTimeMillis() - transitionStartedAt) / (float) duration, 0.0F, 1.0F);
    }

    private float easeInOutCubic(float t) {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
    }

    private float easeInCubic(float t) {
        return t * t * t;
    }

    /** A gentle overshoot makes the incoming layer settle like a polished RPG menu. */
    private float easeOutBack(float t) {
        final float c1 = 1.70158F;
        final float c3 = c1 + 1.0F;
        float x = t - 1.0F;
        return 1.0F + c3 * x * x * x + c1 * x * x;
    }

    private float smootherStep(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }


    private void rememberCurrentChoice() {
        ResourceLocation selected = presenter.selectedOriginId();
        if (selected == null || presenter.isDone() || presenter.currentLayerIndex() >= presenter.totalLayers()) return;
        ResourceLocation layerId = presenter.currentLayer().id();
        String layerName = presenter.currentLayer().name().getString();
        String choiceName = detail != OriginDetailViewModel.EMPTY && detail.origin() != null
                ? detail.origin().name().getString()
                : selected.getPath();
        SESSION_CHOICES.put(layerId, new SelectedChoice(layerName, choiceName));
    }

    private void updateDetail() {
        ResourceLocation selected = presenter.selectedOriginId();
        detail = selected == null ? OriginDetailViewModel.EMPTY : OriginDetailViewModel.compute(selected, isOrb);
        if (confirm != null) confirm.active = selected != null;
        if (back != null) back.active = presenter.currentLayerIndex() > 0;
        updateFavoriteButton();
        maxDetailScroll = 0;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLayerTransition();
        smoothListScroll += (targetListScroll - smoothListScroll) * 0.24F;
        smoothDetailScroll += (targetDetailScroll - smoothDetailScroll) * 0.22F;
        listScroll = Math.max(0, Math.round(smoothListScroll));
        detailScroll = Math.max(0, Math.round(smoothDetailScroll));
        graphics.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        renderAtmosphere(graphics);

        float rawProgress = transitionProgress();
        float slide;
        float vertical;
        float scale;
        float darkness;
        if (transitionStage == TransitionStage.OUT) {
            float eased = easeInCubic(rawProgress);
            slide = -transitionDirection * width * 0.22F * eased;
            vertical = -7.0F * smootherStep(rawProgress);
            scale = 1.0F - 0.025F * smootherStep(rawProgress);
            darkness = 0.46F * smootherStep(rawProgress);
        } else if (transitionStage == TransitionStage.IN) {
            float eased = easeOutBack(rawProgress);
            slide = transitionDirection * width * 0.20F * (1.0F - eased);
            vertical = 9.0F * (1.0F - smootherStep(rawProgress));
            scale = 0.965F + 0.035F * eased;
            darkness = 0.46F * (1.0F - smootherStep(rawProgress));
        } else {
            slide = 0.0F;
            vertical = 0.0F;
            scale = 1.0F;
            darkness = 0.0F;
        }

        graphics.pose().pushPose();
        // Scale around the centre instead of the top-left so the layer feels like
        // a single card moving through depth, rather than a flat panel teleporting.
        graphics.pose().translate(width * 0.5F + slide, height * 0.5F + vertical, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-width * 0.5F, -height * 0.5F, 0.0F);
        renderShells(graphics);

        // Vanilla widgets render before our labels to avoid third-party blur/composite
        // passes fading custom text while leaving EditBox/Button widgets sharp.
        super.render(graphics, mouseX, mouseY, partialTick);

        renderHeader(graphics);
        renderSelectionTrail(graphics);
        if (!presenter.isDone() && presenter.currentLayerIndex() < presenter.totalLayers()) {
            renderSidebar(graphics, mouseX, mouseY);
            renderDetails(graphics);
            renderFooter(graphics);
        }
        graphics.pose().popPose();

        if (darkness > 0.0F) {
            int alpha = Mth.clamp((int)(darkness * 255.0F), 0, 255);
            graphics.fill(0, 0, width, height, alpha << 24);
        }
        renderTransitionEffects(graphics, rawProgress);
    }

    /**
     * Cinematic layer-change pass: a soft accent wipe, a restrained centre flash,
     * and letterbox/vignette shading. Everything is drawn with vanilla primitives
     * so it remains compatible with NeoForge 21.1.241 and shader/resource packs.
     */
    private void renderTransitionEffects(GuiGraphics g, float progress) {
        if (transitionStage == TransitionStage.NONE) return;

        float phase = transitionStage == TransitionStage.OUT
                ? smootherStep(progress)
                : 1.0F - smootherStep(progress);

        // Moving accent seam. Forward and Back naturally travel opposite ways.
        float travel = transitionStage == TransitionStage.OUT ? phase : 1.0F - phase;
        int seamX = transitionDirection > 0
                ? Math.round(width * travel)
                : Math.round(width * (1.0F - travel));
        int glow = Math.round(34.0F * (float)Math.sin(Math.PI * Mth.clamp(phase, 0.0F, 1.0F)));
        if (glow > 0) {
            g.fill(seamX - 14, 0, seamX + 14, height, (glow << 24) | 0x0075C8FF);
            g.fill(seamX - 2, 0, seamX + 2, height, (Math.min(180, glow * 4) << 24) | 0x00A6DEFF);
        }

        // Brief central bloom at the hand-off point, deliberately subtle.
        float centrePulse = 1.0F - Math.abs(phase * 2.0F - 1.0F);
        int pulseAlpha = Mth.clamp(Math.round(centrePulse * 42.0F), 0, 42);
        if (pulseAlpha > 0) {
            int insetX = Math.max(0, width / 2 - width / 5);
            int insetY = Math.max(0, height / 2 - height / 4);
            g.fill(insetX, insetY, width - insetX, height - insetY,
                    (pulseAlpha << 24) | 0x0075C8FF);
        }

        // Thin cinematic bars make the transition feel deliberate without
        // reducing usable space once the animation finishes.
        int bar = Math.round(10.0F * centrePulse);
        if (bar > 0) {
            int barAlpha = Mth.clamp(70 + Math.round(80.0F * centrePulse), 0, 150);
            g.fill(0, 0, width, bar, barAlpha << 24);
            g.fill(0, height - bar, width, height, barAlpha << 24);
        }
    }

    private void renderAtmosphere(GuiGraphics g) {
        int t = (int) ((System.currentTimeMillis() - openedAt) / 35L);
        int drift = t % Math.max(1, width + 180) - 90;
        g.fill(drift - 120, 0, drift + 120, 1, 0x3375C8FF);
        g.fill(width - 170, 0, width, height, 0x12000000);
        for (int i = 0; i < 7; i++) {
            int y = 24 + i * 37;
            int x = width - 26 - (i % 3) * 9;
            g.fill(x, y, x + 1, y + 1, i % 2 == 0 ? 0x5575C8FF : 0x33596779);
        }
    }

    private void renderShells(GuiGraphics g) {
        // Outer borders and subtle inset lines provide depth without transparency.
        g.fill(shellX - 1, shellTop - 1, shellX + leftW + 1, shellBottom + 1, BORDER);
        g.fill(shellX, shellTop, shellX + leftW, shellBottom, PANEL);
        g.fill(rightX - 1, shellTop - 1, rightX + rightW + 1, shellBottom + 1, BORDER);
        g.fill(rightX, shellTop, rightX + rightW, shellBottom, PANEL_RAISED);
        g.fill(shellX, shellTop, shellX + leftW, shellTop + 2, ACCENT);
        g.fill(rightX, shellTop, rightX + rightW, shellTop + 2, 0xFF3A536B);
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(font, Component.literal("ORIGINS"), shellX, 14, ACCENT_STRONG, false);
        g.drawString(font, Component.literal("MODERN SELECTION"), shellX + 45, 14, MUTED, false);
        g.fill(shellX, 28, width - shellX, 29, BORDER_SOFT);

        if (!presenter.isDone() && presenter.currentLayerIndex() < presenter.totalLayers()) {
            String step = (presenter.currentLayerIndex() + 1) + " / " + presenter.totalLayers();
            int stepW = font.width(step);
            g.drawString(font, Component.literal(step), width - shellX - stepW, 14, MUTED, false);
        }
    }


    private void renderSelectionTrail(GuiGraphics g) {
        int y = 34;
        int x = shellX;
        if (SESSION_CHOICES.isEmpty()) {
            g.drawString(font, Component.literal("Selections will appear here as you continue"), x, y + 4, MUTED_DARK, false);
            return;
        }
        for (SelectedChoice choice : SESSION_CHOICES.values()) {
            String text = choice.layerName() + ": " + choice.choiceName();
            int chipW = Math.min(font.width(text) + 14, Math.max(80, width / 3));
            if (x + chipW > width - shellX) break;
            g.fill(x, y, x + chipW, y + 18, BORDER);
            g.fill(x + 1, y + 1, x + chipW - 1, y + 17, 0xFF172231);
            g.drawString(font, font.plainSubstrByWidth(text, chipW - 12), x + 6, y + 5, TEXT_SOFT, false);
            x += chipW + 7;
        }
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        Component layerName = presenter.currentLayer().name();
        g.drawString(font, layerName, shellX + 14, shellTop + 16, TEXT, false);
        g.drawString(font, Component.literal(visibleRows().stream().filter(row -> !row.isSectionHeader()).count() + " entries"),
                shellX + leftW - 14 - font.width(visibleRows().stream().filter(row -> !row.isSectionHeader()).count() + " entries"),
                shellTop + 16, MUTED, false);
        g.fill(shellX + 14, shellTop + 39, shellX + leftW - 14, shellTop + 40, BORDER_SOFT);

        int listY = shellTop + 102;
        int listH = shellBottom - listY - 12;
        renderList(g, mouseX, mouseY, shellX + 10, listY, leftW - 20, listH);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        List<OriginListEntry> rows = visibleRows();
        int rowH = 43;
        int visible = Math.max(1, h / rowH);
        int maxScroll = Math.max(0, rows.size() - visible);
        listScroll = Mth.clamp(listScroll, 0, maxScroll);

        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < visible + 1 && i + listScroll < rows.size(); i++) {
            OriginListEntry row = rows.get(i + listScroll);
            int ry = y + i * rowH;
            if (row.isSectionHeader()) {
                String label = row.displayName().toUpperCase();
                g.drawString(font, label, x + 6, ry + 11, MUTED, false);
                g.fill(x + 6 + font.width(label) + 7, ry + 15, x + w - 7, ry + 16, BORDER_SOFT);
                continue;
            }

            boolean selected = row.id().equals(presenter.selectedOriginId());
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + 39;
            int fill = selected ? PANEL_SELECTED : hovered ? PANEL_HOVER : 0xFF171E29;
            int border = selected ? ACCENT : hovered ? 0xFF435369 : BORDER_SOFT;
            int growPx = selected ? 1 : 0;
            g.fill(x - growPx, ry - growPx, x + w + growPx, ry + 39 + growPx, border);
            g.fill(x + 1 - growPx, ry + 1 - growPx, x + w - 1 + growPx, ry + 38 + growPx, fill);
            if (selected) g.fill(x + 1, ry + 1, x + 4, ry + 38, ACCENT);

            int textX = x + (selected ? 12 : 9);
            EntryMeta meta = entryMeta(row);
            String star = FAVORITES.contains(row.id()) ? "★ " : "";
            float grow = selected ? 1.035F : hovered ? 1.018F : 1.0F;
            g.pose().pushPose();
            g.pose().translate(textX, ry + 6, 0);
            g.pose().scale(0.92F * grow, 0.92F * grow, 1.0F);
            String title = font.plainSubstrByWidth(star + row.displayName(), Math.max(40, (int)((w - 70) / (0.92F * grow))));
            g.drawString(font, title, 0, 0, selected ? TEXT : TEXT_SOFT, false);
            g.pose().popPose();
            drawImpact(g, textX, ry + 20, meta.impact());
            String rating = estimatedRating(meta);
            int chipW = font.width(rating) + 10;
            int chipX = x + w - chipW - 7;
            g.fill(chipX, ry + 19, chipX + chipW, ry + 32, ratingColor(rating));
            g.drawString(font, rating, chipX + 5, ry + 22, TEXT, false);
            String info = impactCompact(meta.impact()) + " • " + difficultyLabel(meta) + " • " + meta.powerCount() + " powers";
            int infoWidth = Math.max(20, chipX - (textX + 31) - 5);
            g.pose().pushPose();
            g.pose().translate(textX + 31, ry + 19, 0);
            g.pose().scale(0.76F, 0.76F, 1.0F);
            g.drawString(font, font.plainSubstrByWidth(info, (int)(infoWidth / 0.76F)), 0, 0, MUTED, false);
            g.pose().popPose();
            String tags = compactTags(row.id(), 2);
            g.pose().pushPose();
            g.pose().translate(textX, ry + 31, 0);
            g.pose().scale(0.72F, 0.72F, 1.0F);
            g.drawString(font, font.plainSubstrByWidth(tags, (int)((w - 52) / 0.72F)), 0, 0, ACCENT_STRONG, false);
            g.pose().popPose();
            if (selected) {
                String mark = "✓";
                g.drawString(font, mark, x + w - font.width(mark) - 9, ry + 7, SUCCESS, false);
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackX = x + w - 3;
            int thumbH = Math.max(16, h * visible / rows.size());
            int thumbY = y + (h - thumbH) * listScroll / maxScroll;
            g.fill(trackX, y, trackX + 2, y + h, 0xFF202A38);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, ACCENT);
        }
    }

    private void renderDetails(GuiGraphics g) {
        float progress = Mth.clamp((System.currentTimeMillis() - selectionChangedAt) / 240.0F, 0.0F, 1.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        int slide = Math.round((1.0F - eased) * 18.0F);
        g.pose().pushPose();
        g.pose().translate(slide, 0, 0);
        int x = rightX + 18;
        int y = shellTop + 17;
        int w = rightW - 36;
        int h = shellBottom - shellTop - 35;

        if (detail == OriginDetailViewModel.EMPTY || detail.origin() == null) {
            renderEmptyDetail(g, x, y, w, h);
            g.pose().popPose();
            return;
        }

        var origin = detail.origin();
        int heroH = 64;
        g.fill(x, y, x + w, y + heroH, 0xFF111821);
        g.fill(x, y + heroH - 1, x + w, y + heroH, BORDER);

        g.pose().pushPose();
        g.pose().translate(x + 10, y + 11, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.renderItem(origin.icon(), 0, 0);
        g.pose().popPose();

        int titleX = x + 54;
        g.drawString(font, origin.name(), titleX, y + 13, TEXT, false);
        drawImpact(g, titleX, y + 31, origin.impact());
        g.drawString(font, impactLabel(origin.impact()), titleX + 31, y + 30, MUTED, false);
        String rating = estimatedRating(new EntryMeta(origin.impact(), detail.powerNames().size()));
        int ratingW = font.width("EST. " + rating) + 10;
        g.fill(titleX, y + 43, titleX + ratingW, y + 58, ratingColor(rating));
        g.drawString(font, "EST. " + rating, titleX + 5, y + 47, TEXT, false);
        String diff = "DIFF " + difficultyLabel(new EntryMeta(origin.impact(), detail.powerNames().size()));
        int diffW = font.width(diff) + 10;
        g.fill(titleX + ratingW + 5, y + 43, titleX + ratingW + 5 + diffW, y + 58, 0xFF493E55);
        g.drawString(font, diff, titleX + ratingW + 10, y + 47, TEXT_SOFT, false);

        ResourceLocation selectedId = presenter.selectedOriginId();
        if (selectedId != null) {
            String namespace = selectedId.getNamespace();
            int nsW = font.width(namespace) + 10;
            g.fill(x + w - nsW, y + 10, x + w, y + 26, 0xFF203044);
            g.drawString(font, namespace, x + w - nsW + 5, y + 14, ACCENT_STRONG, false);
        }

        int legendY = y + heroH + 8;
        renderRatingLegend(g, x, legendY, w);

        int contentY = legendY + 31;
        int contentH = h - heroH - 39;
        g.enableScissor(x, contentY, x + w, contentY + contentH);

        int cy = contentY - detailScroll;
        g.drawString(font, Component.literal("BUILD ANALYSIS"), x, cy, ACCENT, false);
        cy += 15;
        EntryAnalysis analysis = analyzeCurrent();
        String scoreText = analysis.score() + "/100  •  " + analysis.verdict();
        g.drawString(font, scoreText, x, cy, TEXT, false);
        cy += 13;
        g.drawString(font, "Tags: " + String.join("  •  ", analysis.tags()), x, cy, MUTED, false);
        cy += 14;
        g.drawString(font, "Strengths: " + analysis.strengths(), x, cy, SUCCESS, false);
        cy += 12;
        g.drawString(font, "Watch for: " + analysis.weaknesses(), x, cy, 0xFFE2A0A0, false);
        cy += 18;
        g.drawString(font, Component.literal("OVERVIEW"), x, cy, ACCENT, false);
        cy += 16;
        for (var line : font.split(origin.description(), w)) {
            g.drawString(font, line, x, cy, TEXT_SOFT, false);
            cy += 11;
        }
        cy += 11;

        g.drawString(font, Component.literal("ABILITIES"), x, cy, ACCENT, false);
        cy += 17;

        int visiblePowerCount = powersExpanded ? detail.powerNames().size() : Math.min(3, detail.powerNames().size());
        for (int i = 0; i < visiblePowerCount; i++) {
            String powerName = detail.powerNames().get(i);
            String desc = i < detail.powerDescs().size() ? detail.powerDescs().get(i) : "";
            int cardStart = cy;
            List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(Component.literal(desc), Math.max(80, w - 24));
            int cardH = 27 + wrapped.size() * 10 + (desc.isBlank() ? -8 : 0);
            g.fill(x, cardStart, x + w, cardStart + cardH, BORDER_SOFT);
            g.fill(x + 1, cardStart + 1, x + w - 1, cardStart + cardH - 1, 0xFF171F2A);
            g.fill(x + 1, cardStart + 1, x + 4, cardStart + cardH - 1, 0xFF3C6C89);
            g.drawString(font, powerName, x + 12, cardStart + 9, TEXT, false);
            int lineY = cardStart + 22;
            for (var line : wrapped) {
                g.drawString(font, line, x + 12, lineY, MUTED, false);
                lineY += 10;
            }
            cy += cardH + 7;
        }
        if (!powersExpanded && detail.powerNames().size() > visiblePowerCount) {
            g.drawString(font, "+ " + (detail.powerNames().size() - visiblePowerCount) + " more powers (use Powers button)", x + 8, cy + 3, MUTED, false);
            cy += 18;
        }
        g.disableScissor();

        maxDetailScroll = Math.max(0, cy + detailScroll - (contentY + contentH));
        detailScroll = Mth.clamp(detailScroll, 0, maxDetailScroll);
        renderDetailScrollbar(g, x + w + 6, contentY, contentH);
        g.pose().popPose();
    }

    private void renderRatingLegend(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 24, BORDER_SOFT);
        g.fill(x + 1, y + 1, x + w - 1, y + 23, 0xFF121A24);
        g.drawString(font, "RATING GUIDE", x + 8, y + 8, MUTED, false);

        String[] grades = {"S", "A", "B", "C", "D"};
        String[] meanings = {"Exceptional", "Strong", "Balanced", "Situational", "Challenge"};
        int cursor = x + 86;
        int available = x + w - cursor - 7;
        for (int i = 0; i < grades.length; i++) {
            String text = grades[i] + " " + meanings[i];
            int chipW = font.width(text) + 10;
            if (cursor + chipW > x + w - 7) {
                // On narrow windows, preserve all grades and omit the long meaning.
                text = grades[i];
                chipW = font.width(text) + 10;
            }
            if (cursor + chipW > x + w - 7 || available <= 0) break;
            g.fill(cursor, y + 5, cursor + chipW, y + 19, ratingColor(grades[i]));
            g.drawString(font, text, cursor + 5, y + 8, TEXT, false);
            cursor += chipW + 4;
            available = x + w - cursor - 7;
        }
    }

    private void renderEmptyDetail(GuiGraphics g, int x, int y, int w, int h) {
        int centerX = x + w / 2;
        int centerY = y + h / 2;
        g.fill(centerX - 23, centerY - 39, centerX + 23, centerY + 7, BORDER_SOFT);
        g.fill(centerX - 22, centerY - 38, centerX + 22, centerY + 6, 0xFF182231);
        g.drawCenteredString(font, Component.literal("?"), centerX, centerY - 23, ACCENT);
        g.drawCenteredString(font, Component.translatable("screen.originsmodernui.choose_prompt"), centerX, centerY + 19, TEXT_SOFT);
        g.drawCenteredString(font, Component.literal("Browse or search the list to begin"), centerX, centerY + 33, MUTED);
    }

    private void renderDetailScrollbar(GuiGraphics g, int x, int y, int h) {
        if (maxDetailScroll <= 0) return;
        int virtualH = h + maxDetailScroll;
        int thumbH = Math.max(18, h * h / virtualH);
        int thumbY = y + (h - thumbH) * detailScroll / maxDetailScroll;
        g.fill(x, y, x + 2, y + h, BORDER_SOFT);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, ACCENT);
    }

    private void renderFooter(GuiGraphics g) {
        int y = height - 40;
        String help = "Mouse wheel: scroll   •   ↑/↓: navigate   •   Enter: select";
        g.drawString(font, help, shellX, y + 8, MUTED_DARK, false);
        String stats = presenter.totalLayers() + " layers  •  " + visibleRows().stream().filter(r -> !r.isSectionHeader()).count() + " shown  •  " + FAVORITES.size() + " favorites";
        g.drawString(font, stats, width - shellX - font.width(stats), y + 8, MUTED_DARK, false);
    }

    private Component impactLabel(Impact impact) {
        return switch (impact.getDotCount()) {
            case 0 -> Component.literal("NO IMPACT");
            case 1 -> Component.literal("LOW IMPACT");
            case 2 -> Component.literal("MEDIUM IMPACT");
            default -> Component.literal("HIGH IMPACT");
        };
    }

    private void drawImpact(GuiGraphics g, int x, int y, Impact impact) {
        int dots = impact.getDotCount();
        for (int i = 0; i < 3; i++) {
            int color = i < dots ? ACCENT : 0xFF354153;
            g.fill(x + i * 9, y, x + i * 9 + 6, y + 6, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transitionStage != TransitionStage.NONE) return true;
        int x = shellX + 10;
        int y = shellTop + 102;
        int w = leftW - 20;
        int h = shellBottom - y - 12;
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            int index = (int) ((mouseY - y) / 43) + listScroll;
            List<OriginListEntry> rows = visibleRows();
            if (index >= 0 && index < rows.size() && !rows.get(index).isSectionHeader()) {
                select(rows.get(index).id());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (transitionStage != TransitionStage.NONE) return true;
        if (mouseX < rightX) {
            targetListScroll = Math.max(0, targetListScroll - (float) Math.signum(scrollY) * 2.0F);
            return true;
        }
        targetDetailScroll = Mth.clamp(targetDetailScroll - (float) Math.signum(scrollY) * 28.0F, 0.0F, (float) maxDetailScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (transitionStage != TransitionStage.NONE) return true;
        // GLFW: up=265, down=264, enter=257, keypad enter=335.
        if (keyCode == 265 || keyCode == 264) {
            moveSelection(keyCode == 264 ? 1 : -1);
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && search != null && !search.isFocused()) {
            confirmSelection();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelection(int direction) {
        List<OriginListEntry> rows = visibleRows();
        if (rows.isEmpty()) return;
        ResourceLocation current = presenter.selectedOriginId();
        int index = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).isSectionHeader() && rows.get(i).id().equals(current)) {
                index = i;
                break;
            }
        }
        for (int tries = 0; tries < rows.size(); tries++) {
            index = Mth.clamp(index + direction, 0, rows.size() - 1);
            OriginListEntry row = rows.get(index);
            if (!row.isSectionHeader()) {
                select(row.id());
                targetListScroll = Math.max(0, index - 3);
                return;
            }
            if ((index == 0 && direction < 0) || (index == rows.size() - 1 && direction > 0)) return;
        }
    }


    /**
     * A lightweight estimate, not a balance verdict. NeoOrigins exposes impact and
     * ability count, but not win-rate or curated tier data, so this score is only a
     * quick visual guide.
     */
    private int ratingScore(EntryMeta meta) {
        return meta.impact().getDotCount() * 2 + Math.min(4, meta.powerCount() / 2);
    }

    private String estimatedRating(EntryMeta meta) {
        int score = ratingScore(meta);
        if (score >= 8) return "S";
        if (score >= 6) return "A";
        if (score >= 4) return "B";
        if (score >= 2) return "C";
        return "D";
    }

    private int ratingColor(String rating) {
        return switch (rating) {
            case "S" -> 0xFF694D82;
            case "A" -> 0xFF315F54;
            case "B" -> 0xFF36566F;
            case "C" -> 0xFF675B36;
            default -> 0xFF5A4148;
        };
    }

    private String impactCompact(Impact impact) {
        return switch (impact.getDotCount()) {
            case 0 -> "NONE";
            case 1 -> "LOW";
            case 2 -> "MEDIUM";
            default -> "HIGH";
        };
    }

    private int difficultyScore(EntryMeta meta) {
        int impact = meta.impact().getDotCount();
        int complexity = Math.min(3, meta.powerCount() / 5);
        return Mth.clamp(1 + impact + complexity, 1, 5);
    }

    private String difficultyLabel(EntryMeta meta) {
        return switch (difficultyScore(meta)) {
            case 1 -> "★☆☆☆☆";
            case 2 -> "★★☆☆☆";
            case 3 -> "★★★☆☆";
            case 4 -> "★★★★☆";
            default -> "★★★★★";
        };
    }

    private EntryAnalysis analyzeCurrent() {
        if (detail == OriginDetailViewModel.EMPTY || detail.origin() == null) {
            return new EntryAnalysis(0, "No selection", List.of("Unknown"), "None", "None");
        }
        StringBuilder corpus = new StringBuilder(detail.origin().name().getString()).append(' ')
                .append(detail.origin().description().getString()).append(' ');
        detail.powerNames().forEach(v -> corpus.append(v).append(' '));
        detail.powerDescs().forEach(v -> corpus.append(v).append(' '));
        String text = corpus.toString().toLowerCase(Locale.ROOT);
        ArrayList<String> tags = new ArrayList<>();
        if (containsAny(text, "speed", "teleport", "flight", "jump", "dash", "movement")) tags.add("Mobility");
        if (containsAny(text, "damage", "attack", "weapon", "strength", "critical")) tags.add("Combat");
        if (containsAny(text, "health", "armor", "resistance", "shield", "regeneration")) tags.add("Tank");
        if (containsAny(text, "magic", "spell", "mana", "arcane")) tags.add("Magic");
        if (containsAny(text, "water", "swim", "ocean", "aquatic")) tags.add("Water");
        if (containsAny(text, "mining", "ore", "craft", "loot", "trade")) tags.add("Utility");
        if (containsAny(text, "vision", "explore", "night", "dimension")) tags.add("Exploration");
        if (tags.isEmpty()) tags.add("Generalist");
        EntryMeta meta = new EntryMeta(detail.origin().impact(), detail.powerNames().size());
        int base = 40 + ratingScore(meta) * 6;
        int synergy = Math.min(15, Math.max(0, SESSION_CHOICES.size() - 1) * 5);
        int score = Mth.clamp(base + synergy, 0, 100);
        String verdict = score >= 90 ? "Exceptional synergy" : score >= 78 ? "Strong build" : score >= 64 ? "Balanced build" : "Specialist build";
        String strengths = String.join(", ", tags.subList(0, Math.min(3, tags.size())));
        String weaknesses = containsAny(text, "weak", "damage in", "burn", "cannot", "slower", "vulnerable") ? "Meaningful drawbacks detected" : "Read power drawbacks carefully";
        return new EntryAnalysis(score, verdict, List.copyOf(tags), strengths, weaknesses);
    }

    private List<OriginListEntry> rowsForCurrentLayer() {
        ArrayList<OriginListEntry> rows = new ArrayList<>();
        for (ResourceLocation id : presenter.allOriginIds()) {
            OriginDetailViewModel vm = OriginDetailViewModel.compute(id, isOrb);
            String name = vm != OriginDetailViewModel.EMPTY && vm.origin() != null
                    ? vm.origin().name().getString() : id.getPath();
            rows.add(OriginListEntry.origin(id, name, id.getNamespace()));
        }
        return rows;
    }

    private String searchableText(OriginListEntry row) {
        return searchTextCache.computeIfAbsent(row.id(), id -> {
            OriginDetailViewModel vm = OriginDetailViewModel.compute(id, isOrb);
            StringBuilder text = new StringBuilder(row.displayName()).append(' ')
                    .append(row.namespace()).append(' ').append(String.join(" ", tagsFor(id)));
            if (vm != OriginDetailViewModel.EMPTY && vm.origin() != null) {
                text.append(' ').append(vm.origin().description().getString());
                vm.powerNames().forEach(v -> text.append(' ').append(v));
                vm.powerDescs().forEach(v -> text.append(' ').append(v));
            }
            return text.toString().toLowerCase(Locale.ROOT);
        });
    }

    private List<String> tagsFor(ResourceLocation id) {
        return tagCache.computeIfAbsent(id, key -> computeTags(key));
    }

    private List<String> computeTags(ResourceLocation id) {
        OriginDetailViewModel vm = OriginDetailViewModel.compute(id, isOrb);
        if (vm == OriginDetailViewModel.EMPTY || vm.origin() == null) return List.of("Generalist");
        StringBuilder corpus = new StringBuilder(vm.origin().name().getString()).append(' ')
                .append(vm.origin().description().getString()).append(' ');
        vm.powerNames().forEach(v -> corpus.append(v).append(' '));
        vm.powerDescs().forEach(v -> corpus.append(v).append(' '));
        String text = corpus.toString().toLowerCase(Locale.ROOT);
        ArrayList<String> tags = new ArrayList<>();
        if (containsAny(text, "melee", "sword", "axe", "close combat", "attack damage", "strength")) tags.add("⚔ Melee");
        if (containsAny(text, "ranged", "bow", "arrow", "projectile", "crossbow")) tags.add("🏹 Ranged");
        if (containsAny(text, "magic", "spell", "mana", "arcane", "wizard")) tags.add("✦ Magic");
        if (containsAny(text, "water", "swim", "ocean", "aquatic", "underwater")) tags.add("≈ Water");
        if (containsAny(text, "explore", "vision", "night vision", "dimension", "compass")) tags.add("⌖ Exploration");
        if (containsAny(text, "trade", "loot", "emerald", "economy", "discount")) tags.add("$ Economy");
        if (containsAny(text, "health", "armor", "resistance", "shield", "regeneration")) tags.add("♥ Tank");
        if (containsAny(text, "speed", "teleport", "flight", "jump", "dash", "movement")) tags.add("» Mobility");
        if (tags.isEmpty()) tags.add("• Generalist");
        return List.copyOf(tags);
    }

    private String compactTags(ResourceLocation id, int limit) {
        List<String> tags = tagsFor(id);
        return String.join("  ", tags.subList(0, Math.min(limit, tags.size())));
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private enum FilterMode {
        ALL("All"), FAVORITES("★ Fav"), TOP_RATED("S/A"), BEGINNER("Easy"), CHALLENGE("Hard");
        private final String shortLabel;
        FilterMode(String shortLabel) { this.shortLabel = shortLabel; }
        private FilterMode next() { FilterMode[] v = values(); return v[(ordinal() + 1) % v.length]; }
    }

    private enum SortMode {
        DEFAULT("Default"),
        RATING_DESC("Rating S-D"),
        RATING_ASC("Rating D-S"),
        IMPACT_DESC("Impact High"),
        IMPACT_ASC("Impact Low"),
        NAME_ASC("A-Z"),
        NAME_DESC("Z-A"),
        POWERS_DESC("Most Powers"),
        POWERS_ASC("Least Powers"),
        DIFFICULTY_DESC("Difficulty High"),
        DIFFICULTY_ASC("Difficulty Low"),
        NAMESPACE("Pack");

        private final String shortLabel;

        SortMode(String shortLabel) {
            this.shortLabel = shortLabel;
        }

        private SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private record EntryMeta(Impact impact, int powerCount) {}

    private record EntryAnalysis(int score, String verdict, List<String> tags, String strengths, String weaknesses) {}

    private record SelectedChoice(String layerName, String choiceName) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum TransitionStage {
        NONE, OUT, IN
    }

}
