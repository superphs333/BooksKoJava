import java.net.ServerSocket;

public class RearServer {
    private ServerSocket server;
        /*
        ServerSocket = 서버를 가동해주는 객체
        : 클라이언트에서 들어오는 요청을 기다리는 ServerSocket을 구현하는 클래스
        */
    
    // 생성자
    public RearServer(){
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

}
