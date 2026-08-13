package com.banking.frauddetectionservice.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionEventConsumer {

    private final FraudDetectionService fraudDetectionService;

    /**
     * Listening to transaction.initiated event sent from transaction service
     * Every transaction go through fraud check before completing.
     * @param payload
     */
    @KafkaListener(topics = "transaction.initiated",groupId = "fraud-detection-service-group")
    public void consumeTransactionInitiated(
            @Payload Map<String,Object> payload
            ){
       try{
           String transactionId= payload.get("transactionId").toString();
           log.info("Received transaction: {} for fraud check.",transactionId);

           fraudDetectionService.checkTransaction(payload);

       }catch (Exception e){
           log.error("");
       }
    }
}
