package com.travel.api.repository;

import com.travel.api.model.Expense;
import com.travel.api.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByTrip(Trip trip);
}
