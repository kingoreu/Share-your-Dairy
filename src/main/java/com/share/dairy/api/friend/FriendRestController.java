package com.share.dairy.api.friend;

import com.share.dairy.dto.friend.FriendRequestDto;
import com.share.dairy.dto.friend.FriendResponseDto;
import com.share.dairy.model.friend.Friendship;
import com.share.dairy.service.friend.FriendshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/friend")
public class FriendRestController {

    private final FriendshipService friendshipService;

    public FriendRestController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    // 친구 요청 보내기
    @PostMapping("/request")
    public ResponseEntity<?> sendFriendRequest(@RequestBody FriendRequestDto dto) throws SQLException {
        friendshipService.send(dto.getSenderId(), dto.getReceiverId());
        return ResponseEntity.ok().body("친구 요청 보냈습니다.");
    }

    // 친구 요청 받기
    @PostMapping("/accept")
    public ResponseEntity<?> acceptFriendRequest(@RequestBody FriendRequestDto dto) throws SQLException {
        friendshipService.accept(dto.getReceiverId(), dto.getSenderId());
        return ResponseEntity.ok().body("친구 요청을 수락했습니다.");
    }

    // 친구 요청 거절
    @PostMapping("/reject")
    public ResponseEntity<?> rejectFriendRequest(@RequestBody FriendRequestDto dto) throws SQLException {
        friendshipService.reject(dto.getReceiverId(), dto.getSenderId());
        return ResponseEntity.ok().body("친구 요청을 거절했습니다.");
    }

    // 친구 요청 목록 조회
    @GetMapping("/pending/{userId}")
    public List<FriendResponseDto> getPendingRequests(@PathVariable long userId) throws SQLException {
        return friendshipService.findPendingFor(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // 친구 목록 조회
    @GetMapping("/list/{userId}")
    public List<FriendResponseDto> getFriends(@PathVariable long userId) throws SQLException {
        return friendshipService.findFriendsFor(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private FriendResponseDto toResponse(Friendship f) {
        FriendResponseDto dto = new FriendResponseDto();
        dto.setUserId(f.getUserId());
        dto.setFriendId(f.getFriendId());
        dto.setFriendshipStatus(f.getFriendshipStatus());
        dto.setRequestedAt(f.getRequestedAt());
        dto.setRespondedAt(f.getRespondedAt());
        return dto;
    }
}