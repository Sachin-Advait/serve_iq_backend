package com.gis.servelq.events;

public enum TokenEventType {
    // Token lifecycle
    TOKEN_CREATED,
    TOKEN_CALLED,
    TOKEN_SERVING_STARTED,
    TOKEN_COMPLETED,
    TOKEN_TRANSFERRED,
    TOKEN_HELD,
    TOKEN_NO_SHOW,

    // UI intent events
    AGENT_QUEUE_CHANGED,
    COUNTER_STATUS_CHANGED,
    COUNTER_DISPLAY_IMAGE_CHANGED,
    TV_MEDIA_CHANGED,

    // Feedback
    FEEDBACK_SUBMITTED,
}


