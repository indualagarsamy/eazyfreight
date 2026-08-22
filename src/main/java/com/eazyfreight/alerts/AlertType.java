package com.eazyfreight.alerts;

import java.util.List;
import java.util.Set;

import static com.eazyfreight.alerts.AlertCategory.CRITICAL;
import static com.eazyfreight.alerts.AlertCategory.HIGH;
import static com.eazyfreight.alerts.AlertCategory.LOW;
import static com.eazyfreight.alerts.AlertCategory.MEDIUM;
import static com.eazyfreight.alerts.RecipientRole.ACCOUNTING_STAFF;
import static com.eazyfreight.alerts.RecipientRole.COMPLIANCE_STAFF;
import static com.eazyfreight.alerts.RecipientRole.FINANCE_MANAGEMENT;
import static com.eazyfreight.alerts.RecipientRole.MANAGEMENT;
import static com.eazyfreight.alerts.RecipientRole.OPERATIONS_MANAGEMENT;
import static com.eazyfreight.alerts.RecipientRole.OPERATIONS_STAFF;

/**
 * The thirty-one alert definitions from the specification, as data.
 *
 * <p>Every definition names a category, a set of recipient roles, a title, and — for
 * the time-based ones — how many days before ETD the condition starts to matter. Some
 * also name a category they upgrade to as the ETD gets closer: L-003 begins as Medium
 * at ETD-7, becomes High at ETD-5 and Critical at ETD-3. That upgrade is a property of
 * the alert type, not something an operator decides, so it lives here.
 *
 * <p>Keeping them in an enum rather than a configuration table is deliberate. These are
 * the firm's operating rules; a new alert type is a code change with a test, not a row
 * somebody adds on a Friday. {@link AlertConfiguration} exists for the parts that
 * genuinely are per-deployment — thresholds, channels, whether an alert runs at all.
 *
 * <p>The condition itself is not here. It lives in {@link AlertRules}, because it needs
 * facts from five other contexts and an enum constant is the wrong place to reach for
 * a repository.
 */
public enum AlertType {

    // ------------------------------------------------------------ Logistics

    L001_CONTAINER_NOT_DISPATCHED(
            "L-001", AlertTrack.LOGISTICS, MEDIUM,
            "Container Not Yet Dispatched",
            10, HIGH, 7,
            "Create and dispatch outbound truck delivery order",
            OPERATIONS_STAFF),

    L002_CONTAINER_NOT_DELIVERED_TO_CUSTOMER(
            "L-002", AlertTrack.LOGISTICS, MEDIUM,
            "Container Not Yet Delivered to Customer",
            8, HIGH, 5,
            "Contact driver; confirm delivery; record actual delivery date",
            OPERATIONS_STAFF),

    L003_CUSTOMER_LOADING_OVERDUE(
            "L-003", AlertTrack.LOGISTICS, MEDIUM,
            "Customer Loading Overdue",
            7, CRITICAL, 3,
            "Contact customer; confirm loading timeline; assess ETD risk",
            OPERATIONS_STAFF),

    L004_INBOUND_BLOCKED_NO_ITN(
            "L-004", AlertTrack.LOGISTICS, HIGH,
            "Inbound Dispatch Blocked — ITN Not Received",
            null, CRITICAL, 3,
            "Compliance to file AES immediately; hold inbound dispatch until the ITN is confirmed",
            OPERATIONS_STAFF, COMPLIANCE_STAFF),

    L005_EARLY_PORT_DELIVERY_RISK(
            "L-005", AlertTrack.LOGISTICS, HIGH,
            "Early Port Delivery Risk",
            null, null, null,
            "Reschedule inbound delivery to on or after the earliest acceptance date",
            OPERATIONS_STAFF),

    L006_CBP_HOLD_CUTOFF_AT_RISK(
            "L-006", AlertTrack.LOGISTICS, CRITICAL,
            "Container Under CBP Hold — Vessel Cut-off at Risk",
            null, null, null,
            "Contact the carrier to request a loading delay; monitor the examination; "
                    + "prepare for possible reinstatement",
            OPERATIONS_STAFF, COMPLIANCE_STAFF),

    L007_SEAL_NUMBER_NOT_RECORDED(
            "L-007", AlertTrack.LOGISTICS, HIGH,
            "Seal Number Not Recorded",
            null, CRITICAL, 2,
            "Contact the customer to obtain the seal number and record it",
            OPERATIONS_STAFF),

    L008_CONTAINER_NOT_DELIVERED_TO_PORT(
            "L-008", AlertTrack.LOGISTICS, CRITICAL,
            "Container Not Yet Delivered to Port",
            3, null, null,
            "Contact the driver; confirm inbound dispatch status; escalate if unreachable",
            OPERATIONS_STAFF, OPERATIONS_MANAGEMENT),

    // ----------------------------------------------------------- Compliance

    C001_EEI_NOT_INITIATED(
            "C-001", AlertTrack.COMPLIANCE, MEDIUM,
            "EEI Filing Not Yet Initiated",
            10, HIGH, 7,
            "Initiate the EEI filing in AES",
            COMPLIANCE_STAFF),

    C002_EEI_NOT_SUBMITTED(
            "C-002", AlertTrack.COMPLIANCE, HIGH,
            "EEI Not Yet Submitted to CBP",
            7, CRITICAL, 5,
            "Complete and submit the EEI to CBP via AES",
            COMPLIANCE_STAFF),

    C003_EEI_REJECTED(
            "C-003", AlertTrack.COMPLIANCE, HIGH,
            "EEI Filing Rejected by CBP",
            null, CRITICAL, 5,
            "Review the rejection reason; correct the filing data; resubmit to CBP",
            COMPLIANCE_STAFF, OPERATIONS_STAFF),

    C004_ITN_NOT_RECEIVED_APPROACHING_CUTOFF(
            "C-004", AlertTrack.COMPLIANCE, HIGH,
            "ITN Not Received — Approaching Documentation Cut-off",
            5, CRITICAL, 3,
            "Verify the AES submission; file now if not submitted; contact CBP if it was",
            COMPLIANCE_STAFF, OPERATIONS_STAFF),

    C005_ITN_MISSING_CUTOFF_BREACHED(
            "C-005", AlertTrack.COMPLIANCE, CRITICAL,
            "ITN Not Received — Documentation Cut-off Breached",
            3, null, null,
            "Contact CBP directly; notify the carrier; assess whether loading can be "
                    + "delayed; prepare for reinstatement",
            COMPLIANCE_STAFF, OPERATIONS_STAFF, MANAGEMENT),

    C006_EEI_AMENDMENT_REQUIRED(
            "C-006", AlertTrack.COMPLIANCE, HIGH,
            "EEI Amendment Required",
            null, CRITICAL, 2,
            "File the EEI amendment via AES; record the amended ITN when it comes back",
            COMPLIANCE_STAFF),

    C007_LEGAL_DEADLINE_APPROACHING(
            "C-007", AlertTrack.COMPLIANCE, CRITICAL,
            "24-Hour Legal Deadline Approaching",
            1, null, null,
            "Emergency CBP contact; notify the carrier; the cargo may not be loadable "
                    + "on this vessel",
            COMPLIANCE_STAFF, OPERATIONS_STAFF, MANAGEMENT),

    // -------------------------------------------------------------- Finance

    F001_INVOICE_NOT_ISSUED(
            "F-001", AlertTrack.FINANCE, MEDIUM,
            "Invoice Not Yet Issued",
            null, HIGH, null,
            "Review the prepared invoice; confirm actuals; issue it to the customer",
            ACCOUNTING_STAFF),

    F002_PAYMENT_APPROACHING_DUE(
            "F-002", AlertTrack.FINANCE, LOW,
            "Payment Approaching Due Date",
            null, MEDIUM, null,
            "Send a payment reminder to the customer",
            ACCOUNTING_STAFF),

    F003_PAYMENT_OVERDUE(
            "F-003", AlertTrack.FINANCE, MEDIUM,
            "Payment Overdue",
            null, HIGH, null,
            "Contact the customer; escalate to Finance Management if no response in 2 days",
            ACCOUNTING_STAFF),

    F004_CREDIT_HOLD_THRESHOLD(
            "F-004", AlertTrack.FINANCE, HIGH,
            "Customer Payment Overdue — Credit Hold Threshold",
            null, null, null,
            "Finance Management to approve a credit hold; Operations to flag new bookings",
            ACCOUNTING_STAFF, FINANCE_MANAGEMENT, OPERATIONS_STAFF),

    F005_CARRIER_PAYMENT_DUE(
            "F-005", AlertTrack.FINANCE, HIGH,
            "Carrier Payment Due",
            null, CRITICAL, null,
            "Match the carrier invoice and execute the payment",
            ACCOUNTING_STAFF),

    F006_CARRIER_PAYMENT_OVERDUE(
            "F-006", AlertTrack.FINANCE, CRITICAL,
            "Carrier Payment Overdue",
            null, null, null,
            "Execute the carrier payment immediately; advise the carrier it is late",
            ACCOUNTING_STAFF, FINANCE_MANAGEMENT),

    F007_CARRIER_INVOICE_DISCREPANCY(
            "F-007", AlertTrack.FINANCE, MEDIUM,
            "Carrier Invoice Discrepancy",
            null, HIGH, null,
            "Contact the carrier to resolve the discrepancy; do not pay until it is resolved",
            ACCOUNTING_STAFF),

    F008_STORAGE_FEE_ACCRUING(
            "F-008", AlertTrack.FINANCE, HIGH,
            "Storage Fee Accruing",
            null, null, null,
            "Operations to confirm the storage period; Accounting to prepare the invoice",
            OPERATIONS_STAFF, ACCOUNTING_STAFF),

    // -------------------------------------------------------------- Booking

    B001_CARRIER_CONFIRMATION_NOT_RECEIVED(
            "B-001", AlertTrack.BOOKING, MEDIUM,
            "Carrier Confirmation Not Received",
            null, HIGH, null,
            "Pull the booking status from INTTRA; contact the carrier directly if no update",
            OPERATIONS_STAFF),

    B002_VESSEL_OVERBOOKING(
            "B-002", AlertTrack.BOOKING, HIGH,
            "Vessel Overbooking",
            null, CRITICAL, null,
            "Contact the customer; confirm reinstatement or cancellation; if reinstating, "
                    + "identify the next available sailing",
            OPERATIONS_STAFF),

    B003_REINSTATEMENT_RECALCULATION(
            "B-003", AlertTrack.BOOKING, LOW,
            "Reinstatement — Alert Recalculation Required",
            null, null, null,
            "Review the active alerts for this booking; file an EEI amendment; reissue "
                    + "the booking confirmation",
            OPERATIONS_STAFF, COMPLIANCE_STAFF, ACCOUNTING_STAFF),

    B004_ETD_CHANGED_BY_CARRIER(
            "B-004", AlertTrack.BOOKING, MEDIUM,
            "ETD Changed by Carrier",
            null, null, null,
            "Notify the customer; recalculate the payment due date; assess whether an "
                    + "EEI amendment is needed",
            OPERATIONS_STAFF, ACCOUNTING_STAFF),

    // -------------------------------------------------------- Documentation

    D001_CUTOFF_APPROACHING_PRECONDITIONS_UNMET(
            "D-001", AlertTrack.DOCUMENTATION, CRITICAL,
            "Documentation Cut-off Approaching — Preconditions Unmet",
            4, null, null,
            "Address each missing item: container number from the driver, seal from the "
                    + "customer, ITN from CBP",
            OPERATIONS_STAFF, COMPLIANCE_STAFF),

    D002_INSTRUCTIONS_NOT_SENT(
            "D-002", AlertTrack.DOCUMENTATION, HIGH,
            "Master BOL Instructions Not Yet Sent",
            3, CRITICAL, 2,
            "Compile and send the Master BOL instructions to the carrier",
            OPERATIONS_STAFF),

    D003_MASTER_BOL_NOT_RECEIVED(
            "D-003", AlertTrack.DOCUMENTATION, MEDIUM,
            "Master BOL Not Yet Received from Carrier",
            null, HIGH, 2,
            "Follow up with the carrier for Master BOL issuance",
            OPERATIONS_STAFF),

    D004_HOUSE_BOL_NOT_GENERATED(
            "D-004", AlertTrack.DOCUMENTATION, MEDIUM,
            "House BOL Not Yet Generated",
            null, HIGH, null,
            "Generate the House BOL and send it to the shipper and consignee agent",
            OPERATIONS_STAFF);

    private final String code;
    private final AlertTrack track;
    private final AlertCategory category;
    private final String title;
    private final Integer thresholdDays;
    private final AlertCategory escalatedCategory;
    private final Integer escalationThresholdDays;
    private final String recommendedAction;
    private final Set<RecipientRole> recipients;

    AlertType(String code, AlertTrack track, AlertCategory category, String title,
              Integer thresholdDays, AlertCategory escalatedCategory,
              Integer escalationThresholdDays, String recommendedAction,
              RecipientRole... recipients) {
        this.code = code;
        this.track = track;
        this.category = category;
        this.title = title;
        this.thresholdDays = thresholdDays;
        this.escalatedCategory = escalatedCategory;
        this.escalationThresholdDays = escalationThresholdDays;
        this.recommendedAction = recommendedAction;
        this.recipients = Set.of(recipients);
    }

    public String code() {
        return code;
    }

    public AlertTrack track() {
        return track;
    }

    /** The category the alert opens at, before any ETD-driven upgrade. */
    public AlertCategory baseCategory() {
        return category;
    }

    public String title() {
        return title;
    }

    /** Days before ETD at which the condition starts to matter, or null if not time-based. */
    public Integer thresholdDays() {
        return thresholdDays;
    }

    public String recommendedAction() {
        return recommendedAction;
    }

    public Set<RecipientRole> recipients() {
        return recipients;
    }

    /**
     * The category this alert should carry given how close the ETD now is.
     *
     * <p>L-003 is the clearest case: Medium when it first fires at ETD-7, Critical by
     * ETD-3. The same unanswered question is a different-sized problem depending on how
     * much time is left, and the dashboard should say so without anyone re-triaging it.
     */
    public AlertCategory categoryAt(Integer daysToEtd) {
        if (escalatedCategory == null || escalationThresholdDays == null || daysToEtd == null) {
            return category;
        }
        return daysToEtd <= escalationThresholdDays ? escalatedCategory : category;
    }

    /** All types belonging to one track, in declaration order. */
    public static List<AlertType> ofTrack(AlertTrack track) {
        return List.of(values()).stream().filter(type -> type.track == track).toList();
    }
}
