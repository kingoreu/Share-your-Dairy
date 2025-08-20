package com.share.dairy.service.keyword;

import com.share.dairy.dao.keyword.CharacterKeywordImageDao;
import com.share.dairy.model.keyword.CharacterKeywordImage;
import com.share.dairy.dto.keyword.characterKeywordImage.CreateRequest;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
// update 추가 바람
public class CharacterKeywordImageService {

    private final CharacterKeywordImageDao dao;

    public CharacterKeywordImageService(CharacterKeywordImageDao dao) {
        this.dao = dao;
    }

    public long create(CreateRequest req) throws SQLException {
        var m = new CharacterKeywordImage();
        m.setAnalysisId(req.getAnalysisId());
        m.setUserId(req.getUserId());
        m.setCreatedAt(LocalDateTime.now());
        return dao.insert(m);
    }

    public Optional<CharacterKeywordImage> findById(long id) throws SQLException {
        return dao.findById(id);
    }

    public List<CharacterKeywordImage> findByUserId(long userId) throws SQLException {
        return dao.findByUserId(userId);
    }

    public int deleteById(long id) throws SQLException {
        return dao.deleteById(id);
    }

}
