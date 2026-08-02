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
import java.util.List;
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
        return json == null || json.trim().isEmpty()
            ? Optional.empty()
            : Optional.of(codec.fromJson(json));
    }

    @Override
    public synchronized void save(PigeonProfile profile) {
        codec.validate(profile);
        String json = codec.toJson(profile, true);

        // Store a complete document before making it reachable through the index.
        configManager.setConfiguration(CONFIG_GROUP, profileKey(profile.getId()), json);

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
        return true;
    }

    private List<UUID> readIndex() {
        String json = configManager.getConfiguration(CONFIG_GROUP, INDEX_KEY);
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<UUID> ids = gson.fromJson(json, INDEX_TYPE);
            return ids == null ? Collections.emptyList() : ids;
        } catch (JsonParseException exception) {
            throw new ProfileValidationException("Stored profile index is malformed", exception);
        }
    }

    private void writeIndex(List<UUID> ids) {
        configManager.setConfiguration(CONFIG_GROUP, INDEX_KEY, gson.toJson(ids, INDEX_TYPE));
    }

    private static String profileKey(UUID id) {
        return PROFILE_KEY_PREFIX + id;
    }
}
