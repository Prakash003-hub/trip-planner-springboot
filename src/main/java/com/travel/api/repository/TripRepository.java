package com.travel.api.repository;

import com.travel.api.model.Trip;
import com.travel.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT t FROM Trip t LEFT JOIN t.members m WHERE t.user = :user OR m = :user")
    List<Trip> findAllByUserOrMember(@org.springframework.data.repository.query.Param("user") User user);

    java.util.Optional<Trip> findByGroupId(String groupId);
}
