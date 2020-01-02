package com.github.i978sukhoi.spring.activemq.sharer

import org.apache.activemq.command.ActiveMQTextMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.jms.core.JmsTemplate
import org.springframework.jms.listener.DefaultMessageListenerContainer
import java.util.concurrent.ConcurrentHashMap
import javax.jms.ConnectionFactory
import javax.jms.MessageListener

@Suppress("SpringFacetCodeInspection", "SpringJavaInjectionPointsAutowiringInspection")
@Configuration
@EnableConfigurationProperties(SharerProperties::class)
open class SharerConfiguration(
    connectionFactory: ConnectionFactory,
    sharerProperties: SharerProperties
) {
    private val logger = LoggerFactory.getLogger(SharerConfiguration::class.java)
    private val jmsTemplate = JmsTemplate(connectionFactory).also {
        it.defaultDestinationName = sharerProperties.configChannel
        it.isPubSubDomain = true
    }
    private val sharedChannels = ConcurrentHashMap<String, MessageReplicator>()

    private val registerer = RegistererDefault(jmsTemplate, sharerProperties.mappings)

    @Bean
    open fun sharedQueueRegisterer(): SharedQueueRegisterer = registerer

    @EventListener(ApplicationReadyEvent::class)
    open fun onApplicationReadyEvent() {
        startConfigurationChannelListening()
        // do investigate
        logger.info("ActiveMQ Sharer: sending investigation to '{}'", jmsTemplate.defaultDestinationName)
        jmsTemplate.send { it.createTextMessage(ConfigMessageUtil.INV_MSG) }
    }

    private fun startConfigurationChannelListening() = DefaultMessageListenerContainer()
        .apply {
            destinationName = jmsTemplate.defaultDestinationName
            connectionFactory = jmsTemplate.connectionFactory
            isPubSubDomain = true
            messageListener = MessageListener { message ->
                if (message is ActiveMQTextMessage) {
                    val body = message.text
                    logger.info("ActiveMQ Sharer: received {}", body)
                    handleConfigurationChannelMessage(body)
                }
            }
            initialize()
            start()
        }


    private fun handleConfigurationChannelMessage(message: String) {
        if (ConfigMessageUtil.isInvestigate(message)) {
            registerer.responseConfiguredReplications()
        } else {
            val pack = ConfigMessageUtil.parse(message)
            require(pack.second.isNotEmpty())
            require(pack.third.isNotEmpty())
            val replicator = sharedChannels.getOrElse(pack.second) {
                MessageReplicator(jmsTemplate.connectionFactory!!)
                    .also { instance ->
                        logger.info("ActiveMQ Sharer: create new replicator '{}'", pack.second)
                        sharedChannels[pack.second] = instance
                    }
            }
            replicator.handleConfigurationEvent(pack)
        }
    }


    private class RegistererDefault(
        private val jmsTemplate: JmsTemplate,
        mappings: Map<String, String>
    ) : SharedQueueRegisterer {
        private val logger = LoggerFactory.getLogger(RegistererDefault::class.java)
        /**
         * hold my configuration messages
         */
        private val configMessages = mappings.map {
            /**
             * just hold pre-defined sharing. this will send by [responseConfiguredReplications]
             */
            ConfigMessageUtil.toAddMessage(it.value, it.key)
        }.toMutableList()

        override fun register(src: String, dest: String) {
            logger.info("ActiveMQ Sharer: register {} -> {}", src, dest)
            val message = ConfigMessageUtil.toAddMessage(src, dest)
            configMessages.add(message)
            // send to configuration channel
            jmsTemplate.send { session -> session.createTextMessage(message) }
        }

        override fun deregister(src: String, dest: String) {
            logger.info("ActiveMQ Sharer: deregister {} -> {}", src, dest)
            val message = ConfigMessageUtil.toDelMessage(src, dest)
            configMessages.add(message)
            // send to configuration channel
            jmsTemplate.send { session -> session.createTextMessage(message) }
        }

        /**
         * response my sharing
         */
        fun responseConfiguredReplications() = configMessages.forEach { message ->
            logger.info("ActiveMQ Sharer: sending {}", message)
            jmsTemplate.send { session -> session.createTextMessage(message) }
        }
    }
}
