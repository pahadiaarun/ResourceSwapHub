package com.bookbridge.catalog;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.listing.BookCondition;
import com.bookbridge.listing.ListingRepository;
import com.bookbridge.listing.PriceType;

@Service
public class TaxonomyService {
    private final BookRepository bookRepository;
    private final ListingRepository listingRepository;

    public TaxonomyService(BookRepository bookRepository, ListingRepository listingRepository) {
        this.bookRepository = bookRepository;
        this.listingRepository = listingRepository;
    }

    @Transactional(readOnly = true)
    public TaxonomyDto get() {
        List<TaxonomyDto.Option> boards = List.of(EducationBoard.values()).stream()
                .map(board -> new TaxonomyDto.Option(board.name(), board.label()))
                .toList();
        List<TaxonomyDto.Option> conditions = List.of(BookCondition.values()).stream()
                .map(condition -> new TaxonomyDto.Option(condition.name(), condition.label()))
                .toList();
        List<TaxonomyDto.Option> priceTypes = List.of(PriceType.values()).stream()
                .map(type -> new TaxonomyDto.Option(type.name(), type.label()))
                .toList();
        return new TaxonomyDto(
                boards,
                conditions,
                priceTypes,
                IntStream.rangeClosed(1, 12).boxed().toList(),
                bookRepository.findDistinctSubjects(),
                bookRepository.findDistinctLanguages(),
                listingRepository.findDistinctAvailableCities()
        );
    }
}
