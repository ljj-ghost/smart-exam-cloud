package com.smart.exam.analysis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXAM_EXCHANGE = "exam.exchange";
    public static final String SCORE_PUBLISHED_QUEUE = "score.published.q";
    public static final String SCORE_PUBLISHED_RETRY_QUEUE = "score.published.retry.q";
    public static final String SCORE_PUBLISHED_DLQ_QUEUE = "score.published.dlq.q";
    public static final String SCORE_PUBLISHED_ROUTING_KEY = "score.published";
    public static final String SCORE_PUBLISHED_RETRY_ROUTING_KEY = "score.published.retry";
    public static final String SCORE_PUBLISHED_DLQ_ROUTING_KEY = "score.published.dlq";

    @Value("${smart-exam.mq.score-published.retry-ttl-ms:10000}")
    private int scorePublishedRetryTtlMs;

    @Bean
    public TopicExchange examExchange() {
        return new TopicExchange(EXAM_EXCHANGE, true, false);
    }

    @Bean
    public Queue scorePublishedQueue() {
        return QueueBuilder.durable(SCORE_PUBLISHED_QUEUE)
                .deadLetterExchange(EXAM_EXCHANGE)
                .deadLetterRoutingKey(SCORE_PUBLISHED_RETRY_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue scorePublishedRetryQueue() {
        return QueueBuilder.durable(SCORE_PUBLISHED_RETRY_QUEUE)
                .ttl(scorePublishedRetryTtlMs)
                .deadLetterExchange(EXAM_EXCHANGE)
                .deadLetterRoutingKey(SCORE_PUBLISHED_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue scorePublishedDlqQueue() {
        return QueueBuilder.durable(SCORE_PUBLISHED_DLQ_QUEUE).build();
    }

    @Bean
    public Binding scorePublishedBinding(
            @Qualifier("scorePublishedQueue") Queue scorePublishedQueue,
            @Qualifier("examExchange") TopicExchange examExchange) {
        return BindingBuilder.bind(scorePublishedQueue).to(examExchange).with(SCORE_PUBLISHED_ROUTING_KEY);
    }

    @Bean
    public Binding scorePublishedRetryBinding(
            @Qualifier("scorePublishedRetryQueue") Queue scorePublishedRetryQueue,
            @Qualifier("examExchange") TopicExchange examExchange) {
        return BindingBuilder.bind(scorePublishedRetryQueue).to(examExchange).with(SCORE_PUBLISHED_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding scorePublishedDlqBinding(
            @Qualifier("scorePublishedDlqQueue") Queue scorePublishedDlqQueue,
            @Qualifier("examExchange") TopicExchange examExchange) {
        return BindingBuilder.bind(scorePublishedDlqQueue).to(examExchange).with(SCORE_PUBLISHED_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
