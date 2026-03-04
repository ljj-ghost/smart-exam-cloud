package com.smart.exam.grading.mq;

import com.smart.exam.common.core.event.ExamSubmittedEvent;
import com.smart.exam.grading.config.RabbitConfig;
import com.smart.exam.grading.service.GradingDomainService;
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
public class ExamSubmittedListener {

    private static final Logger log = LoggerFactory.getLogger(ExamSubmittedListener.class);

    private final GradingDomainService gradingDomainService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${smart-exam.mq.exam-submitted.max-retries:3}")
    private int maxRetries;

    public ExamSubmittedListener(GradingDomainService gradingDomainService, RabbitTemplate rabbitTemplate) {
        this.gradingDomainService = gradingDomainService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitConfig.EXAM_SUBMITTED_QUEUE)
    public void listen(ExamSubmittedEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            gradingDomainService.onExamSubmitted(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            long retryCount = getRetryCount(message, RabbitConfig.EXAM_SUBMITTED_QUEUE);
            if (retryCount >= maxRetries) {
                log.error("Consume exam.submitted failed after max retries, send to dlq, eventId={}, retryCount={}",
                        event == null ? null : event.getEventId(),
                        retryCount,
                        ex);
                try {
                    rabbitTemplate.convertAndSend(
                            RabbitConfig.EXAM_EXCHANGE,
                            RabbitConfig.EXAM_SUBMITTED_DLQ_ROUTING_KEY,
                            event,
                            new CorrelationData(event == null ? null : event.getEventId())
                    );
                    channel.basicAck(deliveryTag, false);
                } catch (Exception dlqEx) {
                    log.error("Forward exam.submitted message to DLQ failed", dlqEx);
                    channel.basicNack(deliveryTag, false, false);
                }
                return;
            }

            log.warn("Consume exam.submitted failed, will retry, eventId={}, retryCount={}, maxRetries={}",
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
