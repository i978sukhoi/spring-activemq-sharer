package com.github.i978sukhoi.spring.activemq.sharer

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.activemq.sharer")
open class SharerProperties {
    val configChannel = "shared-queue-config"
    val mappings = mutableMapOf<String, String>()
}
