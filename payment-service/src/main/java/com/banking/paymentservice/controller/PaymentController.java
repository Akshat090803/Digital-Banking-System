package com.banking.paymentservice.controller;

import com.banking.paymentservice.dto.ApiResponse;
import com.banking.paymentservice.dto.CreatePaymentRequestDto;
import com.banking.paymentservice.dto.PaymentOrderResponseDto;
import com.banking.paymentservice.service.PaymentService;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentOrderResponseDto>> createPaymentOrder(
            @Valid @RequestBody CreatePaymentRequestDto request
            ) throws RazorpayException {
        PaymentOrderResponseDto paymentOrder = paymentService.createPaymentOrder(request);
        ApiResponse<PaymentOrderResponseDto> response = new ApiResponse<>(
                "Payment order created successfully.",
                HttpStatus.CREATED.value(),
                paymentOrder
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //Razorpay webhook endpoint
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<String>> handleWebHook(
            @RequestBody Map<String,Object> payload
            ){

        paymentService.handleWebHook(payload);
        ApiResponse<String> response=new ApiResponse<>(
                "WebHook processed.",
                HttpStatus.OK.value(),
                "WebHook processed."
        );

        return ResponseEntity.ok(response);
    }
}
