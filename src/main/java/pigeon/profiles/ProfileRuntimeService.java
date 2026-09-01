package pigeon.profiles;

import com.google.gson.Gson;
import pigeon.PigeonConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Singleton
public class ProfileRuntimeService {
    private final ProfileRepository repository;
    private final Gson gson;
    private final PigeonConfig defaults;
    private final AtomicLong generation = new AtomicLong();
    private volatile CachedSnapshot cachedSnapshot;

    @Inject
    public ProfileRuntimeService(ProfileRepository repository, Gson gson, PigeonConfig defaults) {
        this.repository = repository;
        this.gson = gson;
        this.defaults = defaults;
    }

    /**
     * Returns the current immutable profile set. The same snapshot is reused
     * until profile storage changes.
     */
    public synchronized ProfileRuntimeSnapshot snapshot() {
        while (true) {
            long currentGeneration = generation.get();
            CachedSnapshot cached = cachedSnapshot;
            if (cached != null && cached.generation == currentGeneration) {
                return cached.snapshot;
            }

            List<PigeonProfile> profiles = repository.findAll();
            List<PigeonProfileConfig> enabled = profiles.stream()
                .filter(PigeonProfile::isEnabled)
                .map(profile -> new PigeonProfileConfig(gson, profile, defaults))
                .collect(Collectors.toList());
            ProfileRuntimeSnapshot snapshot = new ProfileRuntimeSnapshot(
                !profiles.isEmpty(),
                Collections.unmodifiableList(enabled)
            );

            // A profile may be edited on Swing's event thread while this snapshot
            // is being built. Rebuild instead of publishing stale profile data.
            if (generation.get() == currentGeneration) {
                cachedSnapshot = new CachedSnapshot(currentGeneration, snapshot);
                return snapshot;
            }
        }
    }

    public void invalidate() {
        generation.incrementAndGet();
        cachedSnapshot = null;
    }

    private static final class CachedSnapshot {
        private final long generation;
        private final ProfileRuntimeSnapshot snapshot;

        private CachedSnapshot(long generation, ProfileRuntimeSnapshot snapshot) {
            this.generation = generation;
            this.snapshot = snapshot;
        }
    }
}
