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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.json.simple.*;

public class Server{
    /*
    ServerSocker = 서버를 가동해주는 객체
    : 클라이언트에서 들어오는 요청을 기다리는 ServerSocket을 구현하는 클래스
    */
    private ServerSocket server;

    // 생성자
    public Server(){
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


    } // 생성자 end

    // 실행코드
    public static void main(String[] args){

        // JDBC 드라이버 가동
        try {
            Class.forName("com.mysql.jdbc.Driver");
            System.out.println("jdbc성공");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("jdbc에러=>"+e.getMessage());
        }



         // 간단히 다른 스레드만 호출하고, 메인스레드는 종료
        new Server();

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
            언제 들어올지 모르는 새로운 접속자를 위해 ConnectionThread는 종료되지 않고, 
            계속 무한루프를 돌면서 살아있다가
            -> server.accept()를 통해 새로운 접속자가 소켓에 접속하면
            -> 접속자의 정보를 받고 처리하는 스레드를 하나 더 분기하여 접속자 처리 담당
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

                        /*
                        사용자 닉네임을 처리하는 스레드를 가동
                         */
                        NickNameThread thread
                        = new NickNameThread(socket,this);
                        thread.start();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Socket에러-" + e.getMessage());
                }
            } // end while


        } // end run
    }

    // 대화방의 정보표현 객체
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
    닉네임 입력처리 스레드
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

        // 실행코드
        public void run(){
            /*
            전달받은 소켓을 통해 I/O스트림(입력 스트림)을 생성하여, 서로 데이터를 주고 받을 준비
             */
            // 스트림 추출(소켓으로부터 얻어온다)
            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                    // 클라이언트로부터 메세지를 받고, 주기 위해
                        /*
                        DataInputStream, DataOutputStream
                        => FileInput/OutputStream을 상속하고 있고, 객체 생성시에 
                        InputStream, OutputStream을 매개변수 인자로 갖는다. 
                        => 이 클래스와 입출력 장치를 대상으로 하는 입출력 클래스를 
                        같이 이용하면 자바의 기본 자료형 데이를 파일 입출력 장치로 
                        직접 출력 할 수 있다.
                        */
                
                DataInputStream dis = new DataInputStream(is);
                DataOutputStream dos = new DataOutputStream(os);

                // 방 idx 수신
                String room_idx = dis.readUTF();
                System.out.println("room_idx="+room_idx);

                // 방생성하기
                    // 단, 기존의 방리스트에 동일값 없을때만
                Room room = new Room(room_idx);

                // 방 중복 확인
                int duplicate_count = 0;
                for(int i=0; i<ct.roomV.size(); i++){
                    String room_idx_temp = ct.roomV.get(i).room_idx;

                    // 위에 생성한 값과 같은 room_idx를 가지고 있다면, 중복값을 증가시킴
                    if(room_idx_temp.equals(room.room_idx)){
                        System.out.println("중복된 방idx존재");

                        // 중복값을 증가시킨다
                        duplicate_count++;
                    }
                } // end for
                if(duplicate_count==0){
                    System.out.println("새로운방 추가");
                    ct.roomV.add(room);
                }

                // email 수신
                String email = dis.readUTF();
                System.out.println("email=" + email);

                // nickname 수신
                String nickName = dis.readUTF();
                System.out.println("nickName=" + nickName);

                // profile_url 수신
                String profile_url = dis.readUTF();
                System.out.println("profile_url=" + profile_url);

                /*
                사용자 정보를 관리하는 객체를 생성
                */
                UserClass user = new UserClass(email, nickName, socket);
                user.profile_url = profile_url;

                /*
                방에 사용자 넣기
                */
                for(int i=0; i<ct.roomV.size(); i++){
                    Room r = ct.roomV.get(i);

                    /*
                    현재 인덱스에서 가리키는 있는 방의 idx값이 클라이언트로 부터 가져온 방 idx값과 같다면, 그 방에 넣어준다
                    */
                    if(r.room_idx.equals(room_idx)){
                        // 나의 룸으로 설정하기
                        myRoom = r;

                        // 해당 룸을 나의 룸으로 설정하기
                        user.myRoom = r;

                        // 끝내기
                        break;
                    }
                } // end for

                // 해당방에 사용자를 넣는다
                myRoom.userV.add(user);

                // UserClass 스레드 시작
                user.start();
                


            } catch (Exception e) {
                e.printStackTrace();


            }
        }
    } // end NickNameThread


    /*
    사용자 정보를 관리하는 클래스(하나하나 접속자 등록을 위해)
    -> 메세지를 지속적으로 받을 수 있다 
    */
    class UserClass extends Thread{
        Room myRoom; // 나의 룸
        String email; // 회원식별
        String nickname; // 닉네임
        String profile_url; // 프로필 사진
        Socket socket; // 클라이언트 소켓
        DataInputStream dis; // 데이터 받는용
        DataOutputStream dos; // 데이터 주는 용

        // 파일전송용
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        // 생성자
        public UserClass(String email,String nickname,Socket socket){
            this.email = email;
            this.nickname = nickname;
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

        /*
        사용자로부터 메세지를 계속 수신받는 코드
        -> 해당 접속자에게 메세지를 계속 수신받기 위해 while문 안에서 작동함
        */
        public void run(){
            /*
            while문 작동 조정 boolean
            -> 소켓이 끊어지면, false로 작동을 멈춰준다.
            (그렇지 않으면 오류가 발생함) 
            */
            boolean run = true;

            // 날짜
            String day_before = "";
            String day_today = "";

            try {
                while(run){
                    // 클라이언트에게 메세지를 수신받는다
                    String str = dis.readUTF();
                    System.out.println("메세지 sort="+str);

                    /*
                    날짜비교
                    */
                    SimpleDateFormat format_compare
                    = new SimpleDateFormat("yyyy-MM-dd");
                    Date time_compare = new Date();
                    String compare = format_compare.format(time_compare);
                    day_today = compare; // 오늘날짜

                    // 날짜(공통적으로 보냄)
                    SimpleDateFormat format
                    = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date time = new Date();
                    String time1 = format.format(time);

                    if(!day_today.equals(day_before)){ // 다를때 -> notice-날짜 보냄 + day_before=day_today
                        Send_day_data(myRoom);
                        day_before = day_today;
                    }

                    if(str.equals("message")){
                        // 일반 메세지
                        
                        String content = dis.readUTF();
                        System.out.println(nickname+":"+content);
                        
                        /*
                        데이터베이스에 내용 저장
                        */                    
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
                        sendToClient(map,myRoom);
                    }else if(str.equals("file")){
                        // 파일

                        // 전송된 파일을 서버에 저장하고, 서버에 저장된 파일의 주소를 리턴해줌
                        String result = fileWrite();

                        // 이미지 주소 출력
                        System.out.println("result : " + result);
                    }else if(str.equals("files")){
                        // 다중파일

                        /*
                        파일 순서를 받는다
                        */
                        String order_tag = dis.readUTF();

                        /*
                        전송 된 파일을 서버에 저장하고,
                        서버에 저장 된 파일의 주소를 리턴해준다
                        */
                        String result = fileWrite();        
                        System.out.println("result=>"+result);                         

                        // 이미지 주소 변경 및 출력
                        result = result.replace("/var/www/books/html","");
                        // https://books.dosymmm.ga
                        //System.out.println("result : " + result);

                    

                        /*
                        데이터 저장후 이미지 주소를 클라이언트에게 보내주기
                        */
                        // 데이터 저장
                        Map<String,String> map = new HashMap<>();
                        map.put("room_idx",String.valueOf(myRoom.room_idx));  // room_idx
                        map.put("sort","files");
                        map.put("content",result);
                        map.put("email",email);
                        map.put("date",time1);
                        map.put("order_tag", order_tag);
                        map.put("nickname", this.nickname);
                        map.put("profile_url", this.profile_url);
                        int idx = insert(map);
                        // 메세지 전달
                        map.put("idx",String.valueOf(idx));
                        sendToClient(map, myRoom);
                        
                    }
                }
            } catch (Exception e) {
                // 에러출력
                System.out.println("in UserClass="+e.getMessage());

                // close된 소켓을 myRoom에서 뺀다
                myRoom.userV.remove(this);

                // 소켓 input, output 모두 닫기
                try {
                    dis.close();
                    dos.close();
                    socket.close();

                    // 스레드 종료하기(메세제 받는)
                    run = false;
                } catch (Exception e1) {
                    e1.printStackTrace();
                    System.out.println("소켓 클로즈 에러-"+e1.getMessage());
                }
            }
        } // end run()

        // 전송된 파일을 쓰고, 서버에 저장하기
        private String fileWrite(){
            String result="";

            // 저장장소
            String file_Path = "/var/www/books/html/Img_Chatting";
            try {
                System.out.println("파일 수신 작업을 시작합니다(파일명->파일크기->파일)");

                // 파일명을 전송받기
                String file_Name = dis.readUTF();
                System.out.println("파일명 " + file_Name + "을 전송받았습니다.");

                // 파일 크기 전송 받기
                long file_Size = Long.parseLong(dis.readUTF());
                    // string형태이기 때문에 long으로 변환
                System.out.println("받은 파일사이즈 =" + file_Size); 

                // 파일을 생성하고 파일에 대한 출력 스트림을 생성
                File file = new File(file_Path+"/"+file_Name);
                fos = new FileOutputStream(file); // 파일읽기
                bos = new BufferedOutputStream(fos);
                    // 더 효율적으로 읽기 위해서

                // 바이트 데이터를 전송받으면서 기록
                int readed = 0;
                byte[] b = new byte[10000];
                while(true){
                    readed = dis.read(b);
                    // 파일에 쓰기
                    bos.write(b,0,readed);
                    file_Size-=readed;

                    // 0되면 빠져나오기
                    // (-1까지 가게 되면 socket.closed가 됨)
                    if(file_Size==0){
                        break;
                    }
                }

                result = "SUCCESS";
                System.out.println("파일 수신 작업을 완료하였습니다.");

                // 주소출력
                result = file.getAbsolutePath();
            } catch (IOException e) {
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
    -> 날짜정보(sort=notice)를 전달한다
    */
    public void Send_day_data(Room myRoom){
         // 날짜
         SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
         Date time = new Date();
         String time1 = format.format(time);
         // 찾는값 있는지 확인
         Connection conn = null;
         java.sql.Statement stmt = null;
         ResultSet rs = null;
         String url = "jdbc:mysql://localhost/bookapp?useSSL=false";
         try{
            conn = DriverManager.getConnection(url, "superjg33", "41Asui!@");

            // 쿼리를 날리는 statement
            stmt = conn.createStatement();
        }catch (SQLException e) {e.printStackTrace();}

        String sql = "SELECT date FROM Chatting WHERE room_idx="+myRoom.room_idx+" AND left(date,10)=\'"+time1+"\'";
        System.out.println(sql);
        try{
            rs = stmt.executeQuery(sql);
        }catch (SQLException e) {e.printStackTrace();}
        boolean found; // 해당값이 있는지 확인하기 위해
        try {
            found = rs.next();
            if(found){
                System.out.println("해당날짜에 채팅 데이터 있음");
            }else{
                System.out.println("해당날짜에 채팅 데이터 없음");
                Map<String,String> map = new HashMap<>();
                map.put("room_idx",String.valueOf(myRoom.room_idx)); 
                map.put("sort","notice");
                map.put("content",time1);
                map.put("time",time1);
                map.put("order_tag","0");
                int idx = insert(map);
                map.put("idx",String.valueOf(idx));
                sendToClient(map,myRoom);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("에러!");
        }finally{
            try {
                if(conn!=null && !conn.isClosed()){
                    conn.close();
                }
                if( stmt != null && !stmt.isClosed()){
                    stmt.close();
                }
                if( rs != null && !rs.isClosed()){
                    rs.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } // end finally

    }





    /*
    메세지를 전달하는 메소드
    */
    public synchronized void sendToClient(Map map, Room room){
        // map -> json
        JSONObject jsonObject = new JSONObject(map);
        System.out.println("jsonobject=>"+jsonObject.toJSONString());

        try {
            // 같은 방의 사용자들에게만 메세지를 전달해준다
            for(UserClass user : room.userV){
                user.dos.writeUTF(jsonObject.toJSONString());
                user.dos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("in sendToClient"+e.getMessage());
        }
    }


    /*
    데이터베이스에 채팅 내용 삽입
    - 반환 -> 저장된 값의 idx값
    */
    /**
     * @param map
     * @return
     */
    private int insert(Map<String,String> map){

        System.out.println("insert함수");

        Connection conn = null;
        // SQL문을 데이터베이스에 보내기 위한 객체
        PreparedStatement pstmt = null;
        int idx=0;

        // 연결하기
        String url = "jdbc:mysql://localhost/bookapp?useSSL=false";
        try {
            // Connection 객체 생성
            conn = DriverManager.getConnection(url, "superjg33", "41Asui!@");
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("DriverManager로딩 실패->"+e.getMessage());
        }

        // SQL 쿼리 준비
        String sql = "INSERT INTO Chatting(room_idx,sort,email,content,date,order_tag) VALUES(?,?,?,?,?,?)";
        try {
            pstmt = conn.prepareStatement(sql);
            System.out.println("pstmt 성공");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("pstmt 실패->"+e.getMessage());
        }

        // 데이터바인딩
        try{

            pstmt.setInt(1,Integer.parseInt(map.get("room_idx")));
            pstmt.setString(2,map.get("sort"));
            pstmt.setString(3,map.get("email"));
            pstmt.setString(4,map.get("content"));
            pstmt.setString(5,map.get("date"));
            pstmt.setInt(6,Integer.parseInt(map.get("order_tag")));
            System.out.println("데이터바인딩 성공");
        }catch(SQLException e){
            e.printStackTrace();
            System.out.println("데이터바인딩 에러=>"+e);
        }

        // 쿼리 실행 및 결과 처리
        try {
            int count = pstmt.executeUpdate();
            if(count==0){System.out.println("데이터 입력 실패");}else{
                System.out.println("데이터 입력 성공");

                /*
                idx값 가져오기
                */
                // 데이터베이스로 sql문을 보내기 위한 객체
                java.sql.Statement stmt 
                    = conn.createStatement();
                ResultSet rs = null;
                String sql2 = "select idx from Chatting ORDER BY idx DESC limit 1";
                rs = stmt.executeQuery(sql2);
                while(rs.next()){
                    idx = rs.getInt("idx");
                }
                System.out.println("idx="+idx);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("쿼리 실행 및 결과 처리 에러=>"+e);
        }

        return idx;
    }
}