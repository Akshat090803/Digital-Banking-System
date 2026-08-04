package com.banking.transactionservice.util;

import com.banking.transactionservice.dto.TransactionResponseDto;
import com.banking.transactionservice.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionUtil {

    public TransactionResponseDto mapToTransactionResponseDto(
        Transaction transaction
    ){
        return TransactionResponseDto.builder()
                .id(transaction.getId())
                .senderAccountNumber(transaction.getSenderAccountNumber())
                .receiverAccountNumber(transaction.getReceiverAccountNumber())
                .amount(transaction.getAmount())
                .transactionType(transaction.getTransactionType())
                .transactionStatus(transaction.getTransactionStatus())
                .description(transaction.getDescription())
                .failureReason(transaction.getFailureReason())
                .referenceNumber(transaction.getReferenceNumber())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }
}
