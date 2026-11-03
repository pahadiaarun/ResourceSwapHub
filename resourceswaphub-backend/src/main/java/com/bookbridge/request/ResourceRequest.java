package com.bookbridge.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.bookbridge.category.ResourceCategory;
import com.bookbridge.user.User;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resource_requests")
@Getter
@Setter
@NoArgsConstructor
public class ResourceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ResourceCategory category;

    @Column(length = 120)
    private String subject;

    @Column(length = 255)
    private String author;

    @Column(length = 80)
    private String edition;

    @Column(name = "academic_level", length = 80)
    private String academicLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_condition", length = 20)
    private com.bookbridge.listing.BookCondition preferredCondition;

    @Column(name = "price_min", precision = 10, scale = 2)
    private BigDecimal priceMin;

    @Column(name = "price_max", precision = 10, scale = 2)
    private BigDecimal priceMax;

    @Column(name = "free_only", nullable = false)
    private boolean freeOnly;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "state_id")
    private UUID stateId;

    @Column(name = "city_id")
    private UUID cityId;

    @Column(name = "city_name", nullable = false, length = 80)
    private String cityName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.OPEN;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isOpenForResponses(Instant now) {
        return status == RequestStatus.OPEN && (expiresAt == null || expiresAt.isAfter(now));
    }
}
