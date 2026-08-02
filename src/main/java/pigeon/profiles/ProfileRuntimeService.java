package pigeon.profiles;

import com.google.gson.Gson;
import pigeon.PigeonConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ProfileRuntimeService {
    private final ProfileRepository repository;
    private final Gson gson;
    private final PigeonConfig defaults;

    @Inject
    public ProfileRuntimeService(ProfileRepository repository, Gson gson, PigeonConfig defaults) {
        this.repository = repository;
        this.gson = gson;
        this.defaults = defaults;
    }

    /**
     * Captures one immutable profile set for a single detected game event.
     */
    public ProfileRuntimeSnapshot snapshot() {
        List<PigeonProfile> profiles = repository.findAll();
        List<PigeonProfileConfig> enabled = profiles.stream()
            .filter(PigeonProfile::isEnabled)
            .map(profile -> new PigeonProfileConfig(gson, profile, defaults))
            .collect(Collectors.toList());
        return new ProfileRuntimeSnapshot(
            !profiles.isEmpty(),
            Collections.unmodifiableList(enabled)
        );
    }
}
