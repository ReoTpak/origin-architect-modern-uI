package ro.vlad.originsmodernui;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ro.vlad.originsmodernui.client.ClientArchitectState;

/** Small network layer for authoritative stat allocation and client profile syncing. */
@EventBusSubscriber(modid = OriginsModernUI.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ArchitectNetwork {
    private ArchitectNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(AllocatePayload.TYPE, AllocatePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ArchitectProgression.Stat[] values = ArchitectProgression.Stat.values();
                        if (payload.stat() >= 0 && payload.stat() < values.length) {
                            ArchitectProgression.allocate(player, values[payload.stat()], payload.delta());
                            sync(player);
                        }
                    }
                }));
        registrar.playToServer(ResetPayload.TYPE, ResetPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ArchitectProgression.reset(player);
                        sync(player);
                    }
                }));
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientArchitectState.update(payload.toData())));
    }

    public static void allocate(ArchitectProgression.Stat stat, int delta) {
        PacketDistributor.sendToServer(new AllocatePayload(stat.ordinal(), delta));
    }

    public static void reset() {
        PacketDistributor.sendToServer(new ResetPayload());
    }

    public static void sync(ServerPlayer player) {
        ArchitectProgression.Data data = ArchitectProgression.get(player);
        PacketDistributor.sendToPlayer(player, SyncPayload.from(data));
    }

    public record AllocatePayload(int stat, int delta) implements CustomPacketPayload {
        public static final Type<AllocatePayload> TYPE = new Type<>(id("allocate_stat"));
        public static final StreamCodec<ByteBuf, AllocatePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AllocatePayload::stat,
                ByteBufCodecs.VAR_INT, AllocatePayload::delta,
                AllocatePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ResetPayload() implements CustomPacketPayload {
        public static final Type<ResetPayload> TYPE = new Type<>(id("reset_stats"));
        public static final StreamCodec<ByteBuf, ResetPayload> STREAM_CODEC = StreamCodec.unit(new ResetPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncPayload(int xp, int level, int offense, int defense, int utility, int survival, int unspent)
            implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(id("sync_stats"));
        public static final StreamCodec<ByteBuf, SyncPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SyncPayload decode(ByteBuf buf) {
                return new SyncPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf));
            }
            @Override
            public void encode(ByteBuf buf, SyncPayload value) {
                ByteBufCodecs.VAR_INT.encode(buf, value.xp());
                ByteBufCodecs.VAR_INT.encode(buf, value.level());
                ByteBufCodecs.VAR_INT.encode(buf, value.offense());
                ByteBufCodecs.VAR_INT.encode(buf, value.defense());
                ByteBufCodecs.VAR_INT.encode(buf, value.utility());
                ByteBufCodecs.VAR_INT.encode(buf, value.survival());
                ByteBufCodecs.VAR_INT.encode(buf, value.unspent());
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public ArchitectProgression.Data toData() {
            return new ArchitectProgression.Data(xp, level, offense, defense, utility, survival, unspent);
        }
        public static SyncPayload from(ArchitectProgression.Data d) {
            return new SyncPayload(d.xp(), d.level(), d.offense(), d.defense(), d.utility(), d.survival(), d.unspent());
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OriginsModernUI.MOD_ID, path);
    }
}
