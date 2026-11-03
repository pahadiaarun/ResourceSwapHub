package com.bookbridge.listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.bookbridge.catalog.Book;
import com.bookbridge.category.ResourceCategory;
import com.bookbridge.shared.media.MediaAsset;
import com.bookbridge.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ResourceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BookCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 20)
    private PriceType priceType;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(length = 120)
    private String locality;

    // Standardized location; `city` above mirrors cityId's canonical name for pre-directory clients.
    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "state_id")
    private UUID stateId;

    @Column(name = "city_id")
    private UUID cityId;

    @Column(length = 2000)
    private String notes;

    @Column
    private String title;

    @Column(length = 120)
    private String subject;

    @Column(name = "academic_level", length = 80)
    private String academicLevel;

    @Column(length = 20)
    private String board;

    @Column(length = 255)
    private String author;

    @Column(length = 255)
    private String publisher;

    @Column(length = 80)
    private String edition;

    @Column(name = "publication_year")
    private Short publicationYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ListingMedium medium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "digital_media_id")
    private MediaAsset digitalMedia;

    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Column(name = "file_format", length = 20)
    private String fileFormat;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "owner_rights_confirmed", nullable = false)
    private boolean ownerRightsConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_state", nullable = false, length = 20)
    private ModerationState moderationState = ModerationState.NONE;

    @Column(name = "client_ref", length = 64)
    private String clientRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status = ListingStatus.AVAILABLE;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ListingImage> images = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addImage(ListingImage image) {
        image.setListing(this);
        images.add(image);
    }
}
