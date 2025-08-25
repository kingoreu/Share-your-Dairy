package com.share.dairy.service.keyword;

import com.share.dairy.dao.keyword.KeywordImageDao;
import com.share.dairy.dto.keyword.keywordImage.CreateRequest;
import com.share.dairy.dto.keyword.keywordImage.WithKeywordsDto;
import com.share.dairy.model.keyword.KeywordImage;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
// update 추가 바람
public class KeywordImageService {

    private final KeywordImageDao dao;

    public KeywordImageService(KeywordImageDao dao) {
        this.dao = dao;
    }

    public long create(CreateRequest req) throws SQLException {
        var m = new KeywordImage();
        m.setAnalysisId(req.getAnalysisId());
        m.setUserId(req.getUserId());
        m.setCreatedAt(LocalDateTime.now()); // 테이블 DEFAULT 없으니 여기서 세팅
        return dao.insert(m);
    }

    public Optional<KeywordImage> findById(long id) throws SQLException {
        return dao.findById(id);
    }

    public List<KeywordImage> findByUserId(long userId) throws SQLException {
        return dao.findByUserId(userId);
    }

    // 키워드까지 같이 필요할 때
    public List<WithKeywordsDto> findWithKeywordsByUserId(long userId) throws SQLException {
        return dao.findWithKeywordsByUserId(userId);
    }

    public int deleteById(long id) throws SQLException {
        return dao.deleteById(id);
    }
}
