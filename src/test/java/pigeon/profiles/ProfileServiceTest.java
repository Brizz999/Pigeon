package pigeon.profiles;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileServiceTest {
    private InMemoryProfileRepository repository;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProfileRepository();
        service = new ProfileService(repository, new ProfileCodec(new Gson()));
    }

    @Test
    void createsClonesAndEnablesProfilesIndependently() {
        PigeonProfile original = service.create("Clan");
        PigeonProfile clone = service.cloneProfile(original.getId(), "Friends");

        assertNotEquals(original.getId(), clone.getId());
        assertEquals(
            List.of("Clan", "Friends"),
            service.list().stream().map(PigeonProfile::getName).collect(Collectors.toList())
        );

        service.setEnabled(original.getId(), false);
        assertFalse(repository.findById(original.getId()).orElseThrow().isEnabled());
        assertTrue(repository.findById(clone.getId()).orElseThrow().isEnabled());
    }

    @Test
    void importsAsNewProfileWithoutReusingSharedId() {
        PigeonProfile source = service.create("Shared");
        String export = service.exportProfile(source.getId(), false);

        PigeonProfile imported = service.importAsNew(export);

        assertNotEquals(source.getId(), imported.getId());
        assertEquals(source.getName(), imported.getName());
        assertFalse(imported.isEnabled());
    }

    @Test
    void deletesOnlyRequestedProfile() {
        PigeonProfile first = service.create("First");
        PigeonProfile second = service.create("Second");

        assertTrue(service.delete(first.getId()));
        assertFalse(service.delete(first.getId()));
        assertEquals(List.of(second), service.list());
    }

    @Test
    void updatesExistingProfileConfiguration() {
        PigeonProfile original = service.create("Clan");
        PigeonProfile updated = original.toBuilder()
            .webhooks(new ProfileWebhooks(List.of("https://example.com/clan"), Map.of()))
            .build();

        service.update(updated);

        assertEquals(updated, repository.findById(original.getId()).orElseThrow());
    }

    @Test
    void reportsOverlappingEnabledRoutesWithoutExposingWebhookSecrets() {
        String shared = "https://discord.com/api/webhooks/123/super-secret-token";
        PigeonProfile clan = service.create("Clan");
        service.update(clan.toBuilder()
            .webhooks(new ProfileWebhooks(List.of(shared), Map.of()))
            .build());
        PigeonProfile friends = service.create("Friends");
        service.update(friends.toBuilder()
            .webhooks(new ProfileWebhooks(List.of(), Map.of("lootWebhook", List.of(shared))))
            .build());
        PigeonProfile disabled = service.create("Disabled");
        service.update(disabled.toBuilder()
            .webhooks(new ProfileWebhooks(List.of(shared), Map.of()))
            .enabled(false)
            .build());

        List<ProfileRouteOverlap> overlaps = service.findOverlappingRoutes();

        assertEquals(1, overlaps.size());
        assertEquals("https://discord.com", overlaps.get(0).getEndpoint());
        assertEquals(List.of("Clan", "Friends"), overlaps.get(0).getProfileNames());
        assertFalse(overlaps.get(0).toString().contains("super-secret-token"));
    }

    private static class InMemoryProfileRepository implements ProfileRepository {
        private final Map<UUID, PigeonProfile> profiles = new LinkedHashMap<>();

        @Override
        public List<PigeonProfile> findAll() {
            return new ArrayList<>(profiles.values());
        }

        @Override
        public Optional<PigeonProfile> findById(UUID id) {
            return Optional.ofNullable(profiles.get(id));
        }

        @Override
        public void save(PigeonProfile profile) {
            profiles.put(profile.getId(), profile);
        }

        @Override
        public boolean delete(UUID id) {
            return profiles.remove(id) != null;
        }
    }
}
