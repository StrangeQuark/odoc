package com.strangequark.odoc.space;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRepository extends JpaRepository<Space, UUID> {
    List<Space> findAllByOrderByNameAsc();
    Optional<Space> findByKey(String key);
}
