package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionRequestDto;
import com.banking.transactionservice.dto.TransactionResponseDto;
import com.banking.transactionservice.repository.TransactionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;

    private final AccountServiceClient accountServiceClient;

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

        return null;
    }

    public TransactionResponseDto getTransaction(String transactionId) {
        return null;
    }

    public List<TransactionResponseDto> getTransactionHistory(String accountNumber) {
        return null;
    }

    public TransactionResponseDto verifyOtp(String transactionId, String otp) {
        return null;
    }
}
