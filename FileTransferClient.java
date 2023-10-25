
import java.io.*;
import java.math.RoundingMode;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileTransferClient extends Socket {

    private static final String SERVER_IP = "";
    private static final String Upload = "Upload";
    private static final int SERVER_PORT = 8899;
    private static DecimalFormat df = null;
    static {
        df = new DecimalFormat("#0.0");
        df.setRoundingMode(RoundingMode.HALF_UP);
        df.setMinimumFractionDigits(1);
        df.setMaximumFractionDigits(1);
    }
    private Socket client;
    List<Task_Upload> uploadList=new CopyOnWriteArrayList<>();;
    List<Task_Download> downloadList=new CopyOnWriteArrayList<>();;
    public FileTransferClient() throws Exception {
        super(SERVER_IP, SERVER_PORT);
        this.client = this;
    }
    public void stop(){
        System.out.println("Resume which thread?  (upload/download)");
        Scanner input = new Scanner(System.in);
        String op = input.next();
        if(op.equals("upload")) {
            System.out.println("Input the pid");
            int pid = input.nextInt();
            uploadList.get(pid).setStop(true);
        }
        else if(op.equals("download")) {
            System.out.println("Input the pid");
            int pid = input.nextInt();
            downloadList.get(pid).setStop(true);
        }
        else{
            System.out.println("invalid command!");
        }
    }
    public void resume(){
        System.out.println("Resume which thread?  (upload/download)");
        Scanner input = new Scanner(System.in);
        String op = input.next();
        if(op.equals("upload")) {
            System.out.println("Input the pid");
            int pid = input.nextInt();
            uploadList.get(pid).setStop(false);
        }
        else if(op.equals("download")) {
            System.out.println("Input the pid");
            int pid = input.nextInt();
            downloadList.get(pid).setStop(false);
        }
        else{
            System.out.println("invalid command!");
        }
    }
    public synchronized void uploadListCheckAdd(Task_Upload task_upload){
        if(uploadList.size()==0)
            uploadList.add(task_upload);
        else{
            for(int i=0;i<uploadList.size();++i){
                if(uploadList.get(i).getFinished())
                    uploadList.remove(uploadList.get(i));
            }
            uploadList.add(task_upload);
        }
    }
    public void chat(String command) throws Exception{
        try {

            if(command.equals("query")){
                System.out.println("Upload:");
                for(int i=0;i<uploadList.size();i++) {
                    if (uploadList.get(i).getFinished())
                        uploadList.remove(uploadList.get(i));
                }
                if(uploadList.size()==0){
                    System.out.println("No Thread in work");
                }
                else{
                    for(int i=0;i<uploadList.size();i++)
                        System.out.println("pid: "+i+" "+uploadList.get(i).getProcess());
                }
                System.out.println("Download:");
                for(int i=0;i<downloadList.size();i++) {
                    if (downloadList.get(i).getFinished())
                        downloadList.remove( downloadList.get(i));
                }
                if(downloadList.size()==0){
                    System.out.println("No Thread in work");
                }
                else{
                    for(int i=0;i<downloadList.size();i++)
                        System.out.println("pid: "+i+" "+ downloadList.get(i).getProcess());
                }
            }
            else if(command.equals("upload")){
                PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
                writer.println("upload");
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String respond = reader.readLine();
                if(respond.equals("OK")) {
                    File folder = new File(Upload);
                    if (folder.isDirectory()) {
                        File[] files = folder.listFiles();
                        assert files != null;
                        writer.println(files.length);
                        ExecutorService executor = Executors.newFixedThreadPool(files.length);
                        for (File file : files) {
                            if (file.isFile()) {
                                Socket socket = new FileTransferClient();
                                Task_Upload task_upload = new Task_Upload(socket, file.getName());
                                uploadListCheckAdd(task_upload);
                                executor.submit(task_upload);
                            }
                        }
                    }

                }
            }
            else if(command.equals("download")){
                PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
                writer.println("download");

                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String respond = reader.readLine();
                System.out.println(command);
                if(respond.equals("OK")) {
                    respond = reader.readLine();
                    if(Integer.parseInt(respond)==0){
                        System.out.println("Resources no files");
                    }
                    else{
                        int count = Integer.parseInt(respond);
                        ExecutorService executor = Executors.newFixedThreadPool(count);
                        for(int i=0;i<count;i++){
                            Socket socket=new FileTransferClient();
                            Task_Download task_download = new Task_Download(socket);
                            downloadList.add(task_download);
                            executor.submit(task_download);
                        }
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
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
            FileTransferClient client = new FileTransferClient();
            while(true) {
                System.out.println("Input Command:");
                Scanner in = new Scanner(System.in);
                String cur = in.next();
                if (cur.equals("quit")) return ;
                if (cur.equals("stop")) {
                    client.stop();
                }
                if (cur.equals("resume")) {
                    client.resume();
                }
                else {
                    client.chat(cur);
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    class Task_Upload implements Runnable {

        private Socket socket;

        private FileInputStream fis;
        private String URL;
        private String file_name;
        private DataOutputStream dos;
        private volatile boolean stop;
        private boolean finished;
        private long totalBytes;
        private long siz;
        public Task_Upload(Socket socket,String file_name) {
            this.socket = socket;
            this.file_name=file_name;
            this.totalBytes=0;
            this.URL=Upload+"//"+file_name;
            this.stop=false;
        }
        public boolean getFinished(){return this.finished;}

        public void setStop(boolean stop){this.stop=stop;}
        public String getProcess(){return file_name+"     "+totalBytes+"/"+siz;}
        @Override
        public void run() {
            try {
                System.out.println(URL);
                RandomAccessFile raf = new RandomAccessFile(URL, "r");
                byte[] bytes = new byte[1024];
                int length;
                raf.seek(totalBytes);
                fis = new FileInputStream(URL);
                dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF(file_name);
                dos.flush();
                dos.writeLong(raf.length());
                dos.flush();
                this.siz=raf.length();
                while ((length = fis.read(bytes, 0, bytes.length) )!= -1) {
                    dos.write(bytes, 0, length);
                    totalBytes+=length;
                    while (stop) {
                        Thread.onSpinWait();
                    }
                    dos.flush();
                }
                this.finished=true;
                System.out.println(file_name+" Upload Completely");

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    class Task_Download implements Runnable {

        private Socket socket;

        private FileOutputStream fos;
        private String file_name;
        private DataInputStream dis;
        private volatile boolean stop;
        private boolean finished;
        private long totalBytes;
        private long siz;

        public Task_Download(Socket socket) {
            this.socket = socket;
            this.stop = false;
            this.finished = false;
        }
        public boolean getFinished(){
            return this.finished;
        }
        public String getProcess(){
            return file_name+"     "+totalBytes+"/"+siz;
        }
        public void setStop(boolean stop){
            this.stop=stop;
        }
        @Override
        public void run() {
            try {
                dis = new DataInputStream(socket.getInputStream());
                String fileName = dis.readUTF();
                long fileLength = dis.readLong();
                File directory = new File("Download");
                this.siz=fileLength;
                if(!directory.exists()) {
                    directory.mkdir();
                }
                RandomAccessFile raf = new RandomAccessFile(directory.getAbsolutePath() + File.separatorChar + fileName,"rw");
                raf.seek(totalBytes);
                fos = new FileOutputStream(raf.getFD());
                this.file_name=fileName;
                // 开始接收文件
                byte[] bytes = new byte[1024];
                int length = 0;
                while((length = dis.read(bytes, 0, bytes.length)) != -1) {
                    fos.write(bytes, 0, length);
                    totalBytes+=length;
                    fos.flush();
                    while (stop) {
                        Thread.onSpinWait();
                    }
                    if(totalBytes==fileLength)break;
                }
                this.finished=true;
                System.out.println("======== Download [File Name：" + fileName + "] [Size：" + getFormatFileSize(fileLength) + "] ========");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if(fos != null)
                        fos.close();
                    if(dis != null)
                        dis.close();
                    socket.close();
                } catch (Exception e) {}
            }
        }
    }
}
