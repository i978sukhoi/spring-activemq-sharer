package com.github.i978sukhoi.spring.activemq.sharer

import org.apache.activemq.command.ActiveMQTextMessage
import org.slf4j.LoggerFactory
import org.springframework.jms.core.JmsTemplate
import org.springframework.jms.listener.DefaultMessageListenerContainer
import javax.jms.ConnectionFactory
import javax.jms.MessageListener

/**
 * - [subscribe] source queue. when receive message from source queue, replicate to [destinations]
 * - if receive configuration message, add(or remove) to [destinations]
 */
class MessageReplicator(
    connectionFactory: ConnectionFactory
) {
    private val logger = LoggerFactory.getLogger(MessageReplicator::class.java)
    private val jmsTemplate = JmsTemplate(connectionFactory)
    /**
     * hold shared queue destinations.
     */
    private val destinations = mutableSetOf<String>()
    private var messageListenerContainer: DefaultMessageListenerContainer? = null

    /**
     * configuration message handler
     */
    fun handleConfigurationEvent(message: Triple<Char, String, String>) {
        when (message.first) { // indicator
            ConfigMessageUtil.ADD -> { // if register
                if (destinations.isEmpty()) // if first
                    subscribe(message.second) // subscribe source queue
                destinations.add(message.third) // add to destinations
            }
            ConfigMessageUtil.DEL -> { // if de-register
                destinations.remove(message.third)  // remove from destinations
                if (destinations.isEmpty()) // if no more destination
                    unsubscribe() // unsubscribe
            }
        }
    }

    /**
     * subscribe source queue
     */
    private fun subscribe(queueName: String) {
        if (messageListenerContainer != null) return    // skip if already listening
        messageListenerContainer = DefaultMessageListenerContainer().apply {
            logger.info("ActiveMQ Sharer: subscribe '{}'", queueName)
            destinationName = queueName
            connectionFactory = jmsTemplate.connectionFactory
            isPubSubDomain = false
            messageListener = MessageListener { message ->
                if (message is ActiveMQTextMessage) {
                    val body = message.text
                    // do replicate message to destinations
                    for (dest in destinations) jmsTemplate.send(dest) { session ->
                        session.createTextMessage(body)
                    }
                }
            }
            initialize()
            start()
        }
    }

    /**
     * unsubscribe source queue
     */
    private fun unsubscribe() {
        messageListenerContainer?.apply {
            logger.info("ActiveMQ Sharer: unsubscribe '{}'", destinationName)
            stop()
            shutdown()
        }
        messageListenerContainer = null
    }
}
