package com.mynix.backend.service.impl;

import com.mynix.backend.dto.customer.CustomerRequest;
import com.mynix.backend.dto.customer.CustomerResponse;
import com.mynix.backend.dto.customer.PaymentRequest;
import com.mynix.backend.dto.customer.PaymentResponse;
import com.mynix.backend.model.Customer;
import com.mynix.backend.model.CustomerTransaction;
import com.mynix.backend.model.CustomerTransactionType;
import com.mynix.backend.model.PaymentMethod;
import com.mynix.backend.repository.CustomerRepository;
import com.mynix.backend.repository.CustomerTransactionRepository;
import com.mynix.backend.service.CustomerService;
import com.mynix.backend.dto.customer.CustomerTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerTransactionRepository transactionRepository;

    @Override
    public CustomerResponse create(CustomerRequest request) {

        String name = request.getName().trim();
        String contactNumber = request.getContactNumber().trim();

        if (customerRepository.existsByContactNumber(contactNumber)) {

            throw new RuntimeException(
                    "A customer with this contact number already exists."
            );
        }

        Customer customer = Customer.builder()
                .name(name)
                .contactNumber(contactNumber)
                .active(true)
                .build();

        return toResponse(customerRepository.save(customer));
    }

    @Override
    public CustomerResponse update(
            Long id,
            CustomerRequest request
    ) {
        Customer customer = getCustomer(id);
        String name = request.getName().trim();
        String contactNumber = request.getContactNumber().trim();

        if (customerRepository.existsByContactNumberAndIdNot(
                contactNumber, id
        )) {
            throw new RuntimeException(
                    "A customer with this contact number already exists."
            );
        }
        customer.setName(name);
        customer.setContactNumber(contactNumber);

        return toResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {

        return toResponse(getCustomer(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAll() {

        return customerRepository
                .findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> search(String query) {

        if (query == null || query.isBlank()) {
            return getAll();
        }

        return customerRepository
                .findByNameContainingIgnoreCaseOrContactNumberContaining(
                        query.trim(),
                        query.trim()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void deactivate(Long id) {

        Customer customer = getCustomer(id);

        customer.setActive(false);

        customerRepository.save(customer);
    }

    @Override
    public PaymentResponse recordPayment(Long customerId, PaymentRequest request) {
        Customer customer = getCustomer(customerId);
        BigDecimal previousOutstanding =
                calculateOutstanding(customerId);

        if (previousOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException(
                    "This customer has no outstanding balance."
            );
        }
        BigDecimal paymentAmount = request.getAmount();
        if (paymentAmount.compareTo(previousOutstanding) > 0) {

            throw new RuntimeException(
                    "Payment amount cannot exceed the customer's outstanding balance."
            );
        }
        if (request.getPaymentMethod() == null) {

            throw new RuntimeException(
                    "Payment method is required."
            );
        }
        if (request.getPaymentMethod() != PaymentMethod.CASH
                && request.getPaymentMethod() != PaymentMethod.BANK_DEPOSIT
                && request.getPaymentMethod() != PaymentMethod.CHEQUE) {

            throw new RuntimeException(
                    "Customer payments can only be made by Cash, Bank Deposit or Cheque."
            );
        }
        CustomerTransaction transaction =
                CustomerTransaction.builder()
                        .customer(customer)
                        .type(CustomerTransactionType.PAYMENT)
                        .amount(paymentAmount)
                        .description(
                                request.getDescription() != null
                                        ? request.getDescription().trim()
                                        : null
                        )
                        .build();

        transactionRepository.save(transaction);
        BigDecimal remainingOutstanding =
                previousOutstanding.subtract(paymentAmount);
        return PaymentResponse.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .paymentAmount(paymentAmount)
                .paymentMethod(
                        request.getPaymentMethod().name()
                )
                .previousOutstanding(previousOutstanding)
                .remainingOutstanding(remainingOutstanding)
                .description(transaction.getDescription())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerTransactionResponse> getTransactions(
            Long customerId
    ) {

        getCustomer(customerId);

        return transactionRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(transaction -> {

                    Long saleId = null;
                    String invoiceNumber = null;

                    if (transaction.getSale() != null) {
                        saleId = transaction.getSale().getId();
                        invoiceNumber =
                                transaction.getSale().getInvoiceNumber();
                    }

                    return CustomerTransactionResponse.builder()
                            .id(transaction.getId())
                            .type(transaction.getType())
                            .amount(transaction.getAmount())
                            .description(transaction.getDescription())
                            .createdAt(transaction.getCreatedAt())
                            .saleId(saleId)
                            .invoiceNumber(invoiceNumber)
                            .build();
                })
                .toList();
    }

    private Customer getCustomer(Long id) {

        return customerRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Customer not found"
                        )
                );
    }

    private CustomerResponse toResponse(Customer customer) {

        BigDecimal outstanding =
                calculateOutstanding(customer.getId());

        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .contactNumber(customer.getContactNumber())
                .active(customer.getActive())
                .outstanding(outstanding)
                .build();
    }

    private BigDecimal calculateOutstanding(Long customerId) {
        return transactionRepository
                .findByCustomerId(customerId)
                .stream()
                .map(transaction -> {

                    if (transaction.getType()
                            == CustomerTransactionType.CREDIT_SALE) {

                        return transaction.getAmount();
                    }

                    return transaction.getAmount().negate();
                })
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

}