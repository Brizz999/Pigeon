package pigeon.profiles;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import pigeon.PigeonConfig;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileRuntimeServiceTest {
    @Test
    void reusesSnapshotUntilInvalidated() {
        ProfileRepository repository = mock(ProfileRepository.class);
        PigeonProfile profile = PigeonProfile.create("Clan");
        when(repository.findAll()).thenReturn(List.of(profile));
        ProfileRuntimeService service = new ProfileRuntimeService(
            repository,
            new Gson(),
            mock(PigeonConfig.class)
        );

        ProfileRuntimeSnapshot first = service.snapshot();
        ProfileRuntimeSnapshot second = service.snapshot();

        assertSame(first, second);
        assertTrue(first.isProfilesConfigured());
        verify(repository, times(1)).findAll();

        service.invalidate();
        ProfileRuntimeSnapshot rebuilt = service.snapshot();

        assertNotSame(first, rebuilt);
        verify(repository, times(2)).findAll();
    }

    @Test
    void doesNotPublishSnapshotInvalidatedWhileItIsBeingBuilt() {
        ProfileRepository repository = mock(ProfileRepository.class);
        PigeonProfile original = PigeonProfile.create("Original");
        PigeonProfile updated = PigeonProfile.create("Updated");
        AtomicReference<ProfileRuntimeService> serviceReference = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        when(repository.findAll()).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                serviceReference.get().invalidate();
                return List.of(original);
            }
            return List.of(updated);
        });

        ProfileRuntimeService service = new ProfileRuntimeService(
            repository,
            new Gson(),
            mock(PigeonConfig.class)
        );
        serviceReference.set(service);

        ProfileRuntimeSnapshot snapshot = service.snapshot();

        assertEquals("Updated", snapshot.getEnabledProfiles().get(0).getProfile().getName());
        verify(repository, times(2)).findAll();
        assertSame(snapshot, service.snapshot());
    }
}
