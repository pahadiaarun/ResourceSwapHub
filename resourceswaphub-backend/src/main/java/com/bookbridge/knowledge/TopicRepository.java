package com.bookbridge.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findByNormalizedName(String normalizedName);

    List<Topic> findByNormalizedNameIn(Collection<String> normalizedNames);

    /**
     * Autocomplete over canonical names and aliases, ranked per brief Part 8 §18:
     * exact canonical > prefix canonical > exact alias > trigram similarity > name.
     * pg_trgm is enabled in V5. CASTs keep the optional :type filter typed when null.
     */
    @Query(value = """
            SELECT t.* FROM topics t
            LEFT JOIN topic_aliases a ON a.topic_id = t.id
            WHERE (CAST(:type AS text) IS NULL OR t.topic_type = CAST(:type AS text))
              AND (
                t.normalized_name LIKE CONCAT('%', :q, '%')
                OR a.normalized_alias LIKE CONCAT('%', :q, '%')
              )
            GROUP BY t.id
            ORDER BY
              bool_or(t.normalized_name = :q) DESC,
              bool_or(t.normalized_name LIKE CONCAT(:q, '%')) DESC,
              bool_or(a.normalized_alias = :q) DESC,
              max(similarity(t.normalized_name, :q)) DESC,
              t.canonical_name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Topic> search(@Param("q") String q, @Param("type") String type, @Param("limit") int limit);
}
