package com.strangequark.odoc.jobs;

import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Same artifact in API/worker profiles; correctness remains entirely in the database state machine. */
@Component
class DurableJobWorker {
    private final DurableJobService jobs;
    private final List<DurableJobHandler> handlers;
    private final String workerId = "odoc-" + UUID.randomUUID();

    DurableJobWorker(DurableJobService jobs, List<DurableJobHandler> handlers) {
        this.jobs = jobs;
        this.handlers = handlers;
    }

    @Scheduled(fixedDelayString = "${odoc.jobs.poll-interval:PT2S}")
    void poll() {
        for (DurableJobService.ClaimedJob job : jobs.claim(workerId, 8)) {
            try {
                DurableJobHandler handler = handlers.stream().filter(candidate -> candidate.supports(job.type()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("No handler for job type."));
                handler.handle(job);
                jobs.complete(job);
            } catch (Exception exception) {
                jobs.fail(job, exception);
            }
        }
    }
}
