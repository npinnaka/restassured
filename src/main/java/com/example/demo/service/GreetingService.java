package com.example.demo.service;

import com.example.demo.model.GreetingRequest;
import com.example.demo.model.GreetingResponse;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public GreetingResponse greet(GreetingRequest request, String username) {
        return new GreetingResponse("Hello, " + request.name() + "!", username);
    }
}
