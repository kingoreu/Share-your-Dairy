# Share-your-Dairy
AI융합 인지중재 실버케어 솔루션 개발자 교육과정 중 제작한 Java Project입니다. 
mainscreen 구현은 했는데 아직 장애가 좀 있다 사진 위치 수정 필요 화면 전환 할때 이미지 안보이게 하거나 창을 전환


----------
# DB 연결 (mysql 8.0.33 사용) 08.11
1. 스키마 이름 : dairy
2. port 번호는 각자 mysql 할당된 포트로 설정
3. root, password도 각자 설정한 것으로 properties 변경하여 연결
4. pom.xml에 의존성 추가
```
<dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>8.0.33</version>
</dependency>
```
5. 프로젝트와 데이터 저장소 연결 후, /src/sql 내부에 있는 **dairy.sql, dairy_trigger.sql문 Mysql Workbench에서 실행**
6. DBTest 파일을 통해 연결 확인

-----------
# Server 연결 (Spring 3.3.2 사용) 08.12
1. sohyun 브랜치 코드를 그대로 가져와서 연결해도 가능
2. 실행 시 루트 디렉토리에서 mvn clean javafx:run
3. maven reload도 해주면 좋음
4. pom.xml은 이미 추가되어 있지만 하단에 작성함. (parent, dependency, plugin)
5. **module-info.java 파일을 삭제**해야 스프링까지 연결될 거임 아마도.. (저는 삭제했어요)
```
<parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.2</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
// parent는 properties보다 상단에 위치해야 함.
...
        <!-- Spring Boot Web (REST API) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot JDBC -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <!-- JSON 처리 (Jackson) - spring-boot-starter-web에도 포함되어 있음 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Lombok (디버깅) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
...
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
```

----
# 프로젝트 공용 DB, 서버
1. docker-compose.yml 파일 내용
2. 실제로 만들 필요는 없습니다. 일반 유저 계정과 유저 비밀번호를 알고 계셔야 접속이 되므로 해당 부분을 확인하세요. 
   ```
   version: "3.8"
services:
  mysql:
    image: mysql:8.0
    container_name: dairy-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: 제 이름 영어로 치세용(아시죠?)   # root 계정 비밀번호
      MYSQL_DATABASE: dairy          # 기본 생성 DB 이름
      MYSQL_USER: dairyuser         # 일반 유저 계정
      MYSQL_PASSWORD: dairypass     # 유저 비밀번호
    ports:
      - "3306:3306"                  # 외부에서 접속할 포트
      - "3308:3306" 
    volumes:
      - ./mysql_data:/var/lib/mysql  # 데이터 영구 저장 (컨테이너 삭제해도 유지)
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
                                     # 초기 스키마/데이터 로드
   ```

root 계정 비밀번호는 관리자 권한이므로 public readme에 작성하지 않습니다.

3. cmd에서 mysql -u dairyuser -p -h 113.198.238.119 -P 3306 을 입력한다.
4. 비밀번호인 dairypass를 입력한다.
4-2. 관리자로 들어가고 싶으면 mysql -u root -p -h 113.198.238.119 -P 3306 을 입력한다. 마찬가지로 비밀번호를 입력한다.
5. use dairy;
show tables; 를 입력하여 내용을 확인한다.

6. application.properties의 내용을 변경한다.

- 테이블 수정, 변경 안하고 **일반 유저**로 접속할 경우
driver=com.mysql.cj.jdbc.Driver
url=jdbc:mysql://113.198.238.119:3306/dairy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
user=dairyuser
password=dairypass

- 테이블 변경이 필요해 **관리자**로 접속할 경우
driver=com.mysql.cj.jdbc.Driver
url=jdbc:mysql://113.198.238.119:3306/dairy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
user=root
password= 제 이름 영어로 바꾸세용.

7. HelloApplication의 db 관련 properties도 함께 변경하세요.
8. 현재 사용하고 있는 프로그램의 db 연결도 알아서 변경하시면 됩니다.
<img width="1614" height="1348" alt="image" src="https://github.com/user-attachments/assets/2608401a-0b05-46b2-ab13-9a6beb4af5ac" />
