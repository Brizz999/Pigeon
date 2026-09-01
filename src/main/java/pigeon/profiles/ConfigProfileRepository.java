package pigeon.profiles;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class ConfigProfileRepository implements ProfileRepository {
    public static final String CONFIG_GROUP = "pigeonProfiles";
    public static final String INDEX_KEY = "index";
    private static final String PROFILE_KEY_PREFIX = "profile_";
    private static final Type INDEX_TYPE = new TypeToken<List<UUID>>() { }.getType();

    private final ConfigManager configManager;
    private final Gson gson;
    private final ProfileCodec codec;
    private final Map<UUID, CachedProfile> cachedProfiles = new HashMap<>();
    private String cachedIndexJson;
    private List<UUID> cachedIndex = Collections.emptyList();

    @Inject
    public ConfigProfileRepository(ConfigManager configManager, Gson gson, ProfileCodec codec) {
        this.configManager = configManager;
        this.gson = gson;
        this.codec = codec;
    }

    @Override
    public synchronized List<PigeonProfile> findAll() {
        List<PigeonProfile> profiles = new ArrayList<>();
        for (UUID id : readIndex()) {
            findById(id).ifPresent(profiles::add);
        }
        return Collections.unmodifiableList(profiles);
    }

    @Override
    public synchronized Optional<PigeonProfile> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String json = configManager.getConfiguration(CONFIG_GROUP, profileKey(id));
        if (json == null || json.trim().isEmpty()) {
            cachedProfiles.remove(id);
            return Optional.empty();
        }

        CachedProfile cached = cachedProfiles.get(id);
        if (cached != null && json.equals(cached.json)) {
            return Optional.of(cached.profile);
        }

        PigeonProfile profile = codec.fromJson(json);
        cachedProfiles.put(id, new CachedProfile(json, profile));
        return Optional.of(profile);
    }

    @Override
    public synchronized void save(PigeonProfile profile) {
        codec.validate(profile);
        String json = codec.toJson(profile, true);

        // Store a complete document before making it reachable through the index.
        configManager.setConfiguration(CONFIG_GROUP, profileKey(profile.getId()), json);
        cachedProfiles.put(profile.getId(), new CachedProfile(json, profile));

        List<UUID> ids = new ArrayList<>(readIndex());
        if (!ids.contains(profile.getId())) {
            ids.add(profile.getId());
            writeIndex(ids);
        }
    }

    @Override
    public synchronized boolean delete(UUID id) {
        List<UUID> ids = new ArrayList<>(readIndex());
        if (!ids.remove(id)) {
            return false;
        }

        // Make the document unreachable before removing its data.
        writeIndex(ids);
        configManager.unsetConfiguration(CONFIG_GROUP, profileKey(id));
        cachedProfiles.remove(id);
        return true;
    }

    private List<UUID> readIndex() {
        String json = configManager.getConfiguration(CONFIG_GROUP, INDEX_KEY);
        if (Objects.equals(json, cachedIndexJson)) {
            return cachedIndex;
        }
        if (json == null || json.trim().isEmpty()) {
            updateIndexCache(json, Collections.emptyList());
            return cachedIndex;
        }
        try {
            List<UUID> ids = gson.fromJson(json, INDEX_TYPE);
            updateIndexCache(json, ids == null ? Collections.emptyList() : ids);
            return cachedIndex;
        } catch (JsonParseException exception) {
            throw new ProfileValidationException("Stored profile index is malformed", exception);
        }
    }

    private void writeIndex(List<UUID> ids) {
        String json = gson.toJson(ids, INDEX_TYPE);
        configManager.setConfiguration(CONFIG_GROUP, INDEX_KEY, json);
        updateIndexCache(json, ids);
    }

    private void updateIndexCache(String json, List<UUID> ids) {
        cachedIndexJson = json;
        cachedIndex = Collections.unmodifiableList(new ArrayList<>(ids));
        cachedProfiles.keySet().retainAll(cachedIndex);
    }

    private static String profileKey(UUID id) {
        return PROFILE_KEY_PREFIX + id;
    }

    private static final class CachedProfile {
        private final String json;
        private final PigeonProfile profile;

        private CachedProfile(String json, PigeonProfile profile) {
            this.json = json;
            this.profile = profile;
        }
    }
}
