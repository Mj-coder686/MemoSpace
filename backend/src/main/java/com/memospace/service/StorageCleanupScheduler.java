package com.memospace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StorageCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupScheduler.class);
    private final FileStorageService files;
    private final int retentionHours;

    public StorageCleanupScheduler(FileStorageService files,
                                   @Value("${app.storage.orphan-retention-hours:24}") int retentionHours) {
        this.files = files;
        this.retentionHours = Math.max(1, retentionHours);
    }

    @Scheduled(cron = "${app.storage.orphan-cleanup-cron:0 45 3 * * *}", zone = "Asia/Shanghai")
    public void cleanOrphans() {
        int removed = files.cleanupUnreferencedUploads(retentionHours, 200);
        if (removed > 0) log.info("Cleaned {} unreferenced uploaded files", removed);
    }
}
