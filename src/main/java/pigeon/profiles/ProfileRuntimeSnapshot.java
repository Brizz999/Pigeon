package pigeon.profiles;

import lombok.Value;

import java.util.List;

@Value
public class ProfileRuntimeSnapshot {
    boolean profilesConfigured;
    List<PigeonProfileConfig> enabledProfiles;
}
