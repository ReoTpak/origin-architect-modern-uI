package ro.vlad.originsmodernui.client;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.screen.OriginSelectionPresenter;
import com.cyberday1.neoorigins.screen.OriginSelectionScreen;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import com.cyberday1.neoorigins.screen.model.OriginListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import ro.vlad.originsmodernui.ArchitectStats;
import ro.vlad.originsmodernui.ArchitectNetwork;
import ro.vlad.originsmodernui.ArchitectProgression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private final boolean profileOnly;

    private EditBox search;
    private Button confirm;
    private Button random;
    private Button sort;
    private Button filter;
    private Button favorite;
    private Button powersToggle;
    private Button back;
    private Button summaryBack;
    private Button summaryConfirm;
    private final List<Button> statButtons = new ArrayList<>();
    private Button resetStats;
    private Button confirmResetStats;
    private Button cancelResetStats;
    private boolean resetConfirmationVisible;
    private Button themeButton;
    private String hoveredTooltipKey = "";
    private long hoveredTooltipSince;
    private static final long TOOLTIP_DELAY_MS = 500L;
    private boolean summaryMode;
    private boolean pendingSummary;
    private long summaryOpenedAt;
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
    private static UiTheme activeTheme = UiTheme.AQUA;

    private int shellX;
    private int shellTop;
    private int shellBottom;
    private int leftW;
    private int gap;
    private int rightX;
    private int rightW;

    private ModernOriginSelectionScreen(boolean isOrb, boolean forceReselect, List<ResourceLocation> scopedLayers) {
        this(isOrb, forceReselect, scopedLayers, false);
    }

    private ModernOriginSelectionScreen(boolean isOrb, boolean forceReselect, List<ResourceLocation> scopedLayers, boolean profileOnly) {
        super(Component.translatable(profileOnly ? "screen.originsmodernui.profile" : "screen.originsmodernui.title"));
        this.isOrb = isOrb;
        this.forceReselect = forceReselect;
        this.scopedLayers = List.copyOf(scopedLayers);
        this.profileOnly = profileOnly;
    }

    public static ModernOriginSelectionScreen openProfile() {
        return new ModernOriginSelectionScreen(false, false, List.of(), true);
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
        if (profileOnly) {
            summaryMode = true;
            loadCurrentPlayerChoices();
            calculateLayout();
            int statsPanelW = Math.min(width - 48, 1000);
            int statsPanelX = (width - statsPanelW) / 2;
            int closeW = Math.max(150, Math.min(220, statsPanelW / 4));
            summaryBack = Button.builder(Component.literal("Close"), b -> onClose())
                    .bounds(statsPanelX + statsPanelW - closeW - 14, height - 38, closeW, 22).build();
            summaryConfirm = Button.builder(Component.literal("Done"), b -> onClose())
                    .bounds(0, 0, 1, 1).build();
            summaryConfirm.visible = false;
            addRenderableWidget(summaryBack);
            addRenderableWidget(summaryConfirm);
            themeButton = Button.builder(themeLabel(), b -> cycleTheme())
                    .bounds(width - shellX - 178, 7, 112, 20).build();
            addRenderableWidget(themeButton);
            createStatAllocationButtons();
            return;
        }
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

        // Responsive bottom action dock. On normal windows every action lives on
        // one clean row; narrow windows use two balanced rows instead of squeezing
        // buttons until labels or hitboxes overlap.
        int dockGap = 7;
        int dockLeft = shellX + 10;
        int dockRight = width - shellX - 10;
        int dockWidth = dockRight - dockLeft;
        boolean compactDock = dockWidth < 650;

        int utilityY = compactDock ? height - 78 : height - 47;
        int actionY = height - 47;

        int backW;
        int favoriteW;
        int powersW;
        int utilityX;
        int actionW;
        int actionStartX;

        if (compactDock) {
            int utilityAvailable = dockWidth - dockGap * 2;
            backW = utilityAvailable / 3;
            favoriteW = utilityAvailable / 3;
            powersW = utilityAvailable - backW - favoriteW;
            utilityX = dockLeft;

            actionW = Math.max(100, (dockWidth - dockGap) / 2);
            actionStartX = dockLeft;
        } else {
            backW = 82;
            favoriteW = 108;
            powersW = 142;
            utilityX = dockLeft;

            actionW = 128;
            actionStartX = dockRight - actionW * 2 - dockGap;
        }

        random = Button.builder(Component.translatable("screen.originsmodernui.random"), b -> randomSelection())
                .bounds(actionStartX, actionY, actionW, 24)
                .build();
        confirm = Button.builder(Component.translatable("screen.originsmodernui.select"), b -> confirmSelection())
                .bounds(actionStartX + actionW + dockGap, actionY, actionW, 24)
                .build();
        addRenderableWidget(random);
        addRenderableWidget(confirm);

        back = Button.builder(Component.literal("← Back"), b -> backSelection())
                .bounds(utilityX, utilityY, backW, 24).build();
        favorite = Button.builder(Component.literal("☆ Favorite"), b -> toggleFavorite())
                .bounds(utilityX + backW + dockGap, utilityY, favoriteW, 24).build();
        powersToggle = Button.builder(Component.literal("Powers: All"), b -> {
                    powersExpanded = !powersExpanded;
                    b.setMessage(Component.literal(powersExpanded ? "Powers: All" : "Powers: Collapsed"));
                    detailScroll = 0;
                }).bounds(utilityX + backW + dockGap + favoriteW + dockGap, utilityY, powersW, 24).build();
        addRenderableWidget(back);
        addRenderableWidget(favorite);
        addRenderableWidget(powersToggle);

        int summaryButtonW = Math.max(130, Math.min(210, (width - 64) / 2));
        int summaryButtonY = height - 47;
        summaryBack = Button.builder(Component.literal("← Edit build"), b -> leaveSummary())
                .bounds(width / 2 - summaryButtonW - 6, summaryButtonY, summaryButtonW, 26).build();
        summaryConfirm = Button.builder(Component.literal("Begin adventure"), b -> finalizeBuild())
                .bounds(width / 2 + 6, summaryButtonY, summaryButtonW, 26).build();
        summaryBack.visible = false;
        summaryConfirm.visible = false;
        addRenderableWidget(summaryBack);
        addRenderableWidget(summaryConfirm);

        themeButton = Button.builder(themeLabel(), b -> cycleTheme())
                .bounds(width - shellX - 178, 7, 112, 20).build();
        addRenderableWidget(themeButton);

        updateDetail();
    }

    private void calculateLayout() {
        int margin = width < 620 ? 10 : 18;
        shellTop = 58;
        // Reserve enough room for the action dock. Narrow screens need a second
        // button row, so the scrollable panels stop higher and never hide controls.
        shellBottom = height - (width - margin * 2 < 650 ? 94 : 66);
        shellX = margin;
        int available = width - margin * 2;
        gap = width < 620 ? 8 : 12;
        leftW = Mth.clamp((int) (available * 0.39F), 196, 270);
        rightX = shellX + leftW + gap;
        rightW = width - margin - rightX;
    }

    private void cycleTheme() {
        activeTheme = activeTheme.next();
        if (themeButton != null) themeButton.setMessage(themeLabel());
    }

    private Component themeLabel() {
        return Component.literal("Theme: " + activeTheme.label);
    }

    private int themeBgTop() { return activeTheme.bgTop; }
    private int themeBgBottom() { return activeTheme.bgBottom; }
    private int themePanel() { return activeTheme.panel; }
    private int themeRaised() { return activeTheme.raised; }
    private int themeBorder() { return activeTheme.border; }
    private int themeAccent() { return activeTheme.accent; }
    private int themeAccentStrong() { return activeTheme.accentStrong; }

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
        if (transitionStage != TransitionStage.NONE || summaryMode || presenter.selectedOriginId() == null || presenter.isDone()) return;
        rememberCurrentChoice();
        pendingSummary = presenter.currentLayerIndex() == presenter.totalLayers() - 1;
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
            if (pendingSummary) {
                pendingSummary = false;
                summaryMode = true;
                summaryOpenedAt = System.currentTimeMillis();
                configureSummaryControls();
                return;
            }
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
        boolean selectionVisible = !summaryMode;
        if (search != null) { search.active = enabled && selectionVisible; search.visible = selectionVisible; }
        if (sort != null) { sort.active = enabled && selectionVisible; sort.visible = selectionVisible; }
        if (filter != null) { filter.active = enabled && selectionVisible; filter.visible = selectionVisible; }
        if (random != null) { random.active = enabled && selectionVisible; random.visible = selectionVisible; }
        if (favorite != null) { favorite.active = enabled && selectionVisible; favorite.visible = selectionVisible; }
        if (powersToggle != null) { powersToggle.active = enabled && selectionVisible; powersToggle.visible = selectionVisible; }
        if (confirm != null) { confirm.active = enabled && selectionVisible && presenter.selectedOriginId() != null; confirm.visible = selectionVisible; }
        if (back != null) { back.active = enabled && selectionVisible && presenter.currentLayerIndex() > 0; back.visible = selectionVisible; }
        if (summaryBack != null) { summaryBack.visible = summaryMode; summaryBack.active = enabled && summaryMode; }
        if (summaryConfirm != null) { summaryConfirm.visible = summaryMode && !profileOnly; summaryConfirm.active = enabled && summaryMode && !profileOnly; }
    }

    private void configureSummaryControls() {
        setControlsEnabled(transitionStage == TransitionStage.NONE);
    }

    private void leaveSummary() {
        if (!summaryMode || transitionStage != TransitionStage.NONE) return;
        if (profileOnly) { onClose(); return; }
        summaryMode = false;
        ResourceLocation last = null;
        for (ResourceLocation id : SESSION_CHOICES.keySet()) last = id;
        if (last != null) SESSION_CHOICES.remove(last);
        setControlsEnabled(true);
        selectionChangedAt = System.currentTimeMillis();
    }

    private void finalizeBuild() {
        if (!summaryMode || transitionStage != TransitionStage.NONE) return;
        if (profileOnly) { onClose(); return; }
        presenter.confirm();
        SESSION_CHOICES.clear();
        Minecraft.getInstance().setScreen(null);
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
        EntryAnalysis analysis = analyzeCurrent();
        String description = detail != OriginDetailViewModel.EMPTY && detail.origin() != null
                ? detail.origin().description().getString()
                : "";
        List<String> powerNames = detail != OriginDetailViewModel.EMPTY ? List.copyOf(detail.powerNames()) : List.of();
        List<String> powerDescs = detail != OriginDetailViewModel.EMPTY ? List.copyOf(detail.powerDescs()) : List.of();
        SESSION_CHOICES.put(layerId, new SelectedChoice(layerName, choiceName, selected, analysis.score(), analysis.tags(),
                analysis.strengths(), analysis.weaknesses(), description, powerNames, powerDescs));
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
        smoothListScroll += (targetListScroll - smoothListScroll) * 0.16F;
        smoothDetailScroll += (targetDetailScroll - smoothDetailScroll) * 0.15F;
        listScroll = Math.max(0, Math.round(smoothListScroll));
        detailScroll = Math.max(0, Math.round(smoothDetailScroll));
        if (profileOnly) {
            // Stats Screen is an in-game overlay: keep the world visible and running
            // behind a restrained translucent scrim instead of replacing it with a
            // full-screen menu background.
            graphics.fill(0, 0, width, height, 0x66000000);
        } else {
            graphics.fillGradient(0, 0, width, height, themeBgTop(), themeBgBottom());
            renderAtmosphere(graphics);
        }

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
        if (!summaryMode) {
            renderShells(graphics);
            renderActionDock(graphics);
        }

        // Vanilla widgets still own input, focus, narration and accessibility, but their
        // default labels are temporarily hidden so our themed renderer is the only pass
        // that paints button text. This prevents the doubled/ghosted labels seen in 4.6.0.
        Map<Button, Component> buttonLabels = hideButtonLabelsForVanillaPass();
        super.render(graphics, mouseX, mouseY, partialTick);
        restoreButtonLabels(buttonLabels);
        renderThemedButtons(graphics);

        if (summaryMode) {
            renderBuildSummary(graphics, mouseX, mouseY);
        } else {
            renderHeader(graphics);
            renderSelectionTrail(graphics);
            if (!presenter.isDone() && presenter.currentLayerIndex() < presenter.totalLayers()) {
                renderSidebar(graphics, mouseX, mouseY);
                renderDetails(graphics);
                renderFooter(graphics);
            }
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
        g.fill(drift - 120, 0, drift + 120, 1, (0x33000000 | (themeAccent() & 0x00FFFFFF)));
        g.fill(width - 170, 0, width, height, 0x12000000);
        for (int i = 0; i < 7; i++) {
            int y = 24 + i * 37;
            int x = width - 26 - (i % 3) * 9;
            g.fill(x, y, x + 1, y + 1, i % 2 == 0 ? (0x55000000 | (themeAccent() & 0x00FFFFFF)) : 0x33596779);
        }
    }

    private void renderShells(GuiGraphics g) {
        // Outer borders and subtle inset lines provide depth without transparency.
        g.fill(shellX - 1, shellTop - 1, shellX + leftW + 1, shellBottom + 1, themeBorder());
        g.fill(shellX, shellTop, shellX + leftW, shellBottom, themePanel());
        g.fill(rightX - 1, shellTop - 1, rightX + rightW + 1, shellBottom + 1, themeBorder());
        g.fill(rightX, shellTop, rightX + rightW, shellBottom, themeRaised());
        g.fill(shellX, shellTop, shellX + leftW, shellTop + 2, themeAccent());
        g.fill(rightX, shellTop, rightX + rightW, shellTop + 2, mixColor(themeAccent(), 0xFFFFFFFF, 0.20F));
    }

    private void renderHeader(GuiGraphics g) {
        g.drawString(font, Component.literal("ORIGINS"), shellX, 14, themeAccentStrong(), false);
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
        int rowH = 48;
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
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + 44;
            EntryMeta meta = entryMeta(row);
            int accent = impactColor(meta.impact());
            int fill = selected ? mixColor(0xFF15202C, accent, 0.22F) : hovered ? mixColor(0xFF171E29, accent, 0.10F) : 0xFF151C27;
            int border = selected ? accent : hovered ? mixColor(BORDER_SOFT, accent, 0.55F) : BORDER_SOFT;
            int growPx = selected ? 1 : 0;
            drawRoundedPanel(g, x - growPx, ry - growPx, x + w + growPx, ry + 44 + growPx, 5, border, fill);
            g.fill(x + 1, ry + 5, x + 4, ry + 39, accent);
            if (selected) {
                g.fill(x + 5, ry + 1, x + w - 5, ry + 2, accent);
                g.fill(x + 5, ry + 42, x + w - 5, ry + 43, accent);
            }

            int iconX = x + 8;
            int iconY = ry + 7;
            renderEntryIcon(g, row.id(), iconX, iconY, 24);
            int textX = x + 39;
            String star = FAVORITES.contains(row.id()) ? "★ " : "";
            float grow = selected ? 1.035F : hovered ? 1.018F : 1.0F;
            g.pose().pushPose();
            g.pose().translate(textX, ry + 6, 0);
            g.pose().scale(0.92F * grow, 0.92F * grow, 1.0F);
            String title = font.plainSubstrByWidth(star + row.displayName(), Math.max(40, (int)((w - 102) / (0.92F * grow))));
            g.drawString(font, title, 0, 0, selected ? TEXT : TEXT_SOFT, false);
            g.pose().popPose();
            String rating = estimatedRating(meta);
            int chipW = font.width(rating);
            int chipX = x + w / 2 - chipW / 2;
            g.drawString(font, Component.literal(rating).withStyle(ChatFormatting.BOLD), chipX, ry + 8, ratingColor(rating), false);

            String impactText = impactCompact(meta.impact());
            int impactW = font.width(impactText);
            g.fill(textX, ry + 27, textX + 3, ry + 30, accent);
            g.drawString(font, Component.literal(impactText).withStyle(ChatFormatting.BOLD), textX + 7, ry + 24, accent, false);

            int starX = textX + impactW + 13;
            drawDifficultyStars(g, starX, ry + 23, difficultyScore(meta));
            String powers = meta.powerCount() + " powers";
            int powersX = x + w - font.width(powers) - 24;
            int safePowersX = Math.max(starX + 52, powersX);
            int availablePowersW = Math.max(28, x + w - 24 - safePowersX);
            g.drawString(font, font.plainSubstrByWidth(powers, availablePowersW), safePowersX, ry + 24,
                    MUTED, false);

            List<String> tagList = tagsFor(row.id());
            int tx = textX;
            for (int ti = 0; ti < Math.min(2, tagList.size()); ti++) {
                String tag = cleanTag(tagList.get(ti));
                int tc = tagColor(tag);
                int tw = Math.min(font.width(tag) + 9, 86);
                if (tx + tw > x + w - 10) break;
                g.fill(tx, ry + 39, tx + 3, ry + 42, tc);
                g.pose().pushPose();
                g.pose().translate(tx + 6, ry + 37, 0);
                g.pose().scale(0.76F, 0.76F, 1.0F);
                g.drawString(font, font.plainSubstrByWidth(tag, (int)((tw - 7) / 0.76F)), 0, 0, tc, false);
                g.pose().popPose();
                tx += tw + 5;
            }
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
        g.drawString(font, "STRENGTHS", x, cy, SUCCESS, false);
        cy += 13;
        for (String item : splitAnalysis(analysis.strengths())) {
            for (var line : font.split(Component.literal("+ " + item), Math.max(90, w - 8))) {
                g.drawString(font, line, x + 5, cy, TEXT_SOFT, false);
                cy += 11;
            }
        }
        cy += 5;
        g.drawString(font, "WEAKNESSES", x, cy, 0xFFE2A0A0, false);
        cy += 13;
        for (String item : splitAnalysis(analysis.weaknesses())) {
            for (var line : font.split(Component.literal("- " + item), Math.max(90, w - 8))) {
                g.drawString(font, line, x + 5, cy, TEXT_SOFT, false);
                cy += 11;
            }
        }
        cy += 8;
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

    private void createStatAllocationButtons() {
        statButtons.clear();
        int panelW = Math.min(width - 28, 1040);
        int panelX = (width - panelW) / 2;
        int panelTop = 48;
        int dockTop = height - 44;
        int leftW = Math.max(220, Math.min(258, panelW / 3));
        int gap = 10;
        int rightX = panelX + leftW + gap;
        int rightW = panelW - leftW - gap;
        int buildH = height < 520 ? 58 : 68;
        int attributesTop = panelTop + 32 + buildH + 20;
        int attributesBottom = dockTop - 8;
        int rowGap = 5;
        int rowH = Math.max(48, (attributesBottom - attributesTop - rowGap * 3) / 4);
        int minusW = 30;
        int plusW = 48;
        int controlGap = 5;
        int controlsRight = rightX + rightW - 12;

        ArchitectProgression.Stat[] stats = ArchitectProgression.Stat.values();
        for (int i = 0; i < stats.length; i++) {
            int sy = attributesTop + i * (rowH + rowGap);
            ArchitectProgression.Stat stat = stats[i];
            int plusX = controlsRight - plusW;
            int minusX = plusX - controlGap - minusW;
            int buttonY = sy + Math.max(4, (rowH - 22) / 2);
            Button minus = Button.builder(Component.literal("−"), b -> {
                        int amount = Screen.hasShiftDown() ? 10 : Screen.hasControlDown() ? 5 : 1;
                        ArchitectNetwork.allocate(stat, -amount);
                    }).bounds(minusX, buttonY, minusW, 22).build();
            Button plus = Button.builder(Component.literal("+"), b -> {
                        int amount = Screen.hasShiftDown() ? 10 : Screen.hasControlDown() ? 5 : 1;
                        ArchitectNetwork.allocate(stat, amount);
                    }).bounds(plusX, buttonY, plusW, 22).build();
            statButtons.add(minus);
            statButtons.add(plus);
            addRenderableWidget(minus);
            addRenderableWidget(plus);
        }

        int resetW = 136;
        int resetX = panelX + 12;
        int resetY = dockTop + 10;
        resetStats = Button.builder(Component.literal("Reset stats"), b -> setResetConfirmation(true))
                .bounds(resetX, resetY, resetW, 22).build();
        confirmResetStats = Button.builder(Component.literal("Confirm reset"), b -> {
                    ArchitectNetwork.reset();
                    setResetConfirmation(false);
                }).bounds(resetX, resetY, 112, 22).build();
        cancelResetStats = Button.builder(Component.literal("Cancel"), b -> setResetConfirmation(false))
                .bounds(resetX + 118, resetY, 70, 22).build();
        confirmResetStats.visible = false;
        cancelResetStats.visible = false;
        addRenderableWidget(resetStats);
        addRenderableWidget(confirmResetStats);
        addRenderableWidget(cancelResetStats);
    }

    private void setResetConfirmation(boolean visible) {
        resetConfirmationVisible = visible;
        if (resetStats != null) resetStats.visible = !visible;
        if (confirmResetStats != null) confirmResetStats.visible = visible;
        if (cancelResetStats != null) cancelResetStats.visible = visible;
    }

    private void updateStatAllocationButtons() {
        if (!profileOnly || statButtons.isEmpty()) return;
        ArchitectProgression.Data data = ClientArchitectState.data;
        int cost = ArchitectProgression.upgradeCost(data);
        int vanillaXp = minecraft != null && minecraft.player != null ? minecraft.player.totalExperience : 0;
        ArchitectProgression.Stat[] stats = ArchitectProgression.Stat.values();
        for (int i = 0; i < stats.length; i++) {
            Button minus = statButtons.get(i * 2);
            Button plus = statButtons.get(i * 2 + 1);
            int points = data.points(stats[i]);
            minus.active = points > 0;
            plus.active = points < ArchitectProgression.MAX_ALLOCATION_PER_STAT && vanillaXp >= cost;
            plus.setMessage(Component.literal("+" + cost));
        }
    }

    private String buildStatMilestoneTooltip(int index, ArchitectProgression.Data data, int base) {
        int points = switch (index) {
            case 0 -> data.offense();
            case 1 -> data.defense();
            case 2 -> data.utility();
            default -> data.survival();
        };
        int cost = ArchitectProgression.upgradeCost(data);
        return "Base " + base + " + Invested " + points
                + "\nNext: " + nextRewardText(index, points)
                + "\nCost: " + cost + " XP";
    }

    private String milestoneText(int nextThree, String threeReward, int nextFive, String fiveReward) {
        if (nextThree < 0 && nextFive < 0) return "Maximum allocation reached";
        if (nextThree == nextFive) return "At " + nextThree + " points: " + threeReward + " and " + fiveReward;
        if (nextThree < 0) return "At " + nextFive + " points: " + fiveReward;
        if (nextFive < 0) return "At " + nextThree + " points: " + threeReward;
        return nextThree < nextFive ? "At " + nextThree + " points: " + threeReward
                : "At " + nextFive + " points: " + fiveReward;
    }

    private void renderStatsScreenV5(GuiGraphics g, int mouseX, int mouseY) {
        int panelW = Math.min(width - 28, 1040);
        int panelX = (width - panelW) / 2;
        int panelTop = 48;
        int dockTop = height - 44;
        int panelBottom = dockTop - 5;
        int leftW = Math.max(220, Math.min(258, panelW / 3));
        int gap = 10;
        int rightX = panelX + leftW + gap;
        int rightW = panelW - leftW - gap;
        boolean compact = height < 520;

        drawRoundedPanel(g, panelX, panelTop, panelX + panelW, panelBottom, 7,
                mixColor(themeBorder(), themeAccent(), 0.50F), 0xEA090E16);
        g.fill(panelX + 8, panelTop + 1, panelX + panelW - 8, panelTop + 3, themeAccent());
        g.drawCenteredString(font, Component.literal("STATS SCREEN").withStyle(ChatFormatting.BOLD), width / 2, 10, themeAccentStrong());

        Minecraft mc = Minecraft.getInstance();
        ArchitectProgression.Data progression = ClientArchitectState.data;
        int[] baseStats = buildStats();
        int[] allocated = {progression.offense(), progression.defense(), progression.utility(), progression.survival()};
        int[] finalStats = new int[4];
        for (int i = 0; i < 4; i++) finalStats[i] = Mth.clamp(baseStats[i] + allocated[i], 0, 100);
        int score = buildScore();
        int scoreColor = score >= 82 ? 0xFF9E6FE6 : score >= 68 ? 0xFF63D77A : score >= 52 ? 0xFFE2B44F : 0xFFD85C5C;
        int vanillaXp = mc.player == null ? 0 : mc.player.totalExperience;
        int nextCost = ArchitectProgression.upgradeCost(progression);
        int upgrades = progression.offense() + progression.defense() + progression.utility() + progression.survival();

        // Left column: player, score and progression. All heights are derived from available space.
        int leftX = panelX + 10;
        int leftRight = panelX + leftW - 2;
        int leftInnerW = leftRight - leftX;
        g.fill(panelX + leftW, panelTop + 8, panelX + leftW + 1, panelBottom - 8, 0xFF263343);
        String playerName = mc.player == null ? "Player" : mc.player.getGameProfile().getName();
        g.drawCenteredString(font, Component.literal("✦  " + playerName + "  ✦").withStyle(ChatFormatting.BOLD),
                leftX + leftInnerW / 2, panelTop + 10, TEXT);

        int previewTop = panelTop + 27;
        int previewH = compact ? 142 : 166;
        int previewBottom = previewTop + previewH;
        drawRoundedPanel(g, leftX, previewTop, leftRight, previewBottom, 4,
                mixColor(themeBorder(), themeAccent(), 0.26F), 0x66101924);
        if (mc.player != null) {
            renderPlayerPreview(g, leftX + 16, previewTop + 6, leftRight - 16, previewBottom - 16,
                    compact ? 68 : 78, mouseX, mouseY, mc.player);
        }
        g.fill(leftX + 24, previewBottom - 17, leftRight - 24, previewBottom - 15, mixColor(themeAccent(), 0xFFFFFFFF, 0.28F));
        g.drawCenteredString(font, Component.literal("Drag to rotate"), leftX + leftInnerW / 2, previewBottom - 12, MUTED);

        int scoreTop = previewBottom + 7;
        int scoreH = compact ? 58 : 68;
        drawRoundedPanel(g, leftX, scoreTop, leftRight, scoreTop + scoreH, 4,
                mixColor(themeBorder(), scoreColor, 0.30F), 0x66101924);
        g.drawString(font, Component.literal("BUILD SCORE").withStyle(ChatFormatting.BOLD), leftX + 9, scoreTop + 7, themeAccentStrong(), false);
        String scoreText = score + " / 100";
        g.drawString(font, Component.literal(scoreText).withStyle(ChatFormatting.BOLD), leftX + 9, scoreTop + 24, scoreColor, false);
        String grade = score >= 92 ? "S" : score >= 82 ? "A" : score >= 68 ? "B" : score >= 52 ? "C" : "D";
        String gradeText = "GRADE " + grade;
        g.drawString(font, Component.literal(gradeText).withStyle(ChatFormatting.BOLD), leftRight - 9 - font.width(gradeText), scoreTop + 24, scoreColor, false);
        drawProgressBar(g, leftX + 9, scoreTop + 40, leftInnerW - 18, 5, score / 100.0F, scoreColor);
        if (!compact) g.drawCenteredString(font, Component.literal(buildVerdict(score)), leftX + leftInnerW / 2, scoreTop + 52, TEXT_SOFT);

        int progTop = scoreTop + scoreH + 7;
        int progBottom = panelBottom - 8;
        drawRoundedPanel(g, leftX, progTop, leftRight, progBottom, 4,
                mixColor(themeBorder(), themeAccent(), 0.20F), 0x66101924);
        g.drawString(font, Component.literal("PROGRESSION").withStyle(ChatFormatting.BOLD), leftX + 9, progTop + 7, themeAccentStrong(), false);
        int py = progTop + 24;
        int lineStep = compact ? 14 : 17;
        String[][] rows = {
                {"Level", String.valueOf(progression.level())},
                {"Upgrades", String.valueOf(upgrades)},
                {"XP", String.valueOf(vanillaXp)},
                {"Next cost", nextCost + " XP"}
        };
        for (String[] row : rows) {
            if (py + 9 >= progBottom - 4) break;
            g.drawString(font, Component.literal(row[0]), leftX + 9, py, TEXT_SOFT, false);
            int color = row[0].equals("XP") ? 0xFF63D77A : TEXT;
            g.drawString(font, Component.literal(row[1]).withStyle(ChatFormatting.BOLD), leftRight - 9 - font.width(row[1]), py, color, false);
            py += lineStep;
        }

        // Right header and current build.
        int contentX = rightX + 12;
        int contentRight = panelX + panelW - 12;
        int contentW = contentRight - contentX;
        g.drawString(font, Component.literal("CURRENT BUILD").withStyle(ChatFormatting.BOLD), contentX, panelTop + 10, themeAccentStrong(), false);
        int buildTop = panelTop + 28;
        int buildH = compact ? 58 : 68;
        drawRoundedPanel(g, contentX, buildTop, contentRight, buildTop + buildH, 4,
                mixColor(themeBorder(), themeAccent(), 0.18F), 0x50101924);
        List<SelectedChoice> choices = new ArrayList<>(SESSION_CHOICES.values());
        int choiceGap = 6;
        int choiceW = (contentW - choiceGap * 2) / 3;
        int[] choiceColors = {0xFF9B63D7, 0xFFD7645B, 0xFFD8B348};
        String[] fallbackLayers = {"BACKGROUND", "CLASS", "ORIGIN"};
        for (int i = 0; i < 3; i++) {
            int cx = contentX + i * (choiceW + choiceGap);
            SelectedChoice choice = i < choices.size() ? choices.get(i) : null;
            int c = choiceColors[i];
            if (i > 0) g.fill(cx - 3, buildTop + 8, cx - 2, buildTop + buildH - 8, 0xFF354255);
            String layer = choice == null ? fallbackLayers[i] : choice.layerName().toUpperCase(Locale.ROOT);
            g.drawString(font, Component.literal(font.plainSubstrByWidth(layer, choiceW - 16)).withStyle(ChatFormatting.BOLD), cx + 8, buildTop + 7, c, false);
            if (choice != null) renderEntryIcon(g, choice.id(), cx + 8, buildTop + 27, 18);
            String name = choice == null ? "Not selected" : choice.choiceName();
            g.drawString(font, Component.literal(font.plainSubstrByWidth(name, choiceW - 40)).withStyle(ChatFormatting.BOLD), cx + 32, buildTop + 28, TEXT, false);
            if (choice != null && buildH >= 64) {
                String tagLine = compactTagLine(choice.tags(), 2);
                g.drawString(font, Component.literal(font.plainSubstrByWidth(tagLine, choiceW - 40)),
                        cx + 32, buildTop + 44, tagColor(choice.tags().isEmpty() ? "Generalist" : cleanTag(choice.tags().get(0))), false);
            }
        }

        // Attribute rows. Fixed columns and strict clipping prevent any overlap.
        int attributesTop = buildTop + buildH + 20;
        int attributesBottom = panelBottom - 8;
        g.drawString(font, Component.literal("ATTRIBUTES & UPGRADES").withStyle(ChatFormatting.BOLD), contentX, attributesTop - 14, themeAccentStrong(), false);
        String[] names = {"OFFENSE", "DEFENSE", "UTILITY", "SURVIVAL"};
        String[] descriptions = {"Damage and attack power", "Armor and resistance", "Mobility and XP gain", "Health and recovery"};
        int[] colors = {0xFFE56565, 0xFF66B9ED, 0xFFE8C64F, 0xFF63D47A};
        int rowGap = 5;
        int rowH = Math.max(48, (attributesBottom - attributesTop - rowGap * 3) / 4);
        int controlsW = 88;
        int controlsLeft = contentRight - controlsW;
        int bonusW = Math.max(92, Math.min(155, contentW / 4));
        int bonusX = controlsLeft - bonusW - 8;
        int investedW = 58;
        int investedX = bonusX - investedW - 6;
        int valueW = 48;
        int valueX = investedX - valueW - 6;
        int labelRight = valueX - 8;
        for (int i = 0; i < 4; i++) {
            int sy = attributesTop + i * (rowH + rowGap);
            int c = colors[i];
            drawRoundedPanel(g, contentX, sy, contentRight, sy + rowH, 4,
                    mixColor(themeBorder(), c, 0.42F), mixColor(0xFF101722, c, 0.040F));
            g.fill(contentX + 1, sy + 7, contentX + 4, sy + rowH - 7, c);
            ArchitectIcons.draw(g, ArchitectIcons.stat(i), contentX + 9, sy + 6, 18);
            g.drawString(font, Component.literal(names[i]).withStyle(ChatFormatting.BOLD), contentX + 32, sy + 7, c, false);
            int descWidth = Math.max(50, labelRight - (contentX + 32));
            g.drawString(font, Component.literal(font.plainSubstrByWidth(descriptions[i], descWidth)), contentX + 32, sy + 23, MUTED, false);

            g.drawCenteredString(font, Component.literal(String.valueOf(finalStats[i])).withStyle(ChatFormatting.BOLD), valueX + valueW / 2, sy + 8, TEXT);
            drawProgressBar(g, valueX, sy + 27, valueW, 4, finalStats[i] / 100.0F, c);
            g.drawCenteredString(font, Component.literal(String.valueOf(allocated[i])).withStyle(ChatFormatting.BOLD), investedX + investedW / 2, sy + 8, c);
            g.drawCenteredString(font, Component.literal("invested"), investedX + investedW / 2, sy + 23, MUTED);

            List<String> bonuses = currentBonusLines(i, allocated[i]);
            String bonusText = bonuses.isEmpty() ? "No bonus yet" : String.join(" • ", bonuses);
            List<net.minecraft.util.FormattedCharSequence> wrappedBonus = font.split(Component.literal(bonusText), bonusW);
            int maxBonusLines = Math.max(1, Math.min(3, (rowH - 22) / 10));
            int bonusY = sy + 5;
            for (int line = 0; line < Math.min(maxBonusLines, wrappedBonus.size()); line++) {
                g.drawString(font, wrappedBonus.get(line), bonusX, bonusY, TEXT_SOFT, false);
                bonusY += 10;
            }
            boolean maxed = allocated[i] >= ArchitectProgression.MAX_ALLOCATION_PER_STAT;
            String cost = maxed ? "MAX" : nextCost + " XP";
            if (maxed) {
                float pulse = 0.55F + 0.45F * (float)Math.sin(System.currentTimeMillis() / 180.0D);
                int alpha = Mth.clamp((int)(pulse * 255.0F), 90, 255);
                int pulseColor = (alpha << 24) | (c & 0x00FFFFFF);
                int capstoneX = bonusX + Math.max(4, (bonusW - 16) / 2);
                ArchitectIcons.draw(g, ArchitectIcons.capstone(i), capstoneX, sy + rowH - 31, 16);
            }
            g.drawString(font, Component.literal(cost).withStyle(ChatFormatting.BOLD), bonusX, sy + rowH - 13,
                    maxed ? c : themeAccentStrong(), false);
        }

        // Footer is fully outside the content panel.
        g.fill(panelX, dockTop, panelX + panelW, height - 4, 0xEA080D14);
        g.fill(panelX + 8, dockTop, panelX + panelW - 8, dockTop + 2, themeAccent());
        String note = "Upgrades consume XP";
        g.drawCenteredString(font, Component.literal(note), width / 2, dockTop + 15, MUTED);

        renderStatsV5Tooltips(g, mouseX, mouseY, contentX, buildTop, choiceW, choiceGap,
                contentX, attributesTop, contentW, rowH, rowGap,
                new String[]{nextRewardText(0, allocated[0]), nextRewardText(1, allocated[1]), nextRewardText(2, allocated[2]), nextRewardText(3, allocated[3])}, nextCost);
    }

    private List<String> currentBonusLines(int statIndex, int points) {
        List<String> result = new ArrayList<>();
        int ranks = points / 2;
        int maxRanks = ArchitectProgression.MAX_ALLOCATION_PER_STAT / 2;
        boolean maximum = points >= ArchitectProgression.MAX_ALLOCATION_PER_STAT;
        switch (statIndex) {
            case 0 -> {
                double damage = ranks * (8.0D / maxRanks);
                double speed = ranks * (30.0D / maxRanks);
                result.add(String.format(Locale.ROOT, "+%.1f Damage • +%.0f%% Speed", damage, speed));
                result.add(maximum ? "CAPSTONE: 10% Life Steal" : "Bonus improves every 2 points");
            }
            case 1 -> {
                double armor = ranks * (6.0D / maxRanks);
                double toughness = ranks * (1.5D / maxRanks);
                result.add(String.format(Locale.ROOT, "+%.1f Armor • +%.2f Toughness", armor, toughness));
                result.add(maximum ? "CAPSTONE: Bulwark (20% knockback resist)" : "Bonus improves every 2 points");
            }
            case 2 -> {
                double move = ranks * (15.0D / maxRanks);
                double xp = ranks * (30.0D / maxRanks);
                result.add(String.format(Locale.ROOT, "+%.1f%% Move • +%.0f%% XP", move, xp));
                result.add(maximum ? "CAPSTONE: +10% Swim Speed" : "Bonus improves every 2 points");
            }
            default -> {
                double health = ranks * (12.0D / maxRanks);
                result.add(String.format(Locale.ROOT, "+%.1f%% Max Health", health));
                result.add(maximum ? "CAPSTONE: Last Stand resistance" : "Bonus improves every 2 points");
            }
        }
        return result;
    }

    private String nextRewardText(int statIndex, int points) {
        if (points >= ArchitectProgression.MAX_ALLOCATION_PER_STAT) {
            return switch (statIndex) {
                case 0 -> "Maximum: 10% Life Steal active";
                case 1 -> "Maximum: Bulwark active";
                case 2 -> "Maximum: +10% Swim Speed active";
                default -> "Maximum: Last Stand active";
            };
        }
        int nextBonusPoint = Math.min(ArchitectProgression.MAX_ALLOCATION_PER_STAT, ((points / 2) + 1) * 2);
        if (nextBonusPoint == ArchitectProgression.MAX_ALLOCATION_PER_STAT) {
            return switch (statIndex) {
                case 0 -> "At 60: +8 Damage, +30% Speed and Life Steal";
                case 1 -> "At 60: +6 Armor, +1.5 Toughness and Bulwark";
                case 2 -> "At 60: +15% Move, +30% XP and Swim Speed";
                default -> "At 60: +12% Health and Last Stand";
            };
        }
        return switch (statIndex) {
            case 0 -> "At " + nextBonusPoint + ": more damage and attack speed";
            case 1 -> "At " + nextBonusPoint + ": more armor and toughness";
            case 2 -> "At " + nextBonusPoint + ": more movement speed and XP gain";
            default -> "At " + nextBonusPoint + ": more maximum health";
        };
    }

    private void renderStatsV5Tooltips(GuiGraphics g, int mouseX, int mouseY,
                                       int choicesX, int choicesY, int choiceW, int choiceGap,
                                       int statsX, int statsY, int statsW, int rowH, int rowGap,
                                       String[] nextRewards, int nextCost) {
        List<SelectedChoice> selectedChoices = new ArrayList<>(SESSION_CHOICES.values());
        String key = "";
        Runnable renderer = null;
        for (int i = 0; i < 3; i++) {
            int x = choicesX + i * (choiceW + choiceGap);
            if (inside(mouseX, mouseY, x, choicesY, choiceW, 52)) {
                final int index = i;
                key = "v5choice:" + i;
                renderer = () -> {
                    if (index < selectedChoices.size()) renderSelectedChoiceTooltip(g, selectedChoices.get(index), mouseX, mouseY);
                };
                break;
            }
        }
        if (renderer == null) {
            String[] names = {"Offense", "Defense", "Utility", "Survival"};
            for (int i = 0; i < 4; i++) {
                int y = statsY + i * (rowH + rowGap);
                if (inside(mouseX, mouseY, statsX, y, statsW, rowH)) {
                    final int index = i;
                    key = "v5stat:" + i;
                    renderer = () -> {
                        List<Component> lines = new ArrayList<>();
                        lines.add(Component.literal(names[index]).withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));
                        lines.add(Component.literal("Next: " + nextRewards[index]).withStyle(ChatFormatting.WHITE));
                        lines.add(Component.literal("Cost: " + nextCost + " XP").withStyle(ChatFormatting.GOLD));
                        g.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
                    };
                    break;
                }
            }
        }
        if (renderer == null) {
            hoveredTooltipKey = "";
            hoveredTooltipSince = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (!key.equals(hoveredTooltipKey)) {
            hoveredTooltipKey = key;
            hoveredTooltipSince = now;
            return;
        }
        if (now - hoveredTooltipSince >= TOOLTIP_DELAY_MS) renderer.run();
    }

    private void renderBuildSummary(GuiGraphics g, int mouseX, int mouseY) {
        updateStatAllocationButtons();
        if (profileOnly) {
            renderStatsScreenV5(g, mouseX, mouseY);
            return;
        }
        // Character Profile remains a full review screen, while Stats Screen is
        // a compact centred overlay so gameplay stays visible around it.
        final int outerW = profileOnly ? Math.min(width - 72, 900) : width - Math.max(12, width / 60) * 2;
        final int margin = profileOnly ? (width - outerW) / 2 : Math.max(12, width / 60);
        final int panelTop = profileOnly ? 48 : 44;
        final int dockTop = profileOnly ? height - 48 : height - 55;
        final int panelBottom = dockTop - 6;
        final int totalW = outerW;
        final int gap = 12;
        final int leftW = Mth.clamp((int)(totalW * 0.34F), 260, 340);
        final int leftX = margin;
        final int rightX = leftX + leftW + gap;
        final int rightW = totalW - leftW - gap;
        final int rightEdge = rightX + rightW;

        g.drawCenteredString(font, Component.literal(profileOnly ? "STATS SCREEN" : "CHARACTER PROFILE").withStyle(ChatFormatting.BOLD), width / 2, 10, themeAccentStrong());
        g.drawCenteredString(font, Component.literal(profileOnly ? "Current build, level and allocated attributes" : "Review your character before beginning the adventure"), width / 2, 24, MUTED);

        if (profileOnly) {
            drawRoundedPanel(g, margin - 8, panelTop - 8, rightEdge + 8, dockTop + 2, 9,
                    mixColor(themeBorder(), themeAccent(), 0.38F), 0xE60A1018);
        }

        drawRoundedPanel(g, leftX, panelTop, leftX + leftW, panelBottom, 8, themeBorder(), 0xFF101722);
        drawRoundedPanel(g, rightX, panelTop, rightEdge, panelBottom, 8, mixColor(themeBorder(), themeAccent(), 0.35F), 0xFF101722);
        g.fill(leftX + 10, panelTop + 1, leftX + leftW - 10, panelTop + 3, themeAccent());
        g.fill(rightX + 10, panelTop + 1, rightEdge - 10, panelTop + 3, themeAccentStrong());

        Minecraft mc = Minecraft.getInstance();
        String playerName = mc.player == null ? "Player" : mc.player.getGameProfile().getName();
        int score = buildScore();
        String grade = score >= 92 ? "S" : score >= 82 ? "A" : score >= 68 ? "B" : score >= 52 ? "C" : "D";
        int scoreColor = ratingColor(grade);
        ArchitectProgression.Data progression = profileOnly ? ClientArchitectState.data :
                (mc.player == null ? new ArchitectProgression.Data(0, 1, 0, 0, 0, 0, 4) : ArchitectProgression.get(mc.player));
        int nextXp = ArchitectProgression.xpForNextLevel(progression.level());
        int intoLevel = ArchitectProgression.xpIntoLevel(progression.xp());
        int[] baseStats = buildStats();
        int[] allocated = {progression.offense(), progression.defense(), progression.utility(), progression.survival()};
        int[] finalStats = new int[4];
        for (int i = 0; i < 4; i++) finalStats[i] = Mth.clamp(baseStats[i] + allocated[i], 0, 100);

        // LEFT COLUMN — all geometry is derived from the available panel height.
        int lx = leftX + 14;
        int lw = leftW - 28;
        int playerCenter = leftX + leftW / 2;
        g.drawCenteredString(font, Component.literal(playerName).withStyle(ChatFormatting.BOLD), playerCenter, panelTop + 12, TEXT);
        g.drawCenteredString(font, Component.literal("3D CHARACTER PREVIEW"), playerCenter, panelTop + 27, MUTED);

        int previewTop = panelTop + 42;
        int previewBottom = Math.min(previewTop + 108, panelBottom - 178);
        g.fill(lx + 10, previewBottom - 4, lx + lw - 10, previewBottom - 2, mixColor(themeBorder(), themeAccent(), 0.62F));
        g.fill(lx + lw / 3, previewBottom + 2, lx + (lw * 2) / 3, previewBottom + 5, mixColor(themeAccent(), 0xFFFFFFFF, 0.28F));
        if (mc.player != null) {
            renderPlayerPreview(g, lx + 28, previewTop, lx + lw - 28, previewBottom - 5,
                    Math.min(68, Math.max(54, leftW / 6)), mouseX, mouseY, mc.player);
        }
        g.drawCenteredString(font, Component.literal("Move mouse to rotate"), playerCenter, previewBottom + 7, MUTED_DARK);

        int scoreTop = previewBottom + 18;
        g.fill(lx, scoreTop, lx + lw, scoreTop + 1, 0xFF293646);
        g.drawString(font, Component.literal("BUILD SCORE").withStyle(ChatFormatting.BOLD), lx, scoreTop + 6, MUTED, false);
        g.drawString(font, Component.literal(score + " / 100").withStyle(ChatFormatting.BOLD), lx, scoreTop + 19, scoreColor, false);
        String gradeText = "GRADE " + grade;
        g.drawString(font, Component.literal(gradeText).withStyle(ChatFormatting.BOLD), lx + lw - font.width(gradeText), scoreTop + 19, scoreColor, false);
        drawProgressBar(g, lx, scoreTop + 33, lw, 5, score / 100.0F, scoreColor);
        g.drawString(font, Component.literal(buildVerdict(score)), lx, scoreTop + 40, TEXT_SOFT, false);

        int levelTop = scoreTop + 47;
        g.fill(lx, levelTop, lx + lw, levelTop + 1, 0xFF293646);
        g.drawString(font, Component.literal("LEVEL " + progression.level()).withStyle(ChatFormatting.BOLD), lx, levelTop + 7, 0xFFE7C95A, false);
        int totalAllocated = progression.offense() + progression.defense() + progression.utility() + progression.survival();
        String allocatedText = totalAllocated + " upgrades";
        g.drawString(font, Component.literal(allocatedText).withStyle(ChatFormatting.BOLD), lx + lw - font.width(allocatedText), levelTop + 7,
                themeAccentStrong(), false);
        int vanillaXp = mc.player == null ? progression.xp() : mc.player.totalExperience;
        int nextUpgradeCost = ArchitectProgression.upgradeCost(progression);
        float affordProgress = nextUpgradeCost <= 0 ? 1.0F : Mth.clamp(vanillaXp / (float)nextUpgradeCost, 0.0F, 1.0F);
        drawProgressBar(g, lx, levelTop + 21, lw, 5, affordProgress, themeAccent());
        String xpText = "XP " + vanillaXp + " • Next upgrade " + nextUpgradeCost + " XP";
        String clippedXp = font.plainSubstrByWidth(xpText, lw);
        g.drawString(font, Component.literal(clippedXp), lx + lw - font.width(clippedXp), levelTop + 29, MUTED, false);

        int statsTop = levelTop + 39;
        int statGap = 6;
        int statW = (lw - statGap) / 2;
        int availableStatsH = Math.max(72, panelBottom - statsTop - 8);
        int statH = Math.max(35, (availableStatsH - statGap) / 2);
        String[] statNames = {"OFFENSE", "DEFENSE", "UTILITY", "SURVIVAL"};
        int[] statColors = {0xFFE56565, 0xFF66B9ED, 0xFFE8C64F, 0xFF63D47A};
        String[] statDescriptions = new String[4];
        for (int i = 0; i < statDescriptions.length; i++) {
            statDescriptions[i] = buildStatMilestoneTooltip(i, progression, baseStats[i]);
        }
        for (int i = 0; i < 4; i++) {
            int col = i % 2;
            int row = i / 2;
            int sx = lx + col * (statW + statGap);
            int sy = statsTop + row * (statH + statGap);
            int c = statColors[i];
            drawRoundedPanel(g, sx, sy, sx + statW, sy + statH, 5,
                    mixColor(themeBorder(), c, 0.40F), mixColor(0xFF101722, c, 0.06F));
            ArchitectIcons.draw(g, ArchitectIcons.stat(i), sx + 6, sy + 4, 14);
            g.drawString(font, Component.literal(statNames[i]).withStyle(ChatFormatting.BOLD), sx + 24, sy + 5, c, false);
            String valueText = String.valueOf(finalStats[i]);
            g.drawString(font, Component.literal(valueText).withStyle(ChatFormatting.BOLD), sx + statW - font.width(valueText) - 7, sy + 5, TEXT, false);
            drawProgressBar(g, sx + 7, sy + 18, statW - 14, 4, finalStats[i] / 100.0F, c);
            String base = "Base " + baseStats[i] + "  +" + allocated[i];
            g.drawString(font, Component.literal(font.plainSubstrByWidth(base, statW - 14)), sx + 7, sy + 26, MUTED, false);
        }

        // RIGHT COLUMN — compact choices and bounded text areas.
        int rx = rightX + 14;
        int rw = rightW - 28;
        int y = panelTop + 12;
        g.drawString(font, Component.literal("FINAL BUILD").withStyle(ChatFormatting.BOLD), rx, y, themeAccentStrong(), false);
        y += 19;

        List<SelectedChoice> choices = new ArrayList<>(SESSION_CHOICES.values());
        int choiceGap = 8;
        int choiceW = (rw - choiceGap * 2) / 3;
        int choiceH = 66;
        int[] choiceColors = {0xFF9B63D7, 0xFFD7645B, 0xFFD8B348};
        String[] fallbackLayers = {"ORIGIN", "CLASS", "BACKGROUND"};
        for (int i = 0; i < 3; i++) {
            int cx = rx + i * (choiceW + choiceGap);
            SelectedChoice choice = i < choices.size() ? choices.get(i) : null;
            int c = choiceColors[i];
            g.fill(cx, y, cx + 3, y + choiceH, c);
            g.fill(cx + 9, y + choiceH - 1, cx + choiceW - 4, y + choiceH, mixColor(themeBorder(), c, 0.40F));
            String layer = choice == null ? fallbackLayers[i] : choice.layerName().toUpperCase(Locale.ROOT);
            g.drawString(font, Component.literal(font.plainSubstrByWidth(layer, choiceW - 18)).withStyle(ChatFormatting.BOLD), cx + 10, y + 7, c, false);
            if (choice != null) renderEntryIcon(g, choice.id(), cx + 10, y + 25, 20);
            else drawFallbackLayerIcon(g, cx + 10, y + 25, 20, i, c);
            String name = choice == null ? "Not selected" : choice.choiceName();
            String clippedName = font.plainSubstrByWidth(name, choiceW - 39);
            g.drawString(font, Component.literal(clippedName).withStyle(ChatFormatting.BOLD), cx + 35, y + 27, choice == null ? MUTED_DARK : TEXT, false);
            if (choice != null) {
                String tagLine = compactTagLine(choice.tags(), 2);
                g.drawString(font, Component.literal(font.plainSubstrByWidth(tagLine, choiceW - 39)),
                        cx + 35, y + 44, tagColor(choice.tags().isEmpty() ? "Generalist" : cleanTag(choice.tags().get(0))), false);
            }
        }
        int choicesTop = y;
        y += choiceH + 9;

        int synergyMinH = 108;
        int analysisBottom = Math.min(y + 92, panelBottom - synergyMinH - 11);
        drawRoundedPanel(g, rx, y, rx + rw, analysisBottom, 6, themeBorder(), 0xFF111923);
        int middle = rx + rw / 2;
        g.fill(middle, y + 9, middle + 1, analysisBottom - 9, 0xFF2D3948);
        g.drawString(font, Component.literal("✓ STRENGTHS").withStyle(ChatFormatting.BOLD), rx + 12, y + 9, SUCCESS, false);
        g.drawString(font, Component.literal("✕ WEAKNESSES").withStyle(ChatFormatting.BOLD), middle + 12, y + 9, 0xFFE58C8C, false);
        int lineY = y + 27;
        int maxLines = Math.max(2, (analysisBottom - lineY - 5) / 10);
        int count = 0;
        for (String strength : buildStrengths()) {
            for (var line : font.split(Component.literal("• " + strength), rw / 2 - 26)) {
                if (count++ >= maxLines) break;
                g.drawString(font, line, rx + 12, lineY, TEXT_SOFT, false);
                lineY += 10;
            }
            if (count >= maxLines) break;
        }
        lineY = y + 27;
        count = 0;
        for (String weakness : buildWeaknesses()) {
            for (var line : font.split(Component.literal("• " + weakness), rw / 2 - 26)) {
                if (count++ >= maxLines) break;
                g.drawString(font, line, middle + 12, lineY, TEXT_SOFT, false);
                lineY += 10;
            }
            if (count >= maxLines) break;
        }
        y = analysisBottom + 9;

        int synergyBottom = panelBottom - 10;
        int synergyColor = score >= 82 ? 0xFF9E6FE6 : score >= 68 ? 0xFF63D77A : score >= 52 ? 0xFFE2B44F : 0xFFD85C5C;
        drawRoundedPanel(g, rx, y, rx + rw, synergyBottom, 7, mixColor(themeBorder(), synergyColor, 0.52F), 0xFF111923);
        g.drawString(font, Component.literal("BUILD SYNERGY").withStyle(ChatFormatting.BOLD), rx + 13, y + 10, themeAccentStrong(), false);
        int synergyCenter = rx + rw / 2;
        g.pose().pushPose();
        g.pose().translate(synergyCenter, y + 25, 0);
        g.pose().scale(1.65F, 1.65F, 1.0F);
        g.drawCenteredString(font, Component.literal(score + "%").withStyle(ChatFormatting.BOLD), 0, 0, synergyColor);
        g.pose().popPose();
        g.drawCenteredString(font, Component.literal(buildVerdict(score)).withStyle(ChatFormatting.BOLD), synergyCenter, y + 45, synergyColor);
        drawProgressBar(g, rx + 16, y + 61, rw - 32, 7, score / 100.0F, synergyColor);
        String note = score >= 82 ? "Selections strongly reinforce one another." : score >= 68 ? "A balanced build with manageable weaknesses." : "A specialized build that rewards focused play.";
        if (synergyBottom - y >= 87) {
            g.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(note, rw - 30)), synergyCenter, y + 75, MUTED);
        }

        // Bottom action dock is outside both panels and owns all button space.
        g.fill(margin, dockTop, rightEdge, height - 4, 0xF2080D14);
        g.fill(margin + 8, dockTop, rightEdge - 8, dockTop + 2, themeAccent());

        renderProfileTooltips(g, mouseX, mouseY, rx, choicesTop, choiceW, choiceGap, y, synergyBottom,
                lx, statsTop, statW, statGap, statH, statDescriptions);
    }

    private void renderProfileTooltips(GuiGraphics g, int mouseX, int mouseY,
                                       int choicesX, int choicesY, int choiceW, int choiceGap,
                                       int synergyTop, int synergyBottom,
                                       int statsX, int statsY, int statW, int statGap, int statH,
                                       String[] statDescriptions) {
        List<SelectedChoice> selectedChoices = new ArrayList<>(SESSION_CHOICES.values());
        String key = "";
        Runnable renderer = null;

        for (int i = 0; i < 3; i++) {
            int x = choicesX + i * (choiceW + choiceGap);
            if (inside(mouseX, mouseY, x, choicesY, choiceW, 78)) {
                final int index = i;
                key = "choice:" + i + ":" + (i < selectedChoices.size() ? selectedChoices.get(i).id() : "empty");
                renderer = () -> {
                    if (index < selectedChoices.size()) renderSelectedChoiceTooltip(g, selectedChoices.get(index), mouseX, mouseY);
                    else {
                        String empty = index == 0 ? "No Origin selected." : index == 1 ? "No Class selected." : "No Background selected.";
                        g.renderTooltip(font, Component.literal(empty), mouseX, mouseY);
                    }
                };
                break;
            }
        }

        if (renderer == null) {
            for (int i = 0; i < 4; i++) {
                int x = statsX + (i % 2) * (statW + statGap);
                int y = statsY + (i / 2) * (statH + statGap);
                if (inside(mouseX, mouseY, x, y, statW, statH)) {
                    final int index = i;
                    key = "stat:" + i;
                    renderer = () -> g.renderTooltip(font, Component.literal(statDescriptions[index]), mouseX, mouseY);
                    break;
                }
            }
        }

        if (renderer == null && inside(mouseX, mouseY, choicesX, synergyTop,
                choiceW * 3 + choiceGap * 2, synergyBottom - synergyTop)) {
            key = "synergy";
            renderer = () -> g.renderTooltip(font,
                    Component.literal("Build Synergy estimates how well the selected Origin, Class and Background complement each other."),
                    mouseX, mouseY);
        }

        if (renderer == null) {
            hoveredTooltipKey = "";
            hoveredTooltipSince = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (!key.equals(hoveredTooltipKey)) {
            hoveredTooltipKey = key;
            hoveredTooltipSince = now;
            return;
        }
        if (now - hoveredTooltipSince >= TOOLTIP_DELAY_MS) renderer.run();
    }

    private void renderSelectedChoiceTooltip(GuiGraphics g, SelectedChoice choice, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(choice.layerName().toUpperCase(Locale.ROOT) + ": " + choice.choiceName())
                .withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        if (!choice.description().isBlank()) {
            for (var part : font.split(Component.literal(choice.description()).withStyle(ChatFormatting.GRAY), 280)) {
                lines.add(Component.literal(sequenceToString(part)).withStyle(ChatFormatting.GRAY));
            }
        }

        if (!choice.tags().isEmpty()) {
            lines.add(Component.literal("Tags: " + String.join(", ", choice.tags())).withStyle(ChatFormatting.GOLD));
        }

        int shown = Math.min(5, choice.powerNames().size());
        if (shown > 0) {
            lines.add(Component.literal("Powers").withStyle(ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE));
            for (int i = 0; i < shown; i++) {
                String powerName = choice.powerNames().get(i);
                String powerDesc = i < choice.powerDescs().size() ? choice.powerDescs().get(i) : "";
                lines.add(Component.literal("• " + powerName).withStyle(ChatFormatting.WHITE));
                if (!powerDesc.isBlank()) {
                    for (var part : font.split(Component.literal(powerDesc), 260)) {
                        lines.add(Component.literal("  " + sequenceToString(part)).withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
            if (choice.powerNames().size() > shown) {
                lines.add(Component.literal("+ " + (choice.powerNames().size() - shown) + " more powers")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (!choice.strengths().isBlank()) {
            lines.add(Component.literal("Strength: " + choice.strengths()).withStyle(ChatFormatting.GREEN));
        }
        if (!choice.weaknesses().isBlank()) {
            lines.add(Component.literal("Weakness: " + choice.weaknesses()).withStyle(ChatFormatting.RED));
        }

        g.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    private static String sequenceToString(net.minecraft.util.FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void renderEntryIcon(GuiGraphics g, ResourceLocation id, int x, int y, int size) {
        try {
            OriginDetailViewModel vm = OriginDetailViewModel.compute(id, isOrb);
            if (vm != OriginDetailViewModel.EMPTY && vm.origin() != null) {
                float scale = size / 16.0F;
                g.pose().pushPose();
                g.pose().translate(x, y, 10);
                g.pose().scale(scale, scale, 1.0F);
                g.renderItem(vm.origin().icon(), 0, 0);
                g.pose().popPose();
                return;
            }
        } catch (RuntimeException ignored) { }
        drawFallbackLayerIcon(g, x, y, size, Math.floorMod(id.hashCode(), 3), themeAccent());
    }

    private String primaryTagFor(ResourceLocation id) {
        List<String> tags = tagsFor(id);
        return tags.isEmpty() ? "Generalist" : cleanTag(tags.get(0));
    }

    private void drawFallbackLayerIcon(GuiGraphics g, int x, int y, int size, int type, int color) {
        String glyph = type == 0 ? "◆" : type == 1 ? "⚔" : "♛";
        g.pose().pushPose();
        g.pose().translate(x + size / 2.0F, y + size / 2.0F - 4, 0);
        g.pose().scale(Math.max(1.0F, size / 18.0F), Math.max(1.0F, size / 18.0F), 1.0F);
        g.drawCenteredString(font, Component.literal(glyph), 0, 0, color);
        g.pose().popPose();
    }

    private String compactTagLine(List<String> tags, int limit) {
        if (tags == null || tags.isEmpty()) return "Generalist";
        List<String> cleaned = new ArrayList<>();
        for (String tag : tags) {
            String value = cleanTag(tag);
            if (!value.isBlank() && !cleaned.contains(value)) cleaned.add(value);
            if (cleaned.size() >= limit) break;
        }
        return cleaned.isEmpty() ? "Generalist" : String.join(" • ", cleaned);
    }

    private String entryArchetype(List<String> tags) {
        String corpus = String.join(" ", tags).toLowerCase(Locale.ROOT);
        if (containsAny(corpus, "combat", "melee", "ranged")) return "OFFENSIVE";
        if (containsAny(corpus, "tank", "survival", "water")) return "DEFENSIVE";
        if (containsAny(corpus, "utility", "economy", "exploration")) return "UTILITY";
        if (containsAny(corpus, "mobility")) return "MOBILITY";
        if (containsAny(corpus, "magic")) return "ARCANE";
        return "GENERALIST";
    }

    private int archetypeColor(String type) {
        return switch (type) {
            case "OFFENSIVE" -> 0xFFE56565;
            case "DEFENSIVE" -> 0xFF66B9ED;
            case "UTILITY" -> 0xFFE8C64F;
            case "MOBILITY" -> 0xFF63D7B1;
            case "ARCANE" -> 0xFFB976FF;
            default -> MUTED;
        };
    }

    private int[] buildStats() {
        // Keep the foundation deliberately modest so allocation remains relevant
        // throughout a playthrough instead of the build starting near end-game.
        int offense = 10, defense = 10, utility = 10, survival = 10;
        for (SelectedChoice choice : SESSION_CHOICES.values()) {
            String corpus = (String.join(" ", choice.tags()) + " " + choice.strengths() + " " + choice.weaknesses()).toLowerCase(Locale.ROOT);
            if (containsAny(corpus, "combat", "melee", "ranged", "damage", "strength")) offense += 4;
            if (containsAny(corpus, "tank", "armor", "resistance", "shield", "survivability")) defense += 4;
            if (containsAny(corpus, "utility", "magic", "economy", "craft", "exploration")) utility += 4;
            if (containsAny(corpus, "health", "regeneration", "sustain", "survival", "aquatic")) survival += 4;
            if (containsAny(corpus, "lower maximum health", "takes extra damage")) survival -= 3;
            if (containsAny(corpus, "equipment or armor restrictions")) defense -= 3;
        }
        return new int[] {Mth.clamp(offense, 5, 50), Mth.clamp(defense, 5, 50), Mth.clamp(utility, 5, 50), Mth.clamp(survival, 5, 50)};
    }

    private void loadCurrentPlayerChoices() {
        SESSION_CHOICES.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            Map<ResourceLocation, ResourceLocation> current = ClientOriginState.getOrigins();
            if (current == null || current.isEmpty()) {
                current = mc.player.getData(OriginAttachments.originData()).getOrigins();
            }
            for (Map.Entry<ResourceLocation, ResourceLocation> entry : current.entrySet()) {
                ResourceLocation layerId = entry.getKey();
                ResourceLocation originId = entry.getValue();
                Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
                OriginLayer layer = LayerDataManager.INSTANCE.getLayer(layerId);
                if (origin == null) continue;
                OriginDetailViewModel view = OriginDetailViewModel.compute(originId, false);
                EntryAnalysis analysis = analyzeView(view);
                String layerName = layer != null ? layer.name().getString() : humanize(layerId.getPath());
                String choiceName = origin.name().getString();
                String description = origin.description().getString();
                List<String> powerNames = view != OriginDetailViewModel.EMPTY ? List.copyOf(view.powerNames()) : List.of();
                List<String> powerDescs = view != OriginDetailViewModel.EMPTY ? List.copyOf(view.powerDescs()) : List.of();
                SESSION_CHOICES.put(layerId, new SelectedChoice(layerName, choiceName, originId,
                        analysis.score(), analysis.tags(), analysis.strengths(), analysis.weaknesses(),
                        description, powerNames, powerDescs));
            }
        } catch (RuntimeException ignored) {
            // Keep the profile screen usable even if an addon exposes incomplete client data.
        }
    }

    private String humanize(String value) {
        String[] parts = value.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? value : out.toString();
    }

    private int buildScore() {
        if (SESSION_CHOICES.isEmpty()) return 0;
        int total = 0;
        for (SelectedChoice choice : SESSION_CHOICES.values()) total += choice.score();
        int average = total / SESSION_CHOICES.size();
        int variety = Math.min(8, buildStrengths().size() * 2);
        return Mth.clamp(average + variety, 0, 100);
    }

    private String buildVerdict(int score) {
        if (score >= 92) return "Exceptional synergy";
        if (score >= 82) return "Powerful and versatile";
        if (score >= 68) return "Balanced adventurer";
        if (score >= 52) return "Focused specialist";
        return "Challenge build";
    }

    private List<String> buildStrengths() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (SelectedChoice choice : SESSION_CHOICES.values()) {
            for (String strength : splitAnalysis(choice.strengths())) counts.merge(strength, 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            for (SelectedChoice choice : SESSION_CHOICES.values()) {
                for (String tag : choice.tags()) {
                    String clean = tag.replaceAll("^[^A-Za-z]+", "");
                    counts.merge(clean, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(4).map(Map.Entry::getKey).toList();
    }

    private List<String> buildWeaknesses() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (SelectedChoice choice : SESSION_CHOICES.values()) {
            for (String weakness : splitAnalysis(choice.weaknesses())) counts.merge(weakness, 1, Integer::sum);
        }
        if (counts.isEmpty()) counts.put("No obvious drawback detected; read every power description carefully", 1);
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(4).map(Map.Entry::getKey).toList();
    }

    private void renderPlayerPreview(GuiGraphics g, int x1, int y1, int x2, int y2,
                                     int scale, float mouseX, float mouseY, LivingEntity entity) {
        try {
            for (Method method : InventoryScreen.class.getDeclaredMethods()) {
                if (!method.getName().equals("renderEntityInInventoryFollowsMouse")) continue;
                method.setAccessible(true);
                Class<?>[] types = method.getParameterTypes();
                Object[] args = new Object[types.length];
                int intIndex = 0;
                int floatIndex = 0;
                int[] ints = {x1, y1, x2, y2, scale};
                float[] floats = {0.0F, mouseX, mouseY};
                boolean supported = true;
                for (int i = 0; i < types.length; i++) {
                    if (GuiGraphics.class.isAssignableFrom(types[i])) args[i] = g;
                    else if (LivingEntity.class.isAssignableFrom(types[i])) args[i] = entity;
                    else if (types[i] == int.class && intIndex < ints.length) args[i] = ints[intIndex++];
                    else if (types[i] == float.class && floatIndex < floats.length) args[i] = floats[floatIndex++];
                    else { supported = false; break; }
                }
                if (supported) { method.invoke(null, args); return; }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        g.drawCenteredString(font, Component.literal("3D preview unavailable"), (x1 + x2) / 2, (y1 + y2) / 2, MUTED);
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



    private Map<Button, Component> hideButtonLabelsForVanillaPass() {
        Map<Button, Component> labels = new LinkedHashMap<>();
        rememberAndHide(labels, themeButton);
        rememberAndHide(labels, sort);
        rememberAndHide(labels, filter);
        rememberAndHide(labels, back);
        rememberAndHide(labels, favorite);
        rememberAndHide(labels, powersToggle);
        rememberAndHide(labels, random);
        rememberAndHide(labels, confirm);
        rememberAndHide(labels, summaryBack);
        rememberAndHide(labels, summaryConfirm);
        rememberAndHide(labels, resetStats);
        rememberAndHide(labels, confirmResetStats);
        rememberAndHide(labels, cancelResetStats);
        for (Button button : statButtons) rememberAndHide(labels, button);
        return labels;
    }

    private void rememberAndHide(Map<Button, Component> labels, Button button) {
        if (button == null || !button.visible) return;
        labels.put(button, button.getMessage());
        button.setMessage(Component.empty());
    }

    private void restoreButtonLabels(Map<Button, Component> labels) {
        labels.forEach(Button::setMessage);
    }

    /**
     * Paints an Origin Architect skin over vanilla button widgets. The original
     * widgets remain responsible for focus, narration and click handling, while
     * this pass gives every screen a consistent RPG-style action language.
     */
    private void renderThemedButtons(GuiGraphics g) {
        renderThemedButton(g, themeButton, ButtonRole.THEME);
        renderThemedButton(g, sort, ButtonRole.SECONDARY);
        renderThemedButton(g, filter, ButtonRole.SECONDARY);
        renderThemedButton(g, back, ButtonRole.BACK);
        renderThemedButton(g, favorite, ButtonRole.FAVORITE);
        renderThemedButton(g, powersToggle, ButtonRole.SECONDARY);
        renderThemedButton(g, random, ButtonRole.RANDOM);
        renderThemedButton(g, confirm, ButtonRole.PRIMARY);
        renderThemedButton(g, summaryBack, ButtonRole.BACK);
        renderThemedButton(g, summaryConfirm, ButtonRole.PRIMARY);
        renderThemedButton(g, resetStats, ButtonRole.DANGER);
        renderThemedButton(g, confirmResetStats, ButtonRole.DANGER);
        renderThemedButton(g, cancelResetStats, ButtonRole.BACK);
        for (Button button : statButtons) {
            renderThemedButton(g, button, ButtonRole.STAT);
        }
    }

    private void renderThemedButton(GuiGraphics g, Button button, ButtonRole role) {
        if (button == null || !button.visible) return;

        int x1 = button.getX();
        int y1 = button.getY();
        int x2 = x1 + button.getWidth();
        int y2 = y1 + button.getHeight();
        boolean hovered = button.isHoveredOrFocused();

        int accent = switch (role) {
            case PRIMARY -> 0xFF66D98A;
            case FAVORITE -> 0xFFF0C85A;
            case DANGER -> 0xFFE26B73;
            case RANDOM -> mixColor(themeAccentStrong(), 0xFFD57BFF, 0.48F);
            case BACK -> mixColor(themeBorder(), 0xFFFFFFFF, 0.18F);
            case STAT -> themeAccentStrong();
            case THEME, SECONDARY -> themeAccent();
        };

        int fill = switch (role) {
            case PRIMARY -> mixColor(0xFF15231C, accent, hovered ? 0.32F : 0.20F);
            case FAVORITE -> mixColor(0xFF211D12, accent, hovered ? 0.28F : 0.16F);
            case DANGER -> mixColor(0xFF241619, accent, hovered ? 0.26F : 0.14F);
            case RANDOM -> mixColor(themeRaised(), accent, hovered ? 0.28F : 0.16F);
            case THEME -> mixColor(themePanel(), accent, hovered ? 0.24F : 0.13F);
            case STAT -> mixColor(themeRaised(), accent, hovered ? 0.25F : 0.12F);
            default -> mixColor(themePanel(), themeRaised(), hovered ? 0.66F : 0.42F);
        };

        if (!button.active) {
            accent = mixColor(accent, 0xFF626A75, 0.72F);
            fill = 0xFF141922;
        }

        drawRoundedPanel(g, x1, y1, x2, y2, 3, accent, fill);
        g.fill(x1 + 3, y1 + 2, x2 - 3, y1 + 3,
                hovered && button.active ? mixColor(accent, 0xFFFFFFFF, 0.35F) : mixColor(accent, fill, 0.60F));

        if (role == ButtonRole.PRIMARY) {
            g.fill(x1 + 2, y1 + 4, x1 + 4, y2 - 4, accent);
            g.fill(x2 - 4, y1 + 4, x2 - 2, y2 - 4, accent);
        } else if (role == ButtonRole.THEME) {
            g.fill(x1 + 3, y2 - 3, x2 - 3, y2 - 2, accent);
        }

        int textColor = button.active ? TEXT : MUTED_DARK;
        Component label = button.getMessage().copy().withStyle(ChatFormatting.BOLD);
        g.drawCenteredString(font, label, x1 + button.getWidth() / 2,
                y1 + (button.getHeight() - 8) / 2, textColor);
    }

    private enum ButtonRole {
        PRIMARY,
        SECONDARY,
        BACK,
        FAVORITE,
        RANDOM,
        THEME,
        DANGER,
        STAT
    }

    private void renderActionDock(GuiGraphics g) {
        int dockTop = shellBottom + 7;
        int dockBottom = height - 15;
        int left = shellX;
        int right = width - shellX;
        g.fill(left - 1, dockTop - 1, right + 1, dockBottom + 1, BORDER);
        g.fill(left, dockTop, right, dockBottom, 0xEE101720);
        g.fill(left, dockTop, right, dockTop + 1, ACCENT);

        // Summary/profile screens use the same visual dock, keeping the final
        // confirmation actions anchored and visually consistent.
        if (!summaryMode && right - left >= 650) {
            String help = "Mouse wheel: scroll  •  ↑/↓: navigate  •  Enter: select";
            g.drawCenteredString(font, Component.literal(help), width / 2, dockTop + 5, MUTED_DARK);
        }
    }

    private void renderFooter(GuiGraphics g) {
        // Stats sit just above the dock instead of underneath clickable widgets.
        String stats = presenter.totalLayers() + " layers  •  "
                + visibleRows().stream().filter(r -> !r.isSectionHeader()).count()
                + " shown  •  " + FAVORITES.size() + " favorites";
        int y = shellBottom - 12;
        g.drawString(font, stats, width - shellX - font.width(stats), y, MUTED_DARK, false);
    }

    private void drawRoundedPanel(GuiGraphics g, int x1, int y1, int x2, int y2, int radius, int border, int fill) {
        radius = Math.max(1, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        g.fill(x1 + radius, y1, x2 - radius, y2, border);
        g.fill(x1, y1 + radius, x2, y2 - radius, border);
        g.fill(x1 + radius + 1, y1 + 1, x2 - radius - 1, y2 - 1, fill);
        g.fill(x1 + 1, y1 + radius + 1, x2 - 1, y2 - radius - 1, fill);
        // Pixel-cut corners keep the shape crisp in Minecraft's UI scale.
        g.fill(x1 + radius - 1, y1 + 1, x1 + radius + 1, y1 + 2, fill);
        g.fill(x2 - radius - 1, y1 + 1, x2 - radius + 1, y1 + 2, fill);
        g.fill(x1 + radius - 1, y2 - 2, x1 + radius + 1, y2 - 1, fill);
        g.fill(x2 - radius - 1, y2 - 2, x2 - radius + 1, y2 - 1, fill);
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, float progress, int color) {
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        drawRoundedPanel(g, x, y, x + w, y + h, Math.max(2, h / 2), BORDER_SOFT, 0xFF1C2634);
        int fillW = Math.round((w - 2) * progress);
        if (fillW > 1) drawRoundedPanel(g, x + 1, y + 1, x + 1 + fillW, y + h - 1, Math.max(1, h / 2 - 1), color, color);
    }

    private int mixColor(int base, int accent, float amount) {
        amount = Mth.clamp(amount, 0.0F, 1.0F);
        int a = (base >>> 24) & 255;
        int r = Math.round(((base >>> 16) & 255) * (1 - amount) + ((accent >>> 16) & 255) * amount);
        int g = Math.round(((base >>> 8) & 255) * (1 - amount) + ((accent >>> 8) & 255) * amount);
        int b = Math.round((base & 255) * (1 - amount) + (accent & 255) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int impactColor(Impact impact) {
        return switch (impact.getDotCount()) {
            case 0 -> 0xFF687386;
            case 1 -> 0xFF4FC07B;
            case 2 -> 0xFFE3B648;
            default -> 0xFFE45E63;
        };
    }

    private int difficultyColor(int stars) {
        return switch (stars) {
            case 1 -> 0xFF61D58A;
            case 2 -> 0xFFE5D05A;
            case 3 -> 0xFFE6A24D;
            case 4 -> 0xFFE45E63;
            default -> 0xFFA96AE8;
        };
    }

    private void drawDifficultyStars(GuiGraphics g, int x, int y, int stars) {
        int color = difficultyColor(stars);
        for (int i = 0; i < 5; i++) {
            g.drawString(font, i < stars ? "★" : "☆", x + i * 8, y, i < stars ? color : 0xFF435064, false);
        }
    }

    private String cleanTag(String raw) {
        return raw.replaceAll("^[^A-Za-z0-9]+\\s*", "").trim();
    }

    private int tagColor(String tag) {
        String t = tag.toLowerCase(Locale.ROOT);
        if (t.contains("melee") || t.contains("combat") || t.contains("ranged")) return 0xFFD85B55;
        if (t.contains("magic") || t.contains("mana")) return 0xFFA66BE2;
        if (t.contains("water") || t.contains("aquatic")) return 0xFF4CAED9;
        if (t.contains("tank") || t.contains("survival")) return 0xFF5BBE72;
        if (t.contains("utility") || t.contains("economy")) return 0xFFD6B64A;
        if (t.contains("exploration")) return 0xFFE08B45;
        if (t.contains("mobility")) return 0xFF568FE0;
        return 0xFF6F7E91;
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
            int color = i < dots ? impactColor(impact) : 0xFF354153;
            g.fill(x + i * 9, y, x + i * 9 + 6, y + 6, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (transitionStage != TransitionStage.NONE || summaryMode) return super.mouseClicked(mouseX, mouseY, button);
        int x = shellX + 10;
        int y = shellTop + 102;
        int w = leftW - 20;
        int h = shellBottom - y - 12;
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            int index = (int) ((mouseY - y) / 48) + listScroll;
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
        if (transitionStage != TransitionStage.NONE || summaryMode) return true;
        if (mouseX < rightX) {
            targetListScroll = Math.max(0, targetListScroll - (float) Math.signum(scrollY));
            return true;
        }
        targetDetailScroll = Mth.clamp(targetDetailScroll - (float) Math.signum(scrollY) * 14.0F, 0.0F, (float) maxDetailScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (transitionStage != TransitionStage.NONE) return true;
        if (summaryMode) {
            if (keyCode == 256) { leaveSummary(); return true; }
            if (keyCode == 257 || keyCode == 335) { finalizeBuild(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
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

    private List<String> strengthItems(String text, List<String> tags) {
        ArrayList<String> result = new ArrayList<>();
        if (containsAny(text, "speed", "teleport", "flight", "jump", "dash", "movement")) result.add("High mobility and traversal options");
        if (containsAny(text, "damage", "attack", "weapon", "strength", "critical", "melee")) result.add("Strong combat potential");
        if (containsAny(text, "health", "armor", "resistance", "shield", "regeneration", "heal")) result.add("Good survivability and sustain");
        if (containsAny(text, "magic", "spell", "mana", "arcane")) result.add("Useful magical or ability-based utility");
        if (containsAny(text, "water", "swim", "ocean", "aquatic", "underwater")) result.add("Excels in aquatic environments");
        if (containsAny(text, "mining", "ore", "craft", "loot", "trade", "discount")) result.add("Strong gathering, crafting, or economy utility");
        if (containsAny(text, "vision", "explore", "night", "dimension", "compass")) result.add("Excellent exploration tools");
        if (result.isEmpty()) result.add(tags.contains("Generalist") ? "Flexible general-purpose playstyle" : "Specialized strengths based on its powers");
        return result.subList(0, Math.min(4, result.size()));
    }

    private List<String> weaknessItems(String text) {
        ArrayList<String> result = new ArrayList<>();
        if (containsAny(text, "water damage", "damage in water", "hurt by water", "weak to water", "vulnerable to water")) result.add("Vulnerable to water");
        if (containsAny(text, "burn in sunlight", "sunlight", "daylight", "fire damage", "weak to fire", "vulnerable to fire")) result.add("Vulnerable to sunlight or fire");
        if (containsAny(text, "cannot eat", "food restriction", "restricted diet", "only eat")) result.add("Restricted food options");
        if (containsAny(text, "slower", "reduced speed", "slow movement", "cannot sprint")) result.add("Reduced mobility in some situations");
        if (containsAny(text, "less health", "reduced health", "fewer hearts", "low health")) result.add("Lower maximum health");
        if (containsAny(text, "cannot wear", "armor restriction", "no armor", "restricted armor")) result.add("Equipment or armor restrictions");
        if (containsAny(text, "takes more damage", "increased damage", "extra damage", "vulnerable")) result.add("Takes extra damage under certain conditions");
        if (containsAny(text, "cannot", "weak", "drawback", "disadvantage" ) && result.isEmpty()) result.add("Meaningful situational drawbacks");
        if (result.isEmpty()) result.add("No obvious drawback detected; read every power description carefully");
        return result.subList(0, Math.min(4, result.size()));
    }

    private String joinAnalysis(List<String> values) {
        return String.join(" | ", values);
    }

    private List<String> splitAnalysis(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("\\s*\\|\\s*"))
                .filter(v -> !v.isBlank()).toList();
    }

    private EntryAnalysis analyzeView(OriginDetailViewModel view) {
        if (view == OriginDetailViewModel.EMPTY || view.origin() == null) {
            return new EntryAnalysis(0, "No selection", List.of("Unknown"), "None", "None");
        }
        StringBuilder corpus = new StringBuilder(view.origin().name().getString()).append(' ')
                .append(view.origin().description().getString()).append(' ');
        view.powerNames().forEach(v -> corpus.append(v).append(' '));
        view.powerDescs().forEach(v -> corpus.append(v).append(' '));
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
        EntryMeta meta = new EntryMeta(view.origin().impact(), view.powerNames().size());
        int base = 40 + ratingScore(meta) * 6;
        int score = Mth.clamp(base, 0, 100);
        String verdict = score >= 90 ? "Exceptional synergy" : score >= 78 ? "Strong build" : score >= 64 ? "Balanced build" : "Specialist build";
        String strengths = joinAnalysis(strengthItems(text, tags));
        String weaknesses = joinAnalysis(weaknessItems(text));
        return new EntryAnalysis(score, verdict, List.copyOf(tags), strengths, weaknesses);
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
        String strengths = joinAnalysis(strengthItems(text, tags));
        String weaknesses = joinAnalysis(weaknessItems(text));
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

    private enum UiTheme {
        AQUA("Aqua", 0xFF080B12, 0xFF111927, 0xFF141A24, 0xFF1A2230, 0xFF2B3748, 0xFF75C8FF, 0xFFA6DEFF),
        AMETHYST("Amethyst", 0xFF0D0914, 0xFF1B1027, 0xFF1B1424, 0xFF24192F, 0xFF49375C, 0xFFB06CFF, 0xFFD4A8FF),
        NETHER("Nether", 0xFF100707, 0xFF26100B, 0xFF211312, 0xFF2E1915, 0xFF63342C, 0xFFFF6A3D, 0xFFFFAA75),
        EMERALD("Emerald", 0xFF07100D, 0xFF10261C, 0xFF12231C, 0xFF193127, 0xFF315C49, 0xFF55D98B, 0xFF9AF0BB),
        END("End", 0xFF080610, 0xFF181026, 0xFF171221, 0xFF21182E, 0xFF4A365E, 0xFFD066FF, 0xFFE6ACFF),
        MINIMAL("Minimal", 0xFF0B0C0F, 0xFF17191E, 0xFF171A20, 0xFF20242B, 0xFF3A404A, 0xFFD0D5DC, 0xFFFFFFFF);

        private final String label;
        private final int bgTop, bgBottom, panel, raised, border, accent, accentStrong;
        UiTheme(String label, int bgTop, int bgBottom, int panel, int raised, int border, int accent, int accentStrong) {
            this.label = label; this.bgTop = bgTop; this.bgBottom = bgBottom; this.panel = panel;
            this.raised = raised; this.border = border; this.accent = accent; this.accentStrong = accentStrong;
        }
        private UiTheme next() { UiTheme[] all = values(); return all[(ordinal() + 1) % all.length]; }
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

    private record SelectedChoice(String layerName, String choiceName, ResourceLocation id, int score,
                                  List<String> tags, String strengths, String weaknesses,
                                  String description, List<String> powerNames, List<String> powerDescs) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum TransitionStage {
        NONE, OUT, IN
    }

}
