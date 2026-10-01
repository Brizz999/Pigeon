package pigeon.notifiers;

import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPreFired;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import pigeon.MockedTestBase;
import pigeon.profiles.ProfileRuntimeService;
import pigeon.profiles.ProfileRuntimeSnapshot;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeathNotifierScriptTest extends MockedTestBase {
    @Mock
    private ProfileRuntimeService profiles;

    @InjectMocks
    private DeathNotifier notifier;

    @Test
    void unrelatedScriptsDoNotReadProfiles() {
        ScriptPreFired event = event(1, mock(ScriptEvent.class));

        notifier.onScript(event);

        verifyNoInteractions(profiles);
    }

    @Test
    void missingScriptEventDoesNotReadProfiles() {
        notifier.onScript(event(2307, null));

        verifyNoInteractions(profiles);
    }

    @Test
    void tobPortalStillChecksProfiles() {
        when(profiles.snapshot()).thenReturn(new ProfileRuntimeSnapshot(true, Collections.emptyList()));

        notifier.onScript(event(2307, mock(ScriptEvent.class)));

        verify(profiles).snapshot();
    }

    private static ScriptPreFired event(int id, ScriptEvent scriptEvent) {
        ScriptPreFired event = mock(ScriptPreFired.class);
        when(event.getScriptId()).thenReturn(id);
        when(event.getScriptEvent()).thenReturn(scriptEvent);
        return event;
    }
}
