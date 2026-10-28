package com.neram.pos.controller;

import com.neram.pos.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Marketing opt-out for customers, opened from a link in a message. No login; the cafe is named in the URL
 * ({@code /api/public/{cafe}/...}) so JwtAuthenticationFilter selects that cafe's database before the token is looked up.
 */
@RestController
@RequestMapping("/api/public/{cafe}/campaigns")
@RequiredArgsConstructor
public class PublicCampaignController {

    private final CampaignService campaignService;

    @GetMapping("/unsubscribe/{token}")
    public ResponseEntity<Map<String, Object>> unsubscribeGet(@PathVariable String cafe, @PathVariable String token) {
        return unsubscribe(token);
    }

    @PostMapping("/unsubscribe/{token}")
    public ResponseEntity<Map<String, Object>> unsubscribePost(@PathVariable String cafe, @PathVariable String token) {
        return unsubscribe(token);
    }

    private ResponseEntity<Map<String, Object>> unsubscribe(String token) {
        boolean result = campaignService.unsubscribe(token);
        return ResponseEntity.ok(Map.of("success", result, "message", "You have successfully unsubscribed from marketing communications."));
    }
}
