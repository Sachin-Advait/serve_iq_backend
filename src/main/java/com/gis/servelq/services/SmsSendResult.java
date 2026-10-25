package com.gis.servelq.services;

/** Outcome of one send; statusCode/statusDesc are SMSOMAN's when it answered. */
public record SmsSendResult(boolean success, String batchRefCode, String statusCode, String statusDesc) {
    static SmsSendResult failed(String reason) {
        return new SmsSendResult(false, null, null, reason);
    }
}
