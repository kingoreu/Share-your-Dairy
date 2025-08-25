package com.share.dairy.api.keyword;

import com.share.dairy.dto.keyword.characterKeywordImage.CreateRequest;
import com.share.dairy.model.keyword.CharacterKeywordImage;
import com.share.dairy.service.keyword.CharacterKeywordImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/character_keyword_images")
// update 추가 바람
public class CharacterKeywordImageController {

    private final CharacterKeywordImageService service;

    public CharacterKeywordImageController(CharacterKeywordImageService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Long> create(@RequestBody CreateRequest req) throws SQLException {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CharacterKeywordImage> get(@PathVariable long id) throws SQLException {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) throws SQLException {
        return service.deleteById(id) > 0 ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/latest")
    public ResponseEntity<CharacterKeywordImage> getLatestByUser(@RequestParam long userId) throws SQLException {
        return service.findLatestByUserId(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


}