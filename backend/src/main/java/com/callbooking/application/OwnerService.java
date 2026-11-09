package com.callbooking.application;

import org.springframework.stereotype.Service;

import com.callbooking.domain.Owner;

/**
 * Owner use case. The owner profile and booking rules are assembled from
 * configuration in {@code config} and exposed here unchanged.
 */
@Service
public class OwnerService {

    private final Owner owner;

    public OwnerService(Owner owner) {
        this.owner = owner;
    }

    public Owner getOwner() {
        return owner;
    }
}
