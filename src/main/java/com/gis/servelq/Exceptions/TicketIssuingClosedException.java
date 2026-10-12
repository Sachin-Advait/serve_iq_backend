package com.gis.servelq.Exceptions;

/**
 * Ticket issuing is stopped or outside its hours for the branch. Answered with
 * 400 so the kiosk's existing bad-request handling shows the message.
 */
public class TicketIssuingClosedException extends BusinessException {
    public TicketIssuingClosedException(String message) {
        super(message);
    }
}
