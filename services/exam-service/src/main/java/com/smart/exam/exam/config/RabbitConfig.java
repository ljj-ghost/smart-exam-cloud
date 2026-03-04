package com.smart.exam.exam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);
    public static final String EXAM_EXCHANGE = "exam.exchange";
    public static final String EXAM_SUBMITTED_ROUTING_KEY = "exam.submitted";

    @Bean
    public TopicExchange examExchange() {
        return new TopicExchange(EXAM_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public SmartInitializingSingleton rabbitTemplateCallbacks(ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        return () -> {
            RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
            if (rabbitTemplate == null) {
                log.warn("RabbitTemplate unavailable, publisher callbacks not configured");
                return;
            }
            rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
                if (ack) {
                    log.debug("Rabbit publish confirmed, correlationId={}",
                            correlationData == null ? "N/A" : correlationData.getId());
                    return;
                }
                log.error("Rabbit publish nacked, correlationId={}, cause={}",
                        correlationData == null ? "N/A" : correlationData.getId(),
                        cause);
            });
            rabbitTemplate.setReturnsCallback(returned ->
                    log.error("Rabbit publish returned, exchange={}, routingKey={}, replyCode={}, replyText={}",
                            returned.getExchange(),
                            returned.getRoutingKey(),
                            returned.getReplyCode(),
                            returned.getReplyText()));
        };
    }
}
