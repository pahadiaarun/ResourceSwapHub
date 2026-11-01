package com.bookbridge.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Rows the directory has not placed yet, plus rows whose mirrored text disagrees with the
     * ids already stored on them, walked with an id cursor.
     */
    @Query("select u.id from User u where u.id > :after and ("
            + "(u.cityId is null and u.city is not null) "
            + "or (u.stateId is not null and (u.state is null "
            + "or u.state <> (select s.name from State s where s.id = u.stateId)))) "
            + "order by u.id")
    List<UUID> findLocationCandidateIdsAfter(@Param("after") UUID after, Pageable pageable);
}
