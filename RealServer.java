import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;



public class RealServer {
    private ServerSocket server;
        /*
        ServerSocket = 서버를 가동해주는 객체
        : 클라이언트에서 들어오는 요청을 기다리는 ServerSocket을 구현하는 클래스
        */
    
    // 생성자
    public RealServer(){
         try {
            server = new ServerSocket(1234);
                /*
                사용자 접속 대기 스레드 가동
                -> 새로운 접속자가 들어오면, 그 이벤트를 처리하기 위한 스레드
                */
            ConnectionThread thread = new ConnectionThread();
            thread.start();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("ServerSocekt에러-" + e.getMessage());
        }
    }

    public static void main(String[] args){
        // JDBC 드라이버 가동
        try {
            Class.forName("com.mysql.jdbc.Driver");
            System.out.println("jdbc성공");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 간단히 다른 스레드만 호출하고, 메인스레드는 종료한다
        new RealServer();
    }

    // 사용자의 접속 대기를 처리하는 스레드 클래스
    class ConnectionThread extends Thread{
        // 전체룸
        Vector<Room> roomV;

        // 생성자
        public ConnectionThread(){
            // 방리스트 초기화
            roomV = new Vector<>();
        }

        // 실행코드
        public void run(){
            /*
             * 언제 들어올지 모르는 새로운 접속자를 위해 ConnectionThread는 종료되지 않고, 계속 무한루프를 돌면서 살아있다가, 
             *  -> server.accept()를 통해 새로운 접속자가 소켓에 접속하면
             *  -> 접속자의 정보를 받고 처리하는 스레드를 하나 더 분기하여 접속자 처리 담당
             */
            while(true){
                System.out.println("클라이언트를 기다리고 있습니다.");
                try {
                    Socket socket = server.accept();
                        /*
                            accept()메소드는 동기화에 걸렸다고 볼 수 있다.
                            -> 새로운 사용자가 들어오기 전까지 더이상 코드진행 없이 멈춰있다가,
                            새로운 접속자가 소켓에 접속하면 -> 접속자의 정보를 받고 처리하는 
                            스레드를 하나 더 분기하여 접속자 처리 담당
                        */
                    System.out.println("사용자가 접속하였습니다->" + socket.toString());

                    // 사용자 닉네임을 처리하는 스레드를 가동
                    NickNameThread thread = new NickNameThread(socket,this);
                    thread.start();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Socket에러-" + e.getMessage());
                }
            }
        }
    }

    /*
     * 닉네임 입력처리 스레드
     */
    class NickNameThread extends Thread{
        private Socket socket; // 클라이언트 소켓
        private ConnectionThread ct; // 자신의 방 찾기 위해
        private Room myRoom; // 나의 룸 셋팅

        // 생성자
        public NickNameThread(Socket socket, ConnectionThread ct){
            this.socket = socket;
            this.ct = ct;
        }

        public void run(){
            // 전달받은 소켓을 통해 I/O스트림(입력스트림)을 생성하여, 서로 데이터를 주고 받을 준비
            // 스트림 추출(소켓으로부터 얻어온다) -> 클라이언트로부터 메세지를 받고, 주기 위해
            try{
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();
                DataInputStream dis = new DataInputStream(is);
                DataOutputStream dos = new DataOutputStream(os);

                // 방 idx수신
                String room_idx = dis.readUTF();
                System.out.println("room_idx="+room_idx);

                // 방생성하기(단, 기존의 방리스트에 동일값이 없을 때만)
                Room room = new Room(room_idx);

                // 방 중복 확인
                boolean isDuplicate = ct.roomV.stream()
                    .anyMatch(r -> r.room_idx.equals(room.room_idx));
                if (isDuplicate) {
                    System.out.println("중복된 방idx존재");
                }else{
                    System.out.println("새로운방"+room_idx+"추가");
                    ct.roomV.add(room);
                }

                /*
                 * 사용자 정보 수신받기
                 */
                String jsonString = dis.readUTF();
                JSONObject jsonObject = (JSONObject) new JSONParser().parse(jsonString);

                String email = (String) jsonObject.get("email");
                String nickname = (String) jsonObject.get("nickname");
                String profile_url = (String) jsonObject.get("profile_url");

                System.out.println("email->"+email);
                System.out.println("nickname->"+nickname);
                System.out.println("profile_url->"+profile_url);
                

                /*
                 * 사용자 정보를 관리하는 객체를 생성
                 */
                UserClass user = new UserClass(email,nickname,profile_url,socket);
                
                /*
                 * 방에 사용자 넣기
                 */
                // Room idx를 키로, Room 객체를 값으로 갖는 HashMap을 생성합니다.
                HashMap<String, Room> roomMap = new HashMap<String, Room>();
                for(Room r : ct.roomV){
                    roomMap.put(r.room_idx, r);
                }

                // 클라이언트로부터 가져온 방 idx값을 키로 사용해 해당 방을 찾습니다.
                myRoom = roomMap.get(room_idx);

                // 해당 룸을 나의 룸으로 설정합니다.
                if (myRoom != null) {
                    user.myRoom = myRoom;
                    // 해당방에 사용자를 넣습니다.
                    myRoom.userV.add(user);
                }

                // UserClass스레드 시작
                user.start();

            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * 대화방의 정보표현 객체
     */
    public class Room{
        String room_idx; // 채팅방 idx
        int count; // 방인원수
        // 같은 방에 접속한 Client 정보 저장
        Vector<UserClass> userV;

        // 생성자
        public Room(String room_idx){
            userV = new Vector<>(); // 벡터초기화
            this.room_idx = room_idx;
        }


    }

    /*
     * 사용자 정보를 관리하는 클래스
     *  - 메세지를 지속적으로 받을 수 있다
     */
    class UserClass extends Thread{
        Room myRoom; //나의 룸
        String email; // 회원 식별
        String nickname; // 닉네임
        String profile_url;// 프로필 사진
        Socket socket; // 클라이언트 소켓
        DataInputStream dis; // 데이터 받는용
        DataOutputStream dos; // 데이터 주는 용

        // 파일전송용
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        // 생성자
        public UserClass(String email, String nickname, String profile_url, Socket socket){
            this.email = email;
            this.nickname = nickname;
            this.profile_url = profile_url;
            this.socket = socket;

            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();
                dis = new DataInputStream(is);
                dos = new DataOutputStream(os);
            } catch (IOException e) {
                System.out.println("in UserClass 생성자"+e.getMessage());
                e.printStackTrace();
            }
        }

        // 사용자로부터 메세지를 계속 수신받는 코드(해당 접속자에게 메세지를 계속 수신받기 위해 while문 안에서 작동함)
        public void run(){
            boolean run = true;
                // while문 작동 조정용 (소켓이 끊어지면 false로 작동을 멈춤(그렇지 않으면 오류 발생함))
            
            // 날짜
            String dayBefore = "";
            String dayToday = "";

            try{
                while(run){
                    // 클라이언트에게 메세지를 수신받는다
                    String msgSort = dis.readUTF();
                    System.out.println("msgSort="+msgSort);

                    /*
                     * 날짜비교
                     */
                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                    LocalDate today = LocalDate.now();
                    dayToday = today.format(dateFormatter);

                    if (!dayToday.equals(dayBefore)) {
                        Send_day_data(myRoom);
                        dayBefore = dayToday;
                    }

                    LocalDateTime currentTime = LocalDateTime.now();
                    String time1 = currentTime.format(dateTimeFormatter);

                    /*
                     * 메세지 분기해서 처리
                     */
                    switch (msgSort) {
                        case "message": // 일반 메세지
                            String content = dis.readUTF();
                            System.out.println(nickname+":"+content);

                            // 데이터베이스 저장할 내용
                            Map<String,String> map = new HashMap<>();
                            map.put("room_idx",String.valueOf(myRoom.room_idx));  // room_idx
                            map.put("sort","message");
                            map.put("content",content);
                            map.put("email",email);
                            map.put("date",time1);
                            map.put("order_tag", "0");
                            map.put("nickname", this.nickname);
                            map.put("profile_url", this.profile_url);
                            int idx = insert(map);

                            // 사용자들에게 메세지를 전달한다
                            map.put("idx",String.valueOf(idx));
                            sendToClient(map, myRoom);
                            
                        break;


                        case "file": // 파일
                            // 전송된 파일을 서버에 저장하고, 서버에 저장된 파일의 주소를 리턴해줌
                            String result = fileWrite();
                            // 이미지 주소 출력
                            System.out.println("result : " + result);

                            // 데이터 베이스 저장장
                            Map<String,String> map2 = new HashMap<>();
                            map2.put("room_idx",String.valueOf(myRoom.room_idx));  // room_idx
                            map2.put("sort","file");
                            map2.put("content",result);
                            map2.put("email",email);
                            map2.put("date",time1);
                            map2.put("order_tag", "0");
                            map2.put("nickname", this.nickname);
                            map2.put("profile_url", this.profile_url);
                            int idx2 = insert(map2);
                            
                        break;


                        case "files": // 다중파일
                            // 파일 순서를 받는다
                            String orderTag = dis.readUTF();
                            System.out.println("orderTag : " + orderTag);

                            // 전송 된 파일을 서버에 저장하고, 서버에 저장 된 파일의 주소를 리턴해줌
                            String result2 = fileWrite();
                            // 이미지 주소 출력
                            System.out.println("result2 : " + result2);
                            // 이미지 주소 변경
                            result2 = result2.replace("/var/www/books/html","");

                            /*
                             * 데이터 저장 후 정보 보내줌
                             */
                            Map<String,String> mapforFiles = new HashMap<>();
                            mapforFiles.put("room_idx",String.valueOf(myRoom.room_idx));
                            mapforFiles.put("sort","files");
                            mapforFiles.put("content",result2);
                            mapforFiles.put("email",email);
                            mapforFiles.put("date",time1);
                            mapforFiles.put("order_tag", orderTag);
                            mapforFiles.put("nickname", this.nickname); // fcm알람용
                            mapforFiles.put("profile_url", this.profile_url); // fcm 알람용
                            int idxforFilesChattingDataSave = insert(mapforFiles);
                            mapforFiles.put("idx", String.valueOf(idxforFilesChattingDataSave)); 
                            sendToClient(mapforFiles, myRoom);
                        break;


                        default:
                            
                        break;
                    }
                }
            }catch(Exception e){
                System.out.println("(in UserClass)Exception type: " + e.getClass().getName());
                System.out.println("(in UserClass)Exception message: " + e.getMessage());
                e.printStackTrace();

                // close된 소켓을 myRoom에서 제거
                myRoom.userV.remove(this);

                // 소켓 input, output 모두 닫기
                try{
                    dis.close();
                    dos.close();
                    socket.close();
                    // 스레드 종료하기(메세지 받는)
                    run = false;
                }catch (Exception e1) {
                    e1.printStackTrace();
                    System.out.println("소켓 클로즈 에러-"+e1.getMessage());
                }
            }
        }

        /*
        * 전송된 파일을 쓰고, 서버에 저장
        */
        private String fileWrite(){ 
            String result = "";

            // 저장장소
            String filePath = "/var/www/books/html/Img_Chatting";
            try{
                System.out.println("파일 수신 작업을 시작합니다(파일명->파일크기->파일)");

                // 파일명 전송 받기
                String fileNmae = dis.readUTF();
                System.out.println("파일명 " + fileNmae + "을 전송받았습니다.");

                // 파일 크기 전송 받기
                long fileSize = Long.parseLong(dis.readUTF());
                    // string형태이기 때문에 long으로 변환
                System.out.println("받은 파일사이즈 =" + fileSize);   

                // 파일을 생성하고 파일에 대한 출력 스트림을 생성
                File file = new File(filePath+"/"+fileNmae); // 파일생성
                fos = new FileOutputStream(file); // 파일읽기
                bos = new BufferedOutputStream(fos); // 파일을 더 효율적으로 읽기 위해서( FileOutputStream에 대한 버퍼링된 입출력을 제공)
                // 바이트 데이터를 전송받으면서 기록한다
                int readed = 0;
                byte[] b = new byte[10000];
                while(true){
                    readed = dis.read(b); // 데이터 읽기
                    // 파일에 쓰기
                    bos.write(b,0,readed); // 읽은 데이터를 파일에 쓰기
                    fileSize-=readed;

                    // 0이 되면 빠져나오기(-1까지 가게 되면 socket.closed가 됨)
                    if(fileSize==0) break;
                }
                result = "SUCCESS";
                System.out.println("파일 수신 작업을 완료하였습니다.");

                // 주소출력
                result = file.getAbsolutePath();


            }catch (IOException e) {
                    e.printStackTrace();
                    result = "ERROR";
                    System.out.println("파일받기 에러"+e.getMessage());
            }finally{
                try { bos.close(); } catch (IOException e) { e.printStackTrace(); }
                try { fos.close(); } catch (IOException e) { e.printStackTrace(); }
            }

            return result;
        }


    }



    /*
    날짜 전송(해당 날짜가 해당 방에 처음있는경우 -> room_idx+date(날짜까지만)가 유일한 경우)
    -> 날짜정보(sort=notice)를 전달한다(해당 방에 있는 사용자들에게)
    */
    private void Send_day_data(Room myRoom) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();
        String toSavetime = today.format(dateFormatter);

        /*
         * 찾는 값이 있는지 확인하고, 없으면 데이터베이스에 넣는다
         */
        String url = "jdbc:mysql://localhost/bookapp?useSSL=false";
        String sql = "SELECT date FROM Chatting WHERE room_idx=? AND left(date,10)=?";
        boolean found;

        try (Connection conn = DriverManager.getConnection(url, "superjg33", "41Asui!@");
            PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, Integer.parseInt(myRoom.room_idx)); 
            pstmt.setString(2, toSavetime);

            try (ResultSet rs = pstmt.executeQuery()) {
                found = rs.next();
                if (found) {
                    System.out.println("해당날짜에 채팅 데이터 있음");
                } else {
                    System.out.println("해당날짜에 채팅 데이터 없음");

                    Map<String,String> map = new HashMap<>();
                    map.put("room_idx",String.valueOf(myRoom.room_idx)); 
                    map.put("sort","notice");
                    map.put("content",toSavetime);
                    map.put("time",toSavetime);
                    map.put("order_tag","0");

                    int idx = insert(map); // 데이터베이스에 채팅 내용 삽입하고 idx값 가져오기
                    map.put("idx",String.valueOf(idx)); // idx값도 map에 삽입
                    sendToClient(map,myRoom); // 최종 map 클라이언트들에게 전달
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("에러!");
        }


    }

    /*
     * 메세지를 전달하는 메소드
     */
    public synchronized void sendToClient(Map<String, String> map, Room room) {
        // map -> 전달할 메세지

        String jsonString = new JSONObject(map).toJSONString();
        System.out.println("(전달메세지)jsonObject => " + jsonString);
    
        room.userV.forEach(user -> {
            try {
                user.dos.writeUTF(jsonString);
                user.dos.flush();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("in sendToClient: " + e.getMessage());
            }
        });
    }

    /*
     * 데이터베이스에 채팅 내용 삽입
     */
    private int insert(Map<String,String> map) {
        System.out.println("insert 함수");
    
        int idx = 0;
        String url = "jdbc:mysql://localhost/bookapp?useSSL=false&characterEncoding=UTF-8";
    
        // SQL 쿼리 준비
        String sql = "INSERT INTO Chatting(room_idx,sort,email,content,date,order_tag) VALUES(?,?,?,?,?,?)";
    
        try (Connection conn = DriverManager.getConnection(url, "superjg33", "41Asui!@");
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    // Statement.RETURN_GENERATED_KEYS -> 생성된 키를 포함하는 ResultSet얻기
    
            // 데이터 바인딩
            pstmt.setInt(1,Integer.parseInt(map.get("room_idx")));
            pstmt.setString(2,map.get("sort"));
            pstmt.setString(3,map.get("email"));
            pstmt.setString(4,map.get("content"));
            pstmt.setString(5,map.get("date"));
            pstmt.setInt(6,Integer.parseInt(map.get("order_tag")));
            System.out.println("데이터 바인딩 성공");
    
            // 쿼리 실행 및 결과 처리
            int count = pstmt.executeUpdate();
            if (count == 0) {
                System.out.println("데이터 입력 실패");
            } else {
                System.out.println("데이터 입력 성공");
    
                // idx 값 가져오기
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        idx = rs.getInt(1); // 첫 번째 컬럼이 idx 값임
                    }
                }
                System.out.println("idx=" + idx);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("데이터베이스 작업 에러 => " + e.getMessage());
        }
        return idx;
    }

}
