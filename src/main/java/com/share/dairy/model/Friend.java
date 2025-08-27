package com.share.dairy.model;

import javafx.scene.image.Image;

/** 최소 모델: 이름 + 아바타 이미지 */
public class Friend {
    private final String name;
    private final Image avatar;

    public Friend(String name, Image avatar) {
        this.name = name;
        this.avatar = avatar;
    }
    public String getName()  { return name; }
    public Image  getAvatar(){ return avatar; }
}
