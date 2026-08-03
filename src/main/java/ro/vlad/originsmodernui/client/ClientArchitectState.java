package ro.vlad.originsmodernui.client;

import ro.vlad.originsmodernui.ArchitectProgression;

/** Latest server-authoritative progression snapshot used by the HUD/profile. */
public final class ClientArchitectState {
    private ClientArchitectState() {}
    public static ArchitectProgression.Data data = new ArchitectProgression.Data(0, 1, 0, 0, 0, 0, 4);

    public static void update(ArchitectProgression.Data value) {
        ArchitectProgression.Data previous = data;
        data = value;
        ArchitectHud.onProgressUpdate(previous, value);
    }
}
