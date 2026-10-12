package com.gis.servelq.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "branches")
@Data
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(unique = true)
    private String code;

    @NotBlank
    private String name;

    @NotNull
    private Boolean enabled = true;

    // Ticket issuing (queue procedure section 10). When disabled, or outside
    // the start/end window, the kiosk can't issue new tickets.
    @NotNull
    @Column(name = "ticket_issuing_enabled")
    private Boolean ticketIssuingEnabled = true;

    @Column(name = "ticket_issuing_start")
    private LocalTime ticketIssuingStart;

    @Column(name = "ticket_issuing_end")
    private LocalTime ticketIssuingEnd;

    @Column(name = "ticket_stop_message_en", length = 500)
    private String ticketStopMessageEn;

    @Column(name = "ticket_stop_message_ar", length = 500)
    private String ticketStopMessageAr;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
