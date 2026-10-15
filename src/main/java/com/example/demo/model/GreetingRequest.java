package com.example.demo.model;

import jakarta.validation.constraints.NotBlank;

public record GreetingRequest(@NotBlank String name) {
}
