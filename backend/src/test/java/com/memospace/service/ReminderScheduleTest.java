package com.memospace.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReminderScheduleTest {
    @Test
    void monthlyScheduleKeepsItsOriginalDayWhenShortMonthsIntervene() {
        LocalDateTime anchor = LocalDateTime.of(2028, 1, 31, 9, 30);

        LocalDateTime next = ReminderService.nextTrigger("MONTHLY", anchor,
                LocalDateTime.of(2028, 2, 29, 9, 30), "Asia/Shanghai");

        assertEquals(LocalDateTime.of(2028, 3, 31, 9, 30), next);
    }

    @Test
    void yearlyScheduleReturnsToLeapDayInTheNextLeapYear() {
        LocalDateTime anchor = LocalDateTime.of(2024, 2, 29, 8, 0);

        LocalDateTime next = ReminderService.nextTrigger("YEARLY", anchor,
                LocalDateTime.of(2027, 2, 28, 8, 0), "Asia/Shanghai");

        assertEquals(LocalDateTime.of(2028, 2, 29, 8, 0), next);
    }
}
