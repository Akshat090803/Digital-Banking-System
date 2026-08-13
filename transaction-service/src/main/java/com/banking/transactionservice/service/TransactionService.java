package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionRequestDto;
import com.banking.transactionservice.dto.TransactionResponseDto;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import com.banking.transactionservice.type.TransactionStatus;
import com.banking.transactionservice.type.TransactionType;
import com.banking.transactionservice.util.TransactionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;

    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String,Object> kafkaTemplate; //used for publishing event

    private  final TransactionUtil transactionUtil;

    private final RedisTemplate<String,String> redisTemplate;

    ///  kafka topics
    private static final String TRANSACTION_INITIATED_TOPIC="transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC="transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC="transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC="fraud.detected";


    /**
     * SAGA Step 1 - Initiate Transfer
     * Deduct from sender's account via feign
     * Save transaction as processing
     * Publish event in kafka for fraud check
     * Returns
     * @param request
     * @return
     */
    public TransactionResponseDto transfer(TransactionRequestDto request) {

        log.info("SAGA START - Transfer from account: {} to {} amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());


        //Step 1 - deduct from sender's account
        accountServiceClient.debitAmount(
                request.getSenderAccountNumber(),
                request.getAmount());

        Transaction transaction = Transaction.builder()
                .senderAccountNumber(request.getSenderAccountNumber())
                .receiverAccountNumber(request.getReceiverAccountNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .transactionStatus(TransactionStatus.PROCESSING)
                .transactionType(TransactionType.TRANSFER)
                .referenceNumber(UUID.randomUUID().toString())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as Processing: {}",savedTransaction.getId());

        ///  Saga Step 2 - Publish for  Fraud check
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savedTransaction.getId(),event);
        log.info("SAGA Step 2 - Transaction Initiated Event published: {}",savedTransaction.getId());


        return transactionUtil.mapToTransactionResponseDto(savedTransaction);
    }

    public TransactionResponseDto getTransaction(String transactionId) {
        return transactionUtil.mapToTransactionResponseDto(
                transactionRepository.findById(transactionId)
                        .orElseThrow(()->new RuntimeException(
                                "Transaction not found: "+transactionId
                        ))
        );
    }

    public List<TransactionResponseDto> getTransactionHistory(String accountNumber) {
        /// this will only give outgoing transaction history (debit) as we are looking in senderAccountNumber only
//        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
//                .stream()
//                .map(transactionUtil::mapToTransactionResponseDto)
//                .toList();

        /// Fixed: Queries both incoming and outgoing transfers for a true bank statement history
        return transactionRepository.findBySenderAccountNumberOrReceiverAccountNumberOrderByCreatedAtDesc(accountNumber, accountNumber)
                .stream()
                .map(transactionUtil::mapToTransactionResponseDto)
                .toList();
    }



    @Transactional
    public TransactionResponseDto verifyOtp(String transactionId, String otp) {
        log.info("OTP verification for transaction: {}",transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(()->new RuntimeException("Transaction not found: "+transactionId));
        String otpKey ="verification:otp"+transactionId;
        String storedOtp=  redisTemplate.opsForValue().get(otpKey);

        //otp expired
        if(storedOtp == null){
            log.warn("OTP expired for transaction: {}",transactionId);
            Transaction expiredTx = compensateTransaction(transaction,"OTP expired - transaction cancelled and amount refunded.");
            return transactionUtil.mapToTransactionResponseDto(expiredTx);
        }


        if(!storedOtp.equals(otp)){
            log.warn("Wrong OTP - blocking account and refunding: {}",transactionId);
            Transaction fraudulentTx =blockAndCompensateTransaction(transaction,
                    "Wrong OTP entered - transaction cancelled,"+
                    "account blocked for security.");
            return transactionUtil.mapToTransactionResponseDto(fraudulentTx);
        }

        //otp correct - complete the transaction
        log.info("OTP verified - completing the transaction: {}",transactionId);
        redisTemplate.delete(otpKey);
        Transaction completedTx = completeTransaction(transaction);
        return transactionUtil.mapToTransactionResponseDto(transaction);

    }



    private Transaction completeTransaction(Transaction transaction) {
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        Transaction savedTx = transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC,transaction.getId(),completedEvent);
        log.info("SAGA Completed - Transaction: {} completed",transaction.getId());
        return savedTx;
    }


    private Transaction compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),transaction.getAmount());

        //credit money back to sender's account
        accountServiceClient.creditAmount(transaction.getSenderAccountNumber(),
                transaction.getAmount());

        transaction.setTransactionStatus(TransactionStatus.FLAGGED);

        transaction.setFailureReason(reason +
                " - SAGA Compensation executed, amount refunded at "+ LocalDateTime.now());

        Transaction savedTx = transactionRepository.save(transaction);

        //Publish refund event - notification service will inform user.

        Map<String,Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId",transaction.getId());
        refundEvent.put("amount",transaction.getAmount());
        refundEvent.put("reason",reason);
        refundEvent.put("senderAccountNumber",transaction.getSenderAccountNumber());

        //publish event
        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId(),refundEvent);
        log.info("SAGA compensation completed - {} refunded to: {}",
                transaction.getAmount(),transaction.getSenderAccountNumber());

        return savedTx;
    }

    private Transaction blockAndCompensateTransaction(Transaction transaction, String reason) {
        //Execute SAGA compensation first while the account is still ACTIVE
        Transaction updatedTransaction = compensateTransaction(transaction, reason);

        //Publish fraud.detected event AFTER the money has been safely returned
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("reason", reason);
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getId(), fraudEvent);
        log.warn("fraud.detected event published - account: {} will be blocked. Kindly contact your bank.", transaction.getSenderAccountNumber());

        return updatedTransaction;
    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(()->new RuntimeException("Transaction not found: "+transactionId));

        if(transaction.getTransactionStatus() != TransactionStatus.PROCESSING){
            log.info("Transaction: {} not in Processing state - skipping",transactionId);
            return;
        }

        completeTransaction(transaction);
    }
}
