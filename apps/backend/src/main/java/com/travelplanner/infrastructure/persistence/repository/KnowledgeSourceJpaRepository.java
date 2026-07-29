package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.KnowledgeSourceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code knowledge_source} (V13).
 *
 * <p>Lookup is by {@code source_ref}, not by id. Seed files and the reserved stub marker address
 * sources by that handle precisely so a re-seed does not have to preserve generated uuids, and a
 * repository that only offered {@code findById} would push every caller into keeping a uuid map.
 *
 * <p>No list-returning finder, so no {@code join fetch} question arises here: this is the table the
 * other repositories fetch <em>into</em>.
 */
public interface KnowledgeSourceJpaRepository extends JpaRepository<KnowledgeSourceEntity, UUID> {

    Optional<KnowledgeSourceEntity> findBySourceRef(String sourceRef);
}
