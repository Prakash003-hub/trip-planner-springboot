package com.travel.api.controller;

import com.travel.api.model.Expense;
import com.travel.api.model.Trip;
import com.travel.api.model.User;
import com.travel.api.repository.UserRepository;
import com.travel.api.service.ExpenseService;
import com.travel.api.service.TripService;
import com.travel.api.service.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.time.LocalTime;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final TripService tripService;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    public ExpenseController(ExpenseService expenseService, TripService tripService,
            FileStorageService fileStorageService, UserRepository userRepository) {
        this.expenseService = expenseService;
        this.tripService = tripService;
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
    }

    @PostMapping("/trip/{tripId}")
    public ResponseEntity<Expense> addExpense(
            @PathVariable Long tripId,
            @RequestParam("category") String category,
            @RequestParam("amount") Double amount,
            @RequestParam("memberName") String memberName,
            @RequestParam(value = "logDate", required = false) String logDate,
            @RequestParam(value = "logTime", required = false) String logTime,
            @RequestParam(value = "receipt", required = false) MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        Trip trip = tripService.getTripById(tripId);
        if (trip == null) {
            return ResponseEntity.notFound().build();
        }

        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);

        // Security Check: Only creator or member allowed
        boolean isCreator = trip.getUser().equals(user);
        boolean isMember = trip.getMembers().contains(user);

        if (!isCreator && !isMember) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        Expense expense = new Expense();
        expense.setTrip(trip);
        expense.setCategory(category);
        expense.setAmount(amount);

        if (logDate != null && !logDate.isEmpty()) {
            expense.setLogDate(LocalDate.parse(logDate));
        } else {
            expense.setLogDate(LocalDate.now());
        }

        if (logTime != null && !logTime.isEmpty()) {
            expense.setLogTime(LocalTime.parse(logTime));
        } else {
            expense.setLogTime(LocalTime.now());
        }

        // Auto-attribute to user if memberName is missing or "Self"
        if (memberName == null || memberName.trim().isEmpty() || memberName.equalsIgnoreCase("Self")) {
            expense.setMemberName(user != null ? user.getName() : "Self");
        } else {
            expense.setMemberName(memberName);
        }

        expense.setAddedBy(user);

        if (file != null && !file.isEmpty()) {
            String filename = fileStorageService.save(file);
            expense.setReceiptFilePath(filename);
        }

        return ResponseEntity.ok(expenseService.saveExpense(expense));
    }

    @GetMapping("/trip/{tripId}")
    public ResponseEntity<List<Expense>> getExpenses(@PathVariable Long tripId) {
        Trip trip = tripService.getTripById(tripId);
        if (trip == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(expenseService.getExpensesByTrip(trip));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteExpense(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Expense expense = expenseService.getExpenseById(id).orElse(null);
        if (expense == null) {
            return ResponseEntity.notFound().build();
        }

        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (!canModifyExpense(expense, user)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not have permission to delete this expense."));
        }

        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateExpense(
            @PathVariable Long id,
            @RequestParam("category") String category,
            @RequestParam("amount") Double amount,
            @RequestParam("memberName") String memberName,
            @RequestParam(value = "receipt", required = false) MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        Expense expense = expenseService.getExpenseById(id).orElse(null);
        if (expense == null) {
            return ResponseEntity.notFound().build();
        }

        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (!canModifyExpense(expense, user)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not have permission to update this expense."));
        }

        expense.setCategory(category);
        expense.setAmount(amount);
        expense.setMemberName(memberName == null || memberName.trim().isEmpty()
                ? (user != null ? user.getName() : "Self")
                : memberName.trim());

        if (file != null && !file.isEmpty()) {
            String filename = fileStorageService.save(file);
            expense.setReceiptFilePath(filename);
        }

        return ResponseEntity.ok(expenseService.saveExpense(expense));
    }

    private boolean canModifyExpense(Expense expense, User user) {
        if (expense == null || expense.getTrip() == null || user == null) {
            return false;
        }

        Trip trip = expense.getTrip();
        boolean isLeader = trip.getUser() != null && trip.getUser().equals(user);
        boolean isExpenseOwner = expense.getAddedBy() != null && expense.getAddedBy().equals(user);
        boolean isMember = trip.getMembers() != null && trip.getMembers().contains(user);

        return isLeader || (isExpenseOwner && isMember) || isExpenseOwner;
    }
}
