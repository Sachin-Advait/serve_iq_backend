package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.models.SmsTemplateCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsTemplateServiceTest {

    @Test
    void fillsPlaceholders() {
        String out = SmsTemplateService.fill("Token {token} for {Service}, {queue} waiting",
                Map.of("token", "A001", "service", "Pension", "queue", "5"));
        assertThat(out).isEqualTo("Token A001 for Pension, 5 waiting");
    }

    @Test
    void defaultReceiptMatchesOldText() {
        String out = SmsTemplateService.fill(SmsTemplateService.DEFAULT_RECEIPT_EN, Map.of(
                "token", "A050", "service", "Pension Inquiry", "queue", "5",
                "date", "19/10/2026", "time", "10:30"));
        assertThat(out).startsWith("MSSPF\nVisitor Management\n\nQueue Token: A050\nService: Pension Inquiry")
                .endsWith("Please wait for your number to be called.\nThank you.");
    }

    @Test
    void rejectsUnknownPlaceholder() {
        assertThatThrownBy(() -> SmsTemplateService.validBody("Hi {name}",
                SmsTemplateCode.TOKEN_RECEIPT.placeholders(), "English"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("{name}");
    }

    @Test
    void rejectsEmptyBody() {
        assertThatThrownBy(() -> SmsTemplateService.validBody("  ",
                SmsTemplateCode.TOKEN_RECEIPT.placeholders(), "Arabic"))
                .isInstanceOf(BusinessException.class);
    }
}
