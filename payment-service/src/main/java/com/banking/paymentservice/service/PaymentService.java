package com.banking.paymentservice.service;

import com.banking.paymentservice.dto.CreatePaymentRequestDto;
import com.banking.paymentservice.dto.PaymentOrderResponseDto;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.repository.PaymentRepository;
import com.banking.paymentservice.type.PaymentStatus;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private static final String PAYMENT_COMPLETED_TOPIC="payment.completed";
    private static final String PAYMENT_FAILED_TOPIC="payment.failed";
    private final KafkaTemplate<String,Object> kafkaTemplate;
    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    /**
     * Create razorpay payment order
     * Flow:
     *  1) create order in razorpay
     *  2) save payment record in db
     *  3) return order details to frontend
     *  4) frontend will show razorpay checkout page
     *  5) user pays
     *  6) razorpay calls webhook
     * @param request
     * @return
     */
    public PaymentOrderResponseDto createPaymentOrder(@Valid CreatePaymentRequestDto request) throws RazorpayException {

        log.info("Creating payment order for account: {} amount: {}",
                request.getAccountNumber(),request.getAmount());

        RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId,razorpayKeySecret);

        //convert amount to the smallest unit like INR -> paisa
        int convertedAmount = request.getAmount().multiply(
                BigDecimal.valueOf(100)
        ).intValue();

        //1) Create order in razorpay
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount",convertedAmount);
        orderRequest.put("currency","USD/INR");
        //Max 36 char so we make uuid small be extracting substring
        String uniqueReceipt="rcpt_"+System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-","").substring(0,10);

        orderRequest.put("receipt",uniqueReceipt);

        Order razorpayOrder = razorpayClient.orders.create(orderRequest);
        log.info("Razorpay order created: {}",razorpayOrder.get("id").toString());

        //2) Save record in db

        Payment payment = Payment.builder()
                .razorpayOrderId(razorpayOrder.get("id").toString())
                .accountNumber(request.getAccountNumber())
                .currency("USD/INR")
                .amount(request.getAmount())
                .status(PaymentStatus.CREATED)
                .description(request.getDescription())
                .build();
        Payment savedPayment = paymentRepository.save(payment);



        return  PaymentOrderResponseDto.builder()
                .paymentId(savedPayment.getId())
                .razorpayOrderId(razorpayOrder.get("id").toString())
                .amount(request.getAmount())
                .currency(savedPayment.getCurrency())
                .status(savedPayment.getStatus())
                .razorpayKeyId(razorpayKeyId)
                .build();
    }

    //payment gateways provides us webhooks which listens events on frontend like payment-success
//    payment-failed etc. and help to notify backend to take appropriate actions
    public void handleWebHook(Map<String, Object> payload) {

        log.info("Received razorpay webhook: {}",payload.get("event"));

        String event = payload.get("event").toString();

        if("payment.captured".equals(event)){
            handlePaymentSuccess(payload);
        }
        else if("payment.failed".equals(event)){
            handlePaymentFailure(payload);
        }
    }

    @Transactional
    private void handlePaymentSuccess(Map<String, Object> payload) {
        try{
            Map<String,Object> paymentData = extractPaymentData(payload);
            String orderId = paymentData.get("order_id").toString();
            String paymentId = paymentData.get("id").toString();

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                        .orElseThrow(()->new RuntimeException(
                                "Payment not found for order: "+orderId
                        ));

            payment.setRazorpayPaymentId(paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);

            //publish payment completed event to kafka
            Map<String,Object> event = new HashMap<>();
            event.put("paymentId",payment.getId());
            event.put("accountNumber",payment.getAccountNumber());
            event.put("amount",payment.getAmount());
            event.put("razorpayPaymentId",paymentId);

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC,payment.getId(),event);
            log.info("Payment completed: {}",payment.getId());


        } catch (Exception e) {
            log.error("Error handling payment success: {}",e.getMessage());
        }
    }

    @Transactional
    private void handlePaymentFailure(Map<String, Object> payload) {
        try{
            Map<String,Object> paymentData = extractPaymentData(payload);
            String orderId = paymentData.get("order_id").toString();
            String paymentId = paymentData.get("id").toString();

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(()->new RuntimeException(
                            "Payment not found for order: "+orderId
                    ));


            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setFailureReason("Payment failed via Razorpay.");

            //publish payment failed event to kafka
            Map<String,Object> event = new HashMap<>();
            event.put("paymentId",payment.getId());
            event.put("accountNumber",payment.getAccountNumber());
            event.put("amount",payment.getAmount());
            event.put("reason",payment.getFailureReason());

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC,payment.getId(),event);
            log.info("Payment failed: {}",payment.getId());


        } catch (Exception e) {
            log.error("Error handling payment failure: {}",e.getMessage());
        }
    }

    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {

        Map<String, Object> entity = (Map<String, Object>) payload.get("payload");
        Map<String, Object>  paymentWrapper= (Map<String, Object>) entity.get("payment");

        return  (Map<String, Object>) paymentWrapper.get("entity");


    }
}
