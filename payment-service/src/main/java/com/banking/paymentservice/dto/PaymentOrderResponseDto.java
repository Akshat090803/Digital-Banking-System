package com.banking.paymentservice.dto;


import com.banking.paymentservice.type.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PaymentOrderResponseDto {

    private String paymentId;

    private String razorpayOrderId;

    private BigDecimal amount;

    private String currency;

    private PaymentStatus status;

    private String razorpayKeyId;

}
