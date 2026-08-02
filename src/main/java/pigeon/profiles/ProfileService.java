package pigeon.profiles;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.net.URI;
import java.util.UUID;

@Singleton
public class ProfileService {
    private final ProfileRepository repository;
    private final ProfileCodec codec;

    @Inject
    public ProfileService(ProfileRepository repository, ProfileCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    public List<PigeonProfile> list() {
        return repository.findAll();
    }

    public PigeonProfile create(String name) {
        PigeonProfile profile = PigeonProfile.create(normalizeName(name));
        repository.save(profile);
        return profile;
    }

    public PigeonProfile cloneProfile(UUID sourceId, String name) {
        PigeonProfile source = requireProfile(sourceId);
        PigeonProfile clone = source.copyAs(normalizeName(name));
        repository.save(clone);
        return clone;
    }

    public PigeonProfile rename(UUID id, String name) {
        PigeonProfile updated = requireProfile(id).toBuilder()
            .name(normalizeName(name))
            .build();
        repository.save(updated);
        return updated;
    }

    public PigeonProfile setEnabled(UUID id, boolean enabled) {
        PigeonProfile updated = requireProfile(id).toBuilder()
            .enabled(enabled)
            .build();
        repository.save(updated);
        return updated;
    }

    public PigeonProfile update(PigeonProfile profile) {
        if (profile == null) {
            throw new ProfileValidationException("Profile is required");
        }
        requireProfile(profile.getId());
        codec.validate(profile);
        repository.save(profile);
        return profile;
    }

    public boolean delete(UUID id) {
        return repository.delete(id);
    }

    public List<ProfileRouteOverlap> findOverlappingRoutes() {
        Map<String, List<String>> profilesByUrl = new LinkedHashMap<>();
        for (PigeonProfile profile : repository.findAll()) {
            if (!profile.isEnabled()) continue;
            LinkedHashSet<String> urls = new LinkedHashSet<>(profile.getWebhooks().getPrimary());
            profile.getWebhooks().getOverrides().values().forEach(urls::addAll);
            for (String url : urls) {
                profilesByUrl.computeIfAbsent(url.trim(), ignored -> new ArrayList<>()).add(profile.getName());
            }
        }

        List<ProfileRouteOverlap> overlaps = new ArrayList<>();
        profilesByUrl.forEach((url, names) -> {
            if (names.size() > 1) {
                overlaps.add(new ProfileRouteOverlap(endpoint(url), List.copyOf(names)));
            }
        });
        return List.copyOf(overlaps);
    }

    public String exportProfile(UUID id, boolean includeSecrets) {
        return codec.toJson(requireProfile(id), includeSecrets);
    }

    public PigeonProfile previewImport(String json) {
        return codec.fromJson(json);
    }

    public PigeonProfile importAsNew(String json) {
        return importAsNew(previewImport(json));
    }

    public PigeonProfile importAsNew(PigeonProfile profile) {
        codec.validate(profile);
        PigeonProfile imported = profile
            .toBuilder()
            .id(UUID.randomUUID())
            .enabled(false)
            .build();
        repository.save(imported);
        return imported;
    }

    private PigeonProfile requireProfile(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Profile not found: " + id));
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new ProfileValidationException("Profile name is required");
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new ProfileValidationException("Profile name is required");
        }
        if (normalized.length() > ProfileCodec.MAX_NAME_LENGTH) {
            throw new ProfileValidationException("Profile name cannot exceed " + ProfileCodec.MAX_NAME_LENGTH + " characters");
        }
        return normalized;
    }

    private String endpoint(String url) {
        URI uri = URI.create(url);
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT);
    }
}
