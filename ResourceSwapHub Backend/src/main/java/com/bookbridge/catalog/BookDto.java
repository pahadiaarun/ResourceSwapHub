package com.bookbridge.catalog;

import java.util.UUID;

public record BookDto(
        UUID id,
        String title,
        String author,
        String publisher,
        String isbn,
        int classLevel,
        String board,
        String boardLabel,
        String subject,
        String edition,
        String language
) {
    public static BookDto from(Book book) {
        return new BookDto(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                book.getIsbn(),
                book.getClassLevel(),
                book.getEducationBoard().name(),
                book.getEducationBoard().label(),
                book.getSubject(),
                book.getEdition(),
                book.getLanguage()
        );
    }
}
