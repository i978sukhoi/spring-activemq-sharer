package com.github.i978sukhoi.spring.activemq.sharer

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
//@Configuration
open class SampleAppConfig(
    private val sharedQueueRegisterer: SharedQueueRegisterer
) {

    @EventListener(ApplicationReadyEvent::class)
    fun justRegister() {
        sharedQueueRegisterer.register("source-queue-name", "destination-queue-name")
    }
}
