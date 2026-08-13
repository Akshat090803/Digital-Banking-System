package com.banking.transactionservice.type;

/**
 * Transaction Status lifecycle flow
 * Pending  ->  Processing  ->  Completed  (Clean Transaction)
 *                          ->  Pending_Verification (Suspicious detected)
 *                                          ->  Completed (Verified)
 *                                          ->  Flagged (SAGA Refund)
 *                          ->  Failed
 *                          ->  Flagged
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    PENDING_VERIFICATION,
    FLAGGED,
    FAILED,
    COMPENSATION_FAILED
}
