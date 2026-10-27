package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedback")
@Data
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String tokenId;
    private String counterCode;
    // Agent logged in at the counter when the rating arrived; set by the server, not the client.
    private String agentId;
    private String agentName;

    @Enumerated(EnumType.STRING)
    private MoodRating rating;
    private String review;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // Filled from the ticket when feedback is listed; not stored.
    @Transient
    private String tokenNumber;
    @Transient
    private String serviceName;
    /** Seconds from ticket issue to first call, null if never called. */
    @Transient
    private Long waitSeconds;

    public enum MoodRating {
        SAD,
        NEUTRAL,
        HAPPY
    }
}
