import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** @author Jörg Vogt */

/*
 * Remarks: UDP-checksum calculation, UDP-Lite RFC 3828
 * UDP checksum is calculated over IP-Pseudo-Header, UDP-Header, UDP-Data
 * No option to disable checksum in JAVA for UDP
 * UDP-Lite is part of Linux-kernel since 2.6.20
 * UDP-Lite support in java not clear
 */

public class FileCopy {
  static int port;
  static int delay;
  static double loss;
  static String dir = "/home/clemensd/Public/";

  public static void main(String[] args) throws IOException {
    Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    CustomLoggingHandler.prepareLogger(logger);
    /* set logging level
     * Level.CONFIG: default information (incl. RTSP requests)
     * Level.ALL: debugging information (headers, received packages and so on)
     */
    logger.setLevel(Level.CONFIG);
    logger.setLevel(Level.ALL);


    if (args.length != 2 && args.length !=4 && args.length !=5) {
      System.out.println("Usage: FileCopy server port [loss] [delay] ");
      System.out.println("Usage: FileCopy client host port file protocol");
      System.exit(1);
    }

    switch (args[0]) {
      case "client":
        String host = args[1];
        port = Integer.parseInt(args[2]);
        String fileName = args[3];
        String arqProt;
        if(args.length == 4) {
          arqProt = "1";
        } else {
          arqProt = args[4];
        }
        System.out.println("Client started for connection to: " + host + " at port " + port);
        System.out.println("Protokoll: " + arqProt);
        sendFile(host, port, fileName, arqProt);
        break;

      case "server":
        port = Integer.parseInt(args[1]);
        if (args.length == 4) {
          loss = Double.parseDouble(args[2]);
          delay = Integer.parseInt(args[3]);
        }
        System.out.println("Server started at port: " + port);
        handleConnection(port);
        break;

      default:
        System.out.println("No specification as to whether the programme should be used for the client or server.");
        System.exit(2);
    }
  }

  private static void sendFile(String host, int port, String fileName, String arq) throws IOException {
    Socket socket = new Socket(host, port);
    FileTransfer myFT = new FileTransfer(host, socket, fileName, arq);
    if (myFT.file_req() == true) {
      System.out.println("The client has successfully sent a file.");
    }
    else {
      System.out.println("The client has cancelled the file transfer due to the maximum retransmissions.");
    }
    System.exit(1);
  }

  private static void handleConnection(int port) throws IOException {
    Socket socket = new Socket(port, loss, delay);
    FileTransfer myFT = new FileTransfer(socket, dir);
    do{
        System.out.println("The server is waiting for a connection request.");
        if (myFT.file_init() == true){
          System.out.println("The server has successfully received a file.");
        }
        else {
          System.out.println("The server has cancelled the file transfer due to a timeout or a problem with the checksum.");
        }
    }while (true);
  }
}
