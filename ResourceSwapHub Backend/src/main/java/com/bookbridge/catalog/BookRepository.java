package com.bookbridge.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, UUID> {
    Optional<Book> findByIsbn(String isbn);

    Optional<Book> findByDedupeKey(String dedupeKey);

    @Query("""
            select b from Book b
            where lower(b.title) like lower(concat('%', :q, '%'))
               or lower(b.author) like lower(concat('%', :q, '%'))
               or lower(b.subject) like lower(concat('%', :q, '%'))
               or lower(coalesce(b.publisher, '')) like lower(concat('%', :q, '%'))
            order by b.title asc
            """)
    List<Book> search(@Param("q") String query, Pageable pageable);

    @Query("select distinct b.subject from Book b order by b.subject asc")
    List<String> findDistinctSubjects();

    @Query("select distinct b.language from Book b order by b.language asc")
    List<String> findDistinctLanguages();
}
