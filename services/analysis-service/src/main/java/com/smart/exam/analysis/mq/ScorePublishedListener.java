package com.smart.exam.analysis.mq;

import com.smart.exam.analysis.config.RabbitConfig;
import com.smart.exam.analysis.service.ReportDomainService;
import com.smart.exam.common.core.event.ScorePublishedEvent;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class ScorePublishedListener {

    private static final Logger log = LoggerFactory.getLogger(ScorePublishedListener.class);

    private final ReportDomainService reportDomainService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${smart-exam.mq.score-published.max-retries:3}")
    private int maxRetries;

    public ScorePublishedListener(ReportDomainService reportDomainService, RabbitTemplate rabbitTemplate) {
        this.reportDomainService = reportDomainService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitConfig.SCORE_PUBLISHED_QUEUE)
    public void onMessage(ScorePublishedEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            reportDomainService.onScorePublished(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            long retryCount = getRetryCount(message, RabbitConfig.SCORE_PUBLISHED_QUEUE);
            if (retryCount >= maxRetries) {
                log.error("Consume score.published failed after max retries, send to dlq, eventId={}, retryCount={}",
                        event == null ? null : event.getEventId(),
                        retryCount,
                        ex);
                try {
                    rabbitTemplate.convertAndSend(
                            RabbitConfig.EXAM_EXCHANGE,
                            RabbitConfig.SCORE_PUBLISHED_DLQ_ROUTING_KEY,
                            event,
                            new CorrelationData(event == null ? null : event.getEventId())
                    );
                    channel.basicAck(deliveryTag, false);
                } catch (Exception dlqEx) {
                    log.error("Forward score.published message to DLQ failed", dlqEx);
                    channel.basicNack(deliveryTag, false, false);
                }
                return;
            }

            log.warn("Consume score.published failed, will retry, eventId={}, retryCount={}, maxRetries={}",
                    event == null ? null : event.getEventId(),
                    retryCount,
                    maxRetries,
                    ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private long getRetryCount(Message message, String queueName) {
        Object xDeathHeader = message.getMessageProperties().getHeaders().get("x-death");
        if (!(xDeathHeader instanceof List<?> xDeathList)) {
            return 0L;
        }
        for (Object item : xDeathList) {
            if (!(item instanceof Map<?, ?> xDeathEntry)) {
                continue;
            }
            if (!queueName.equals(xDeathEntry.get("queue"))) {
                continue;
            }
            Object count = xDeathEntry.get("count");
            if (count instanceof Number countNumber) {
                return countNumber.longValue();
            }
        }
        return 0L;
    }
}
