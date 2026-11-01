package com.bookbridge.catalog;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.error.ApiException;

@Service
public class BookService {
    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public List<BookDto> search(String query, int limit) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(limit, 20));
        return bookRepository.search(cleaned, PageRequest.of(0, capped)).stream()
                .map(BookDto::from)
                .toList();
    }

    @Transactional
    public BookDto findOrCreate(BookCreateRequest request) {
        String title = request.title().trim();
        String author = request.author().trim();
        String subject = request.subject().trim();
        String publisher = trimToNull(request.publisher());
        String edition = trimToNull(request.edition());
        String language = request.language() == null || request.language().isBlank()
                ? "English" : request.language().trim();
        String isbn = normalizeAndValidateIsbn(request.isbn());
        EducationBoard board = parseBoard(request.board());

        Book existing = null;
        if (isbn != null) {
            existing = bookRepository.findByIsbn(isbn).orElse(null);
        }
        if (existing == null) {
            String dedupeKey = dedupeKey(title, author, publisher, request.classLevel(), board,
                    subject, edition, language);
            existing = bookRepository.findByDedupeKey(dedupeKey).orElse(null);
        }
        if (existing != null) {
            return BookDto.from(existing);
        }

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setPublisher(publisher);
        book.setIsbn(isbn);
        book.setClassLevel((short) request.classLevel());
        book.setEducationBoard(board);
        book.setSubject(subject);
        book.setEdition(edition);
        book.setLanguage(language);
        bookRepository.saveAndFlush(book);
        return BookDto.from(book);
    }

    public static String normalizeIsbn(String isbn) {
        if (isbn == null) {
            return null;
        }
        String normalized = isbn.replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeAndValidateIsbn(String isbn) {
        String normalized = normalizeIsbn(isbn);
        if (normalized == null) {
            return null;
        }
        boolean valid = switch (normalized.length()) {
            case 10 -> validIsbn10(normalized);
            case 13 -> validIsbn13(normalized);
            default -> false;
        };
        if (!valid) {
            throw ApiException.badRequest("INVALID_ISBN", "Enter a valid ISBN-10 or ISBN-13, or leave it blank.");
        }
        return normalized;
    }

    private boolean validIsbn10(String isbn) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = isbn.charAt(i);
            int value;
            if (i == 9 && c == 'X') {
                value = 10;
            } else if (Character.isDigit(c)) {
                value = c - '0';
            } else {
                return false;
            }
            sum += value * (10 - i);
        }
        return sum % 11 == 0;
    }

    private boolean validIsbn13(String isbn) {
        if (!isbn.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = IntStream.range(0, 12)
                .map(i -> Character.digit(isbn.charAt(i), 10) * (i % 2 == 0 ? 1 : 3))
                .sum();
        int check = (10 - (sum % 10)) % 10;
        return check == Character.digit(isbn.charAt(12), 10);
    }

    public static String dedupeKey(String title, String author, String publisher, int classLevel,
                                   EducationBoard board, String subject, String edition, String language) {
        return normalizePart(title) + "|"
                + normalizePart(author) + "|"
                + normalizePart(publisher) + "|"
                + classLevel + "|"
                + board.name() + "|"
                + normalizePart(subject) + "|"
                + normalizePart(edition) + "|"
                + normalizePart(language);
    }

    private static String normalizePart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private EducationBoard parseBoard(String value) {
        try {
            return EducationBoard.valueOf(value.trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_BOARD", "Choose a supported education board.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
