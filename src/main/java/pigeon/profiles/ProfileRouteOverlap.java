package pigeon.profiles;

import lombok.Value;

import java.util.List;

@Value
public class ProfileRouteOverlap {
    String endpoint;
    List<String> profileNames;
}
