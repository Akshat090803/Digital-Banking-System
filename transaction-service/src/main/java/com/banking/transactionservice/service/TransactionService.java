package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionRequestDto;
import com.banking.transactionservice.dto.TransactionResponseDto;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import com.banking.transactionservice.type.TransactionStatus;
import com.banking.transactionservice.type.TransactionType;
import com.banking.transactionservice.util.TransactionUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;

    private final AccountServiceClient accountServiceClient;

    private final KafkaTemplate<String,Object> kafkaTemplate; //used for publishing event

    private  final TransactionUtil transactionUtil;

    ///  kafka topics
    private static final String TRANSACTION_INITIATED_TOPIC="transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC="transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC="transaction.refunded";


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



    public TransactionResponseDto verifyOtp(String transactionId, String otp) {
        return null;
    }
}
