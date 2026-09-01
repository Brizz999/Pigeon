package pigeon.profiles;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigProfileRepositoryTest {
    private final Map<String, String> storedConfig = new HashMap<>();
    private Gson gson;
    private ProfileCodec codec;
    private ConfigProfileRepository repository;

    @BeforeEach
    void setUp() {
        gson = new Gson();
        ConfigManager configManager = mock(ConfigManager.class);

        when(configManager.getConfiguration(anyString(), anyString()))
            .thenAnswer(invocation -> storedConfig.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        doAnswer(invocation -> {
            Object value = invocation.getArgument(2);
            storedConfig.put(
                key(invocation.getArgument(0), invocation.getArgument(1)),
                String.valueOf(value)
            );
            return null;
        }).when(configManager).setConfiguration(anyString(), anyString(), any());
        doAnswer(invocation -> {
            storedConfig.remove(key(invocation.getArgument(0), invocation.getArgument(1)));
            return null;
        }).when(configManager).unsetConfiguration(anyString(), anyString());

        codec = spy(new ProfileCodec(gson));
        repository = new ConfigProfileRepository(configManager, gson, codec);
    }

    @Test
    void freshRepositoryStartsWithoutProfiles() {
        assertEquals(List.of(), repository.findAll());
    }

    @Test
    void reusesDecodedProfilesUntilStoredJsonChanges() {
        PigeonProfile profile = PigeonProfile.create("Clan");
        String profileJson = codec.toJson(profile, true);
        storedConfig.put(
            key(ConfigProfileRepository.CONFIG_GROUP, ConfigProfileRepository.INDEX_KEY),
            gson.toJson(List.of(profile.getId()))
        );
        storedConfig.put(
            key(ConfigProfileRepository.CONFIG_GROUP, "profile_" + profile.getId()),
            profileJson
        );
        clearInvocations(codec);

        PigeonProfile first = repository.findAll().get(0);
        PigeonProfile second = repository.findAll().get(0);

        assertSame(first, second);
        verify(codec, times(1)).fromJson(profileJson);

        PigeonProfile disabled = profile.toBuilder().enabled(false).build();
        String updatedJson = codec.toJson(disabled, true);
        storedConfig.put(
            key(ConfigProfileRepository.CONFIG_GROUP, "profile_" + profile.getId()),
            updatedJson
        );

        assertFalse(repository.findAll().get(0).isEnabled());
        verify(codec, times(1)).fromJson(updatedJson);
    }

    @Test
    void savesLoadsAndDeletesInIndexOrder() {
        PigeonProfile clan = PigeonProfile.create("Clan");
        PigeonProfile friends = PigeonProfile.create("Friends");

        repository.save(clan);
        repository.save(friends);
        repository.save(clan.toBuilder().enabled(false).build());

        assertEquals(
            List.of("Clan", "Friends"),
            repository.findAll().stream().map(PigeonProfile::getName).collect(java.util.stream.Collectors.toList())
        );
        assertFalse(repository.findById(clan.getId()).orElseThrow().isEnabled());

        repository.delete(clan.getId());
        assertEquals(List.of(friends), repository.findAll());
        assertFalse(repository.findById(clan.getId()).isPresent());
    }

    @Test
    void rejectsMalformedStoredIndex() {
        storedConfig.put(key(ConfigProfileRepository.CONFIG_GROUP, ConfigProfileRepository.INDEX_KEY), "not-json");

        assertThrows(ProfileValidationException.class, repository::findAll);
    }

    private static String key(String group, String item) {
        return group + "." + item;
    }
}
