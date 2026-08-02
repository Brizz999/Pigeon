package pigeon.profiles;

import com.google.inject.ImplementedBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ImplementedBy(ConfigProfileRepository.class)
public interface ProfileRepository {
    List<PigeonProfile> findAll();

    Optional<PigeonProfile> findById(UUID id);

    void save(PigeonProfile profile);

    boolean delete(UUID id);
}
