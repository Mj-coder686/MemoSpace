package com.memospace.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MemoryTrashScheduler {
    private final MemoryService memories;
    private final int retentionDays;

    public MemoryTrashScheduler(MemoryService memories,
                                @Value("${app.memory.trash-retention-days:30}") int retentionDays) {
        this.memories = memories;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.memory.trash-purge-cron:0 20 3 * * *}", zone = "Asia/Shanghai")
    public void purgeExpired() {
        if (retentionDays <= 0) return;
        memories.purgeDeletedBefore(LocalDateTime.now().minusDays(retentionDays));
    }
}
