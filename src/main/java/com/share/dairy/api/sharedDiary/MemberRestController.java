package com.share.dairy.api.sharedDiary;

import com.share.dairy.dto.sharedDiary.MemberAddRequest;
import com.share.dairy.dto.sharedDiary.MemberResponse;
import com.share.dairy.model.sharedDiary.SharedDiaryMember;
import com.share.dairy.service.sharedDiary.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

import static com.share.dairy.service.sharedDiary.MemberService.toResponse;

@RestController
@RequestMapping("/api/sharedDiary/{sharedDiaryId}/member")
public class MemberRestController {

    private final MemberService service;

    public MemberRestController(MemberService service) {
        this.service = service;
    }

    // 멤버 목록
    @GetMapping
    public List<MemberResponse> list(@PathVariable long sharedDiaryId) throws SQLException {
        return service.findMembers(sharedDiaryId).stream().map(MemberService::toResponse).toList();

    }

    // 멤버 추가
    @PostMapping
    public ResponseEntity<?> add(@PathVariable long sharedDiaryId,
                                 @RequestBody MemberAddRequest req) throws SQLException {
        service.addMember(sharedDiaryId, req);
        return ResponseEntity.ok().body("멤버 추가 완료");
    }

    // 멤버 제거 (혹시나 몰라서)
    public ResponseEntity<?> remove(@PathVariable long sharedDiaryId,
                                    @PathVariable long userId) throws SQLException {
        service.removeMember(sharedDiaryId, userId);
        return ResponseEntity.ok().body("멤버 제거 완료");
    }
}