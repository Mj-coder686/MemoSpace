package com.memospace.api;

import com.memospace.realtime.RealtimeGateway;
import com.memospace.security.CurrentUser;
import com.memospace.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/friends")
public class ChatController {
    private final ChatService chat;
    private final RealtimeGateway realtime;

    public ChatController(ChatService chat, RealtimeGateway realtime) {
        this.chat = chat;
        this.realtime = realtime;
    }

    @GetMapping("/{friendId}/messages")
    public ChatService.MessagePage history(
            @PathVariable @Positive long friendId,
            @RequestParam(required = false) @Positive Long beforeId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return chat.history(CurrentUser.id(), friendId, beforeId, limit);
    }

    @PostMapping("/{friendId}/messages/read")
    public Map<String, Object> read(@PathVariable @Positive long friendId,
                                    @Valid @RequestBody ReadRequest request) {
        ChatService.ReadReceipt receipt = chat.markRead(CurrentUser.id(), friendId, request.throughMessageId());
        Map<String, Object> event = readEvent(receipt);
        realtime.sendToUser(receipt.readerId(), "READ", event);
        realtime.sendToUser(receipt.friendId(), "READ", event);
        return event;
    }

    public static Map<String, Object> readEvent(ChatService.ReadReceipt receipt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("userId", receipt.readerId());
        event.put("friendId", receipt.friendId());
        event.put("throughMessageId", receipt.throughMessageId());
        event.put("readAt", receipt.readAt());
        event.put("updatedCount", receipt.updatedCount());
        return event;
    }

    public record ReadRequest(@Positive long throughMessageId) {}
}
