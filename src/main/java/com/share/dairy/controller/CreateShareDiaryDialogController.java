package com.share.dairy.controller;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.scene.control.cell.CheckBoxListCell;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CreateShareDiaryDialogController
 * --------------------------------
 * - 연결 FXML: /fxml/diary/our_diary/create-share-diary-dialog.fxml
 * - 역할:
 *   1) 제목 입력/검증
 *   2) 친구 검색 및 체크 선택
 *   3) START 시 Result(title, buddyIds) 반환
 *
 * 사용 흐름(호출 측: OurDiaryController.onNew()):
 *  - FXMLLoader로 FXML 로드 → controller 참조
 *  - controller.setBuddies(...)로 친구 목록 주입
 *  - Stage를 모달로 띄워 showAndWait()
 *  - controller.getResult()로 결과 확인
 */
public class CreateShareDiaryDialogController {

    /* ========== FXML 바인딩 ========== */
    @FXML private TextField titleField;                   // 제목 입력
    @FXML private TextField searchField;                  // 친구 검색
    @FXML private ListView<SelectableBuddy> buddyListView; // 체크박스 리스트
    @FXML private Button startButton;                     // (있으면 기본버튼으로)

    /* ========== 내부 모델 ========== */

    /** 화면에서 체크 선택 가능한 버디 래퍼 */
    public static class SelectableBuddy {
        private final String id;
        private final String name;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        public SelectableBuddy(String id, String name) {
            this.id = id;
            this.name = name;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        @Override public String toString() { return name; }
    }

    /** 호출 측에서 넘겨줄 가벼운 버디 DTO (실제 Buddy 엔티티 사용해도 OK) */
    public record BuddyLite(String id, String name) {}

    /** START 클릭 성공 시 호출 측으로 반환할 결과 */
    public record Result(String title, List<String> buddyIds) {}

    /* ========== 상태 ========== */
    private final ObservableList<SelectableBuddy> items = FXCollections.observableArrayList();
    private final FilteredList<SelectableBuddy> filtered = new FilteredList<>(items, b -> true);
    private Optional<Result> result = Optional.empty();

    /* ========== 라이프 사이클 ========== */
    @FXML
    private void initialize() {
        // 1) 체크박스 셀 팩토리: 선택 상태 ↔ 모델 바인딩, 텍스트는 이름
        buddyListView.setItems(filtered);
        buddyListView.setCellFactory(lv -> new CheckBoxListCell<>(
            // BooleanProperty는 ObservableValue<Boolean>를 상속하므로 캐스팅 불필요
            (SelectableBuddy sb) -> sb.selectedProperty(),
            new StringConverter<SelectableBuddy>() {
                @Override public String toString(SelectableBuddy sb) {
                    return (sb == null) ? "" : sb.getName();
                }
                @Override public SelectableBuddy fromString(String s) { return null; }
            }
        ));

        // 2) 검색어 → 이름 부분 일치 필터
        searchField.textProperty().addListener((obs, oldV, q) -> {
            String s = (q == null) ? "" : q.trim().toLowerCase();
            filtered.setPredicate(b -> {
                String name = (b.getName() == null) ? "" : b.getName().toLowerCase();
                return name.contains(s);
            });
        });

        // 3) 포커스 & 단축키(ESC 닫기, Enter 시작, Space 체크 토글, 더블클릭 토글)
        Platform.runLater(() -> {
            Scene sc = titleField.getScene();
            if (startButton != null) startButton.setDefaultButton(true);
            titleField.requestFocus();

            sc.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                // ESC: 닫기
                if (e.getCode() == KeyCode.ESCAPE) {
                    onCancel();
                    e.consume();
                    return;
                }
                // ENTER: 시작 (검색창/제목칸/리스트 어디서든)
                if (e.getCode() == KeyCode.ENTER) {
                    onStart();
                    e.consume();
                    return;
                }
                // SPACE: 리스트에서 현재 항목 체크 토글
                if (e.getCode() == KeyCode.SPACE && sc.getFocusOwner() == buddyListView) {
                    int idx = buddyListView.getSelectionModel().getSelectedIndex();
                    if (idx >= 0) {
                        SelectableBuddy sb = filtered.get(idx);
                        sb.selectedProperty().set(!sb.selectedProperty().get());
                        e.consume();
                    }
                }
            });

            buddyListView.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2) {
                    int idx = buddyListView.getSelectionModel().getSelectedIndex();
                    if (idx >= 0) {
                        SelectableBuddy sb = filtered.get(idx);
                        sb.selectedProperty().set(!sb.selectedProperty().get());
                    }
                }
            });
        });
    }

    /* ========== 외부(호출 측) API ========== */

    /** 친구 목록 주입 (DB/서비스에서 가져온 리스트를 전달) */
    public void setBuddies(Collection<BuddyLite> buddies) {
        if (buddies == null) {
            items.clear();
            return;
        }
        items.setAll(buddies.stream()
                .map(b -> new SelectableBuddy(b.id(), b.name()))
                .collect(Collectors.toList()));
    }

    /** showAndWait() 이후 START로 닫혔다면 값이 존재 */
    public Optional<Result> getResult() { return result; }

    /* ========== 버튼 핸들러 ========== */

    /** 취소: 값 없이 닫기 */
    @FXML
    private void onCancel() {
        close();
    }

    /** START: 입력 검증 → 결과 저장 → 닫기 */
    @FXML
    private void onStart() {
        if (!validateInputs()) return;

        String title = titleField.getText().trim();
        List<String> selectedIds = items.stream()
                .filter(SelectableBuddy::isSelected)
                .map(SelectableBuddy::getId)
                .toList();

        result = Optional.of(new Result(title, selectedIds));
        close();
    }

    /* ========== 유틸 ========== */

    /** 간단 입력 검증 + 경고 */
    private boolean validateInputs() {
        String title = (titleField.getText() == null) ? "" : titleField.getText().trim();
        if (title.isEmpty()) {
            warn("제목을 입력하세요.");
            return false;
        }
        boolean any = items.stream().anyMatch(SelectableBuddy::isSelected);
        if (!any) {
            warn("초대할 버디를 한 명 이상 선택하세요.");
            return false;
        }
        return true;
    }

    private void warn(String msg) {
        new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK).showAndWait();
    }

    /** 현재 씬의 Stage를 얻어 안전하게 닫기 */
    private void close() {
        Stage st = (Stage) titleField.getScene().getWindow();
        st.close();
    }
    private java.util.function.Consumer<Long> onSaved;
    public void setOnSaved(java.util.function.Consumer<Long> cb) { this.onSaved = cb; }
}
