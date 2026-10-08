package com.carticom.service;

import com.carticom.dto.returnrequest.ReturnCreateRequest;
import com.carticom.dto.returnrequest.ReturnResponse;
import com.carticom.exception.BadRequestException;
import com.carticom.exception.ResourceNotFoundException;
import com.carticom.model.Order;
import com.carticom.model.OrderStatus;
import com.carticom.model.ReturnRequest;
import com.carticom.repository.OrderRepository;
import com.carticom.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnService {

    private static final int MAX_REASON_LENGTH = 1000;

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRequestRepository;

    /**
     * Records a return request against one of the customer's delivered orders.
     * One open request at a time — a second one is refused until the first is
     * reviewed. Anyone who doesn't own the order gets a 404 so we never
     * reveal whether an order exists.
     */
    @Transactional
    public ReturnResponse createReturn(String customerEmail, Long orderId, ReturnCreateRequest request) {
        String reason = request.getReason() != null ? request.getReason().trim() : null;
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Please tell us what's wrong with your order");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("Your reason can be up to 1000 characters");
        }

        Order order = getOwnedOrder(customerEmail, orderId);
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Only delivered orders can be returned");
        }
        if (returnRequestRepository.existsByOrderIdAndStatus(orderId, ReturnRequest.STATUS_REQUESTED)) {
            throw new BadRequestException("A return is already being processed for this order");
        }

        ReturnRequest returnRequest = ReturnRequest.builder()
                .orderId(order.getId())
                .customerId(customerEmail)
                .reason(reason)
                .status(ReturnRequest.STATUS_REQUESTED)
                .build();
        returnRequestRepository.save(returnRequest);
        log.info("Return request created for order {} by {}", orderId, customerEmail);

        return mapToResponse(returnRequest);
    }

    /** The return requests for one of the customer's orders, newest first. */
    public List<ReturnResponse> getReturnsForOrder(String customerEmail, Long orderId) {
        getOwnedOrder(customerEmail, orderId);
        return returnRequestRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /** Everything the customer has asked to return, newest first. */
    public List<ReturnResponse> getMyReturns(String customerEmail) {
        return returnRequestRepository.findByCustomerIdOrderByCreatedAtDesc(customerEmail).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /** Loads the order and confirms it belongs to this customer — following the
     *  same pattern as cancelling an order: not yours is a 404, never a 403. */
    private Order getOwnedOrder(String customerEmail, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getCustomer() == null || !customerEmail.equals(order.getCustomer().getEmail())) {
            throw new ResourceNotFoundException("Order not found");
        }
        return order;
    }

    private ReturnResponse mapToResponse(ReturnRequest returnRequest) {
        return ReturnResponse.builder()
                .id(returnRequest.getId())
                .orderId(returnRequest.getOrderId())
                .customerId(returnRequest.getCustomerId())
                .reason(returnRequest.getReason())
                .status(returnRequest.getStatus())
                .createdAt(returnRequest.getCreatedAt())
                .updatedAt(returnRequest.getUpdatedAt())
                .build();
    }
}
