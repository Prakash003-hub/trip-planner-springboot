package com.travel.api.service;

import com.travel.api.dto.AuthenticationRequest;
import com.travel.api.dto.AuthenticationResponse;
import com.travel.api.dto.RegisterRequest;
import com.travel.api.model.User;
import com.travel.api.repository.TripRepository;
import com.travel.api.repository.UserRepository;
import com.travel.api.security.JwtUtils;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
        private final UserRepository repository;
        private final TripRepository tripRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtUtils jwtUtils;
        private final AuthenticationManager authenticationManager;      

        public AuthService(UserRepository repository, TripRepository tripRepository, PasswordEncoder passwordEncoder,
                        JwtUtils jwtUtils,
                        AuthenticationManager authenticationManager) {
                this.repository = repository;
                this.tripRepository = tripRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtUtils = jwtUtils;
                this.authenticationManager = authenticationManager;
        }

        public AuthenticationResponse register(RegisterRequest request) {
                // Check if user already exists
                if (repository.findByEmail(request.getEmail()).isPresent()) {
                        throw new RuntimeException("Email already in use");
                }
                if (repository.findByUserId(request.getUserId()).isPresent()) {
                        throw new RuntimeException("User ID already in use");
                }

                User user = new User();
                user.setName(request.getName());
                user.setUserId(request.getUserId());
                user.setEmail(request.getEmail());
                user.setPassword(passwordEncoder.encode(request.getPassword()));

                repository.save(user);
                var jwtToken = jwtUtils.generateToken(user);

                return new AuthenticationResponse(jwtToken, user.getName(), user.getUserId(), user.getEmail());
        }

        public AuthenticationResponse authenticate(AuthenticationRequest request) {
                String identifier = request.getIdentifier() != null ? request.getIdentifier().trim() : "";
                String password = request.getPassword() != null ? request.getPassword().trim() : "";
                String groupId = request.getGroupId() != null ? request.getGroupId().trim() : "";

                System.out.println(
                                "Login attempt with identifier: [" + identifier + "] and GroupID: [" + groupId + "]");

                // Try finding user by email or userId
                User user = repository.findByEmail(identifier)
                                .or(() -> repository.findByUserId(identifier))
                                .orElseThrow(() -> {
                                        System.out.println("User not found: [" + identifier + "]");
                                        return new RuntimeException("User not found");
                                });

                System.out.println("User found: " + user.getEmail());

                try {
                        authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(
                                                        user.getEmail(),
                                                        password));
                        System.out.println("Authentication successful for: " + user.getEmail());
                } catch (Exception e) {
                        System.out.println(
                                        "Authentication failed for: " + user.getEmail() + " Error: " + e.getMessage());
                        throw e;
                }

                // Verify Group ID if provided
                Long tripId = null;
                if (groupId != null && !groupId.isEmpty()) {
                        var trip = tripRepository.findByGroupId(groupId)
                                        .orElseThrow(() -> {
                                                System.out.println("Invalid Group ID: [" + groupId + "]");
                                                return new RuntimeException("Invalid Group ID");
                                        });
                        tripId = trip.getId();
                        System.out.println("Group ID verified: " + groupId + " for trip: " + tripId);
                }

                var jwtToken = jwtUtils.generateToken(user);

                return new AuthenticationResponse(jwtToken, user.getName(), user.getUserId(), user.getEmail(), tripId);
        }

        public User getUserByEmail(String email) {
                return repository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        }

        public void processForgotPassword(String email) {
                // In a real application, you would generate a token, save it, and send an
                // email.
                // For this demo, we'll just log it.
                System.out.println("Processing password reset for: " + email);
                repository.findByEmail(email).ifPresent(user -> {
                        System.out.println("User found! A reset link would be sent to: " + email);
                });
        }
}
