package com.share.dairy.controller;

import com.share.dairy.dto.diary.diaryEntry.CreateRequest;
import com.share.dairy.model.enums.Visibility;
import com.share.dairy.service.diary.DiaryService;
import com.share.dairy.dao.diary.DiaryEntryDao;
import com.share.dairy.dao.sharedDiary.SharedDiaryRoomDao;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public class OurDiaryRoomController {

    // UI
    @FXML private Label roomTitle;          // 상단 방 제목
    @FXML private TextField titleField;     // 새 글 제목
    @FXML private TextArea contentArea;     // 새 글 본문
    @FXML private VBox listContainer;       // 글 목록

    // 상태
    private long sharedDiaryId;
    private String sharedDiaryTitle;

    // 서비스/DAO
    private final DiaryService diaryService = new DiaryService(new DiaryEntryDao());
    private final SharedDiaryRoomDao roomDao = new SharedDiaryRoomDao();

    /** OurDiaryController.openRoom(...) 에서 호출 */
    public void initRoom(long sharedDiaryId, String title) {
        this.sharedDiaryId = sharedDiaryId;
        this.sharedDiaryTitle = title;
        if (roomTitle != null) roomTitle.setText("OUR DIARY · " + title);
        refreshList();
    }

    /** 저장 */
    @FXML
    private void onSave() {
        String t = titleField.getText() == null ? "" : titleField.getText().trim();
        String c = contentArea.getText() == null ? "" : contentArea.getText().trim();
        if (c.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "본문을 입력해 주세요.").showAndWait();
            return;
        }
        try {
            var req = new CreateRequest();
            req.setEntryDate(LocalDate.now());
            req.setTitle(t);
            req.setDiaryContent(c);
            req.setVisibility(Visibility.PUBLIC); // 공유방은 기본 공개
            req.setSharedDiaryId(sharedDiaryId);  // ✅ 이 방으로 저장

            diaryService.create(req);             // (userId는 서비스에서 세션으로 처리)

            titleField.clear();
            contentArea.clear();
            refreshList();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "저장 실패: " + e.getMessage()).showAndWait();
        }
    }

    /** 목록 로드 */
    private void refreshList() {
        try {
            var rows = roomDao.findEntries(sharedDiaryId); // 작성자 닉네임 포함
            listContainer.getChildren().clear();
            for (var r : rows) listContainer.getChildren().add(makeCard(r));
        } catch (Exception e) {
            listContainer.getChildren().setAll(new Label("목록 로드 실패: " + e.getMessage()));
        }
    }

    /** 카드 UI (작성자 표시) */
    private VBox makeCard(SharedDiaryRoomDao.EntryRow r) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12));
        card.setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 12;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 3);
        """);

        var meta = new Label("DATE " + r.entryDate() + "   ·   작성자 " + r.nickname());
        meta.setStyle("-fx-text-fill:#666; -fx-font-size:12;");

        String title = (r.title() == null || r.title().isBlank()) ? "(제목 없음)" : r.title();
        var titleLb = new Label(title);
        titleLb.setStyle("-fx-font-size:15; -fx-font-weight:700;");

        String body = r.content() == null ? "" : r.content();
        if (body.length() > 300) body = body.substring(0, 300) + "…";
        var contentLb = new Label(body);
        contentLb.setWrapText(true);

        card.getChildren().addAll(meta, titleLb, contentLb);
        return card;
    }
}
