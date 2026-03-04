package com.smart.exam.grading.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);
    public static final String EXAM_EXCHANGE = "exam.exchange";
    public static final String EXAM_SUBMITTED_QUEUE = "exam.submitted.q";
    public static final String EXAM_SUBMITTED_RETRY_QUEUE = "exam.submitted.retry.q";
    public static final String EXAM_SUBMITTED_DLQ_QUEUE = "exam.submitted.dlq.q";
    public static final String EXAM_SUBMITTED_ROUTING_KEY = "exam.submitted";
    public static final String EXAM_SUBMITTED_RETRY_ROUTING_KEY = "exam.submitted.retry";
    public static final String EXAM_SUBMITTED_DLQ_ROUTING_KEY = "exam.submitted.dlq";
    public static final String SCORE_PUBLISHED_ROUTING_KEY = "score.published";

    @Value("${smart-exam.mq.exam-submitted.retry-ttl-ms:10000}")
    private int examSubmittedRetryTtlMs;

    @Bean
    public TopicExchange examExchange() {
        return new TopicExchange(EXAM_EXCHANGE, true, false);
    }

    @Bean
    public Queue examSubmittedQueue() {
        return QueueBuilder.durable(EXAM_SUBMITTED_QUEUE)
                .deadLetterExchange(EXAM_EXCHANGE)
                .deadLetterRoutingKey(EXAM_SUBMITTED_RETRY_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue examSubmittedRetryQueue() {
        return QueueBuilder.durable(EXAM_SUBMITTED_RETRY_QUEUE)
                .ttl(examSubmittedRetryTtlMs)
                .deadLetterExchange(EXAM_EXCHANGE)
                .deadLetterRoutingKey(EXAM_SUBMITTED_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue examSubmittedDlqQueue() {
        return QueueBuilder.durable(EXAM_SUBMITTED_DLQ_QUEUE).build();
    }

    @Bean
    public Binding examSubmittedBinding(
            @Qualifier("examSubmittedQueue") Queue examSubmittedQueue,
            @Qualifier("examExchange") TopicExchange examExchange) {
        return BindingBuilder.bind(examSubmittedQueue).to(examExchange).with(EXAM_SUBMITTED_ROUTING_KEY);
    }

    @Bean
    public Binding examSubmittedRetryBinding(
            @Qualifier("examSubmittedRetryQueue") Queue examSubmittedRetryQueue,
            @Qualifier("examExchange") TopicExchange examExchange) {
        return BindingBuilder.bind(examSubmittedRetryQueue).to(examExchange).with(EXAM_SUBMITTED_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding examSubmittedDlqBinding(
            @Qualifier("examSubmittedDlqQueue") Queue examSubmittedDlqQueue,
            @Qualifier("examExchange") TopicExchange examExchange) {
        return BindingBuilder.bind(examSubmittedDlqQueue).to(examExchange).with(EXAM_SUBMITTED_DLQ_ROUTING_KEY);
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
