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
## properties sample
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
    fun registerSharedQueue() {
        sharedQueueRegisterer.register("source-queue-name", "destination-queue-name")
    }
}
```

# working example
```kotlin
@SpringBootApplication
@EnableJms
@RestController
class DemoApplication {

    @Autowired
    lateinit var sharedQueueRegisterer: SharedQueueRegisterer

    @EventListener(ApplicationReadyEvent::class)
    fun registerSharedQueue() {
        // register shared queues
        sharedQueueRegisterer.register("sq-source", "sq-target-1")
        sharedQueueRegisterer.register("sq-source", "sq-target-2")
    }

    @Bean // Serialize message content to json using TextMessage
    fun jacksonJmsMessageConverter(): MessageConverter =
            MappingJackson2MessageConverter().apply {
                setTargetType(MessageType.TEXT)
                setTypeIdPropertyName("_type")
            }

    // sample DTO
    class Sample {
        var name = ""
        var now = Date()
        override fun toString() = "Sample($name, $now)"
    }

    // listening sq-target-1
    @JmsListener(destination = "sq-target-1")
    fun target1Listener(@Payload payload: Sample) {
        println("target1Listener: $payload")
    }

    // listening sq-target-2
    @JmsListener(destination = "sq-target-2")
    fun target2Listener(@Payload payload: Sample) {
        println("target2Listener: $payload")
    }

    @Autowired
    lateinit var jmsTemplate: JmsTemplate

    @RequestMapping("/dummy")
    @ResponseBody
    fun dummy(): Any? {
        val sample = Sample()
        sample.name = "Hello SQ"
        sample.now = Date()
        // send message to source-queue
        jmsTemplate.convertAndSend("sq-source", sample)
        return sample
    }
}
```
output
```text
...
2020-01-02 16:58:43.679  INFO 9197 --- [           main] o.s.s.concurrent.ThreadPoolTaskExecutor  : Initializing ExecutorService 'applicationTaskExecutor'
2020-01-02 16:58:44.273  INFO 9197 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8080 (http) with context path ''
2020-01-02 16:58:44.277  INFO 9197 --- [           main] com.example.demo.DemoApplicationKt       : Started DemoApplicationKt in 3.251 seconds (JVM running for 3.86)
2020-01-02 16:58:44.278  INFO 9197 --- [           main] .s.SharerConfiguration$RegistererDefault : ActiveMQ Sharer: register sq-source -> sq-target-1
2020-01-02 16:58:44.308  INFO 9197 --- [           main] .s.SharerConfiguration$RegistererDefault : ActiveMQ Sharer: register sq-source -> sq-target-2
2020-01-02 16:58:44.314  INFO 9197 --- [           main] c.g.i.s.a.sharer.SharerConfiguration     : ActiveMQ Sharer: sending investigation to 'shared-queue-config'
2020-01-02 16:58:44.329  INFO 9197 --- [enerContainer-1] c.g.i.s.a.sharer.SharerConfiguration     : ActiveMQ Sharer: received ?;;
2020-01-02 16:58:44.329  INFO 9197 --- [enerContainer-1] .s.SharerConfiguration$RegistererDefault : ActiveMQ Sharer: sending +;sq-source;sq-target-1
2020-01-02 16:58:44.336  INFO 9197 --- [enerContainer-1] .s.SharerConfiguration$RegistererDefault : ActiveMQ Sharer: sending +;sq-source;sq-target-2
2020-01-02 16:58:44.342  INFO 9197 --- [enerContainer-1] c.g.i.s.a.sharer.SharerConfiguration     : ActiveMQ Sharer: received +;sq-source;sq-target-1
2020-01-02 16:58:44.342  INFO 9197 --- [enerContainer-1] c.g.i.s.a.sharer.SharerConfiguration     : ActiveMQ Sharer: create new replicator 'sq-source'
2020-01-02 16:58:44.342  INFO 9197 --- [enerContainer-1] c.g.i.s.a.sharer.MessageReplicator       : ActiveMQ Sharer: subscribe 'sq-source'
2020-01-02 16:58:44.348  INFO 9197 --- [enerContainer-1] c.g.i.s.a.sharer.SharerConfiguration     : ActiveMQ Sharer: received +;sq-source;sq-target-2
2020-01-02 16:58:49.365  INFO 9197 --- [nio-8080-exec-3] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring DispatcherServlet 'dispatcherServlet'
2020-01-02 16:58:49.365  INFO 9197 --- [nio-8080-exec-3] o.s.web.servlet.DispatcherServlet        : Initializing Servlet 'dispatcherServlet'
2020-01-02 16:58:49.370  INFO 9197 --- [nio-8080-exec-3] o.s.web.servlet.DispatcherServlet        : Completed initialization in 5 ms
target2Listener: Sample(Hello SQ, Thu Jan 02 16:58:49 KST 2020)
target1Listener: Sample(Hello SQ, Thu Jan 02 16:58:49 KST 2020)
```
