package com.share.dairy.ui;

import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.IOException;

/**
 * 공용 사이드바 아이템(커스텀 컨트롤)
 * - fx:root 패턴 사용: SidebarItem.fxml의 루트는 <fx:root type="StackPane">
 * - 여기서 fxml.setRoot(this) + fxml.setController(this)로 로드
 * - 제공 속성: text, icon, selected, target, onAction
 */
public class SidebarItem extends StackPane {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    // FXML-injected nodes
    @FXML private ImageView iconView;
    @FXML private Label textLabel;

    // Properties
    private final StringProperty text = new SimpleStringProperty(this, "text", "");
    private final ObjectProperty<Image> icon = new SimpleObjectProperty<>(this, "icon");
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected", false);
    private final StringProperty target = new SimpleStringProperty(this, "target", "");
    private final ObjectProperty<EventHandler<ActionEvent>> onAction =
            new SimpleObjectProperty<>(this, "onAction");

    public SidebarItem() {
        // SidebarItem.fxml 로드 (fx:root 패턴)
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("/fxml/SidebarItem.fxml"));
        fxml.setRoot(this);         // fx:root와 짝
        fxml.setController(this);   // fx:controller 대신 여기서 지정
        try {
            fxml.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 속성 바인딩
        textLabel.textProperty().bind(text);
        iconView.imageProperty().bind(icon);

        // 선택 상태를 CSS pseudo-class(:selected)에 반영
        selected.addListener((obs, oldV, sel) -> pseudoClassStateChanged(SELECTED, sel));

        // 클릭 시 onAction 핸들러 호출 (필요 없으면 외부에서 핸들러 안 주면 됨)
        setOnMouseClicked(e -> {
            var h = getOnAction();
            if (h != null) h.handle(new ActionEvent(this, null));
        });
    }

    // --- Properties getters/setters ---
    public String getText() { return text.get(); }
    public void setText(String t) { text.set(t); }
    public StringProperty textProperty() { return text; }

    public Image getIcon() { return icon.get(); }
    public void setIcon(Image i) { icon.set(i); }
    public ObjectProperty<Image> iconProperty() { return icon; }

    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean s) { selected.set(s); }
    public BooleanProperty selectedProperty() { return selected; }

    /** 네비게이션 목적지 식별자 (예: "MY_INFO", "ADD_FRIENDS" 등) */
    public String getTarget() { return target.get(); }
    public void setTarget(String t) { target.set(t); }
    public StringProperty targetProperty() { return target; }

    public EventHandler<ActionEvent> getOnAction() { return onAction.get(); }
    public void setOnAction(EventHandler<ActionEvent> h) { onAction.set(h); }
    public ObjectProperty<EventHandler<ActionEvent>> onActionProperty() { return onAction; }
}
