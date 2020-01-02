# spring-jms-sharer
[JMS](https://en.wikipedia.org/wiki/Java_Message_Service) provide many features but shared-queue was missing.
In [MSA](https://en.wikipedia.org/wiki/Microservices), shared-queue is very useful for broadcast messages to services.

* application have specific events that should shared with other services.
* service may have multiple instances. that means, there are many consumers which role is same. aka consumer group.
* if broadcast to all consumers, same logic will run multiple times.
* so we need grouping consumers and broadcast message to groups.
* only one consumer will receive message in each groups.

[Apache Kafka](https://kafka.apache.org/) already has that feature but too heavy.
[Active MQ](https://activemq.apache.org) is very reasonable choice for message broker.

So this library will
* Subscribe a topic(configuration message channel)
* Exchange shared-queue information to each other.  
    (on [ApplicationReadyEvent](https://docs.spring.io/spring-boot/docs/2.2.2.RELEASE/reference/html/spring-boot-features.html#boot-features-application-events-and-listeners) and when investigation message received)
* Hold shared-queue mappings. (Map&lt;`source-queue`, List&lt;`destination-queue`&gt;&gt;)
* Subscribe source queues.
* Replicate messages to destination queues when, received it from source queue.

# configurable properties
| property name | default | description |
| ---- | ---- | ---- |
| spring.activemq.sharer.config-channel | shared-queue-config | `topic` that used to exchange shared queue configuration message |
| spring.activemq.sharer.mappings |  | Map&lt;String,String&gt; <br> - key: destination-queue <br> - value: source-queue |
## sample
```properties
spring.activemq.sharer.config-channel=shared-queue-config
# all `user-event` message will replicated to `user-event-logging` and `user-event-notifier` 
spring.activemq.sharer.mappings.user-event-logging=user-event
spring.activemq.sharer.mappings.user-event-notifier=user-event
```

# programmatic use
```kotlin
@Configuration
open class SampleAppConfig(
    private val sharedQueueRegisterer: SharedQueueRegisterer
) {

    @EventListener(ApplicationReadyEvent::class)
    fun justRegister() {
        sharedQueueRegisterer.register("source-queue-name", "destination-queue-name")
    }
}
```
