package com.banking.transactionservice.controller;


import com.banking.transactionservice.dto.ApiResponse;
import com.banking.transactionservice.dto.TransactionRequestDto;
import com.banking.transactionservice.dto.TransactionResponseDto;
import com.banking.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@Slf4j
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponseDto>>transfer(
            @Valid @RequestBody TransactionRequestDto request
            ){
        log.info("Processing transfer request from account: {}", request.getSenderAccountNumber());
        TransactionResponseDto transaction = transactionService.transfer(request);

        /// To fix this type mismatch error, explicitly provide the generic type parameter to the builder method
        ///by changing ApiResponse.builder() to ApiResponse.<TransactionResponseDto>builder().
        ///This tells Java's type inference mechanism exactly what type the .data() payload contains.
        ApiResponse<TransactionResponseDto> response = ApiResponse.<TransactionResponseDto>builder()
                .message("Transfer completed successfully")
                .status(HttpStatus.CREATED.value())
                .data(transaction)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response) ;
    }


    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponseDto>> getTransaction(
            @PathVariable String transactionId
    ){
        log.info("Fetching transaction details for : {}", transactionId);

        TransactionResponseDto transaction = transactionService.getTransaction(transactionId);
        ApiResponse<TransactionResponseDto> response = ApiResponse.<TransactionResponseDto>builder()
                .message("Transaction: "+transactionId+" fetched successfully.")
                .status(HttpStatus.OK.value())
                .data(transaction)
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<List<TransactionResponseDto>>> getTransactionHistory(
            @PathVariable String accountNumber
    ){
        log.info("Fetching transaction history for account: {}", accountNumber);

        List<TransactionResponseDto> transactionHistory = transactionService.getTransactionHistory(accountNumber);
        ApiResponse<List<TransactionResponseDto>> response = new ApiResponse<>(
                "Transaction history for account: "+accountNumber+" fetched successfully.",
                HttpStatus.OK.value(),
                transactionHistory
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{transactionId}/verify")
    public ResponseEntity<ApiResponse<TransactionResponseDto>> verifyOtp(
            @PathVariable String transactionId,
            @RequestParam String otp
    ){
        log.info("Otp verification request - transaction: {}",transactionId);
        TransactionResponseDto verified = transactionService.verifyOtp(transactionId,otp);
         String message = verified.getFailureReason() == null ? "Otp verified successfully." : verified.getFailureReason();
        ApiResponse<TransactionResponseDto> response = new ApiResponse<>(
                message,
                HttpStatus.OK.value(),
                verified
        );
        return ResponseEntity.ok(response);
    }

}
