import java.io.*;
import java.math.RoundingMode;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;


public class FileTransferServer extends ServerSocket {

    private static final int SERVER_PORT = 8899; 
    private static final String Resources = "Resources";
    private static DecimalFormat df = null;

    static {
        df = new DecimalFormat("#0.0");
        df.setRoundingMode(RoundingMode.HALF_UP);
        df.setMinimumFractionDigits(1);
        df.setMaximumFractionDigits(1);
    }
    static int upload_cnt = 0;
    static int download_cnt = 0;
    public FileTransferServer() throws Exception {
        super(SERVER_PORT);
    }
    public void load() throws Exception {
        Socket socket = this.accept();
        while (true) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message = reader.readLine();
            System.out.println("Get message from client: " + message);
            if(message.equals("upload")) {
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println("OK");
                message = reader.readLine();
                int count = Integer.parseInt(message);
                for(int i=0;i<count;i++){
                    try {
                        Socket current_socket = this.accept();
                        synchronized (this){
                            upload_cnt++;
                        }
                        new Thread(new Task_Receive(current_socket)).start();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if(upload_cnt==0)
                    System.out.println("Receive Completed");
            }
            else if(message.equals("download")){
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println("OK");
                File folder = new File(Resources);
                File[] files = folder.listFiles();
                int count = files.length;
                System.out.println(count);
                writer.println(count);
                for(int i=0;i<files.length;i++){
                    try {
                        Socket current_socket = this.accept();
                        synchronized (this) {
                            download_cnt++;
                        }
                        new Thread(new Task_Upload(current_socket,files[i].getName())).start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if(download_cnt==0);
                    System.out.println("Upload Completed");
            }
        }
    }

    class Task_Receive implements Runnable {

        private Socket socket;
        private ServerSocket serverSocket;
        private FileOutputStream fos;
        private DataInputStream dis;
        private long totalBytes;

        public Task_Receive(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                dis = new DataInputStream(socket.getInputStream());
                // 文件名和长度
                String fileName = dis.readUTF();
                long fileLength = dis.readLong();
                File directory = new File("Storage");
                if(!directory.exists()) {
                    directory.mkdir();
                }
                RandomAccessFile raf = new RandomAccessFile(directory.getAbsolutePath() + File.separatorChar + fileName,"rw");
                raf.seek(totalBytes);
                fos = new FileOutputStream(raf.getFD());
                byte[] bytes = new byte[1024];
                int length = 0;
                while((length = dis.read(bytes, 0, bytes.length)) != -1) {
                    fos.write(bytes, 0, length);
                    totalBytes+=length;
                    fos.flush();
                    if(totalBytes==fileLength)break;
                }
                System.out.println("======== Received [File Name：" + fileName + "] [Size：" + getFormatFileSize(fileLength) + "] ========");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if(fos != null)
                        fos.close();
                    if(dis != null)
                        dis.close();
                    socket.close();
                    System.out.println("执行了么？");
                    synchronized (this) {
                        upload_cnt--;
                    }
                } catch (Exception e) {}
            }
        }
    }

    class Task_Upload implements Runnable{
        private Socket socket;
        private FileInputStream fis;
        private DataOutputStream dos;
        private long totalBytes;
        private String name;
        public Task_Upload(Socket socket,String name) {
            this.socket = socket;
            this.name = name;
        }
        @Override
        public void run() {
            try {
                String URL = Resources+"//"+name;
                RandomAccessFile raf = new RandomAccessFile(URL, "r");
                byte[] bytes = new byte[1024];
                int length;
                raf.seek(totalBytes);
                fis = new FileInputStream(URL);
                dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF(name);
                dos.flush();
                dos.writeLong(raf.length());
                dos.flush();
                while ((length = fis.read(bytes, 0, bytes.length) )!= -1) {
                    dos.write(bytes, 0, length);
                    totalBytes+=length;
                    dos.flush();
                }
                System.out.println(name+" Upload Completely");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                synchronized (this) {
                    download_cnt--;
                }
            }
        }
    }

    private String getFormatFileSize(long length) {
        double size = ((double) length) / (1 << 30);
        if(size >= 1) {
            return df.format(size) + "GB";
        }
        size = ((double) length) / (1 << 20);
        if(size >= 1) {
            return df.format(size) + "MB";
        }
        size = ((double) length) / (1 << 10);
        if(size >= 1) {
            return df.format(size) + "KB";
        }
        return length + "B";
    }
    public static void main(String[] args) {
        try {
            FileTransferServer server = new FileTransferServer(); 
            server.load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
