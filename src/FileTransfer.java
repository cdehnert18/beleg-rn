//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

public class FileTransfer implements FT {
    private ARQ myARQ;
    private String host;
    private Socket socket;
    private String fileName;
    private String downloaddir;
    private Logger logger;
    String dir;

    public FileTransfer(String host, Socket socket, String fileName, String arq) {
        this.socket = socket;
        this.fileName = fileName;
        this.host = host;
        if(arq.equals("1")) {
            this.myARQ = new SW(socket, (short) new Random().nextInt(Short.MAX_VALUE + 1));
        } else {
            this.myARQ = new GBN(socket, (short) new Random().nextInt(Short.MAX_VALUE + 1));
        }
        
        this.logger = Logger.getLogger("global");
        this.logger.log(Level.FINER, "Client-FT:" + arq + " new session ID: 1");
    }

    public FileTransfer(Socket socket, String dir) {
        this.myARQ = new SW(socket);
        this.logger = Logger.getLogger("global");
        this.logger.log(Level.FINEST, "Server-FT Constructor");
        this.dir = dir;
    }

    public boolean file_req() throws IOException {
        File file = new File(this.fileName);
        if (file.exists() && file.isFile()) {
            ByteArrayOutputStream startPackage = new ByteArrayOutputStream();
            startPackage.write("Start".getBytes());
            byte[] dataLength = new byte[8];
            long fileSize = file.length();

            for(int i = 7; i >= 0; --i) {
                dataLength[i] = (byte)((int)(fileSize & 255L));
                fileSize >>= 8;
            }

            startPackage.write(dataLength);
            byte[] dataNameLength = new byte[2];
            String dataFileName = file.getName();
            if (dataFileName.length() > 255) {
                dataFileName = dataFileName.substring(0, 255);
            }

            long fileNameSize = (long)dataFileName.length();

            for(int i = 1; i >= 0; --i) {
                dataNameLength[i] = (byte)((int)(fileNameSize & 255L));
                fileNameSize >>= 8;
            }

            startPackage.write(dataNameLength);
            String dataName = file.getName();
            if (dataName.length() > 255) {
                dataName = dataName.substring(0, 255);
            }

            startPackage.write(dataName.getBytes());
            ByteArrayOutputStream crc32Stream = new ByteArrayOutputStream();
            crc32Stream.write("Start".getBytes());
            crc32Stream.write(dataLength);
            crc32Stream.write(dataNameLength);
            crc32Stream.write(dataName.getBytes());
            CRC32 x = new CRC32();
            x.update(crc32Stream.toByteArray());
            byte[] dataCRC32 = new byte[4]; 
            long l = x.getValue();

            for(int i = 3; i >= 0; --i) {
                dataCRC32[i] = (byte)((int)(l & 255L));
                l >>= 8;
            }

            startPackage.write(dataCRC32);
            long timeA = System.currentTimeMillis();
            boolean serverAck = this.myARQ.data_req(startPackage.toByteArray(), startPackage.toByteArray().length, false);
            if (!serverAck) {
                System.out.println("The client was not able to send the start packet.");
                return false;
            } else {
                FileInputStream fileInputStream = new FileInputStream(file);

                while(fileInputStream.available() > 0) {
                    byte[] b;
                    if (fileInputStream.available() > 1400) {
                        b = new byte[1400];
                    } else {
                        b = new byte[fileInputStream.available()];
                    }

                    fileInputStream.read(b);
                    serverAck = this.myARQ.data_req(b, b.length, false);
                    if (!serverAck) {
                        System.out.println("The client was not able to send a packet.");
                        return false;
                    }
                }

                CRC32 z = new CRC32();
                z.update((new FileInputStream(file)).readAllBytes());
                byte[] fileCRC32 = new byte[4];
                long d = z.getValue();

                for(int i = 3; i >= 0; --i) {
                    fileCRC32[i] = (byte)((int)(d & 255L));
                    d >>= 8;
                }

                serverAck = this.myARQ.data_req(fileCRC32, fileCRC32.length, true);
                if (!serverAck) {
                    System.out.println("The client was not able to send the last packet (CRC32).");
                    return false;
                } else {
                    int fourBytesIJustRead = this.myARQ.getBackData();
                    long fileCRC32fromServer = fourBytesIJustRead & 0xffffffffl;
                    if (fileCRC32fromServer != z.getValue()) {
                        System.out.println("Client: FileACK false");
                        System.out.println("Provided: " + fileCRC32fromServer);
                        System.out.println("Correct: " + z.getValue());
                        return false;
                    } else {
                        long timeB = System.currentTimeMillis();
                        float Datenrate = file.length()*8 / (timeB - timeA);
                        System.out.println("Datenrate gesamt: " + Datenrate + " Kbit/s");
                        myARQ.closeConnection();
                        return true;
                    }
                }
            }
        } else {
            throw new IOException("Datei konnte nicht gefunden werden");
        }
    }

    public boolean file_init() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        ByteArrayInputStream byteArrayInputStream;
        
        try {
            byteArrayInputStream = new ByteArrayInputStream(myARQ.data_ind_req());
        } catch (TimeoutException e) {
            System.out.println("Server could not create the inputstream.");
            throw new RuntimeException(e);
        }

        // Lese Startpaket
        byte[] bytes = new byte[5];
        byteArrayInputStream.read(bytes);
        byteArrayOutputStream.write(bytes);
        bytes = new byte[8];
        byteArrayInputStream.read(bytes);
        byteArrayOutputStream.write(bytes);
        long reconstructedDataLength = 0L;

        for(int i = 0; i < 8; ++i) {
            reconstructedDataLength <<= 8;
            reconstructedDataLength |= (long)(bytes[i] & 255);
        }

        bytes = new byte[2];
        byteArrayInputStream.read(bytes);
        byteArrayOutputStream.write(bytes);
        long reconstructedDataNameLength = 0L;

        for(int i = 0; i < 2; ++i) {
            reconstructedDataNameLength <<= 8;
            reconstructedDataNameLength |= (long)(bytes[i] & 255);
        }

        bytes = new byte[(int)reconstructedDataNameLength];
        StringBuilder stringBuilder = new StringBuilder();

        for(int i = 0; (long)i < reconstructedDataNameLength; ++i) {
            bytes = new byte[1];
            byteArrayInputStream.read(bytes);
            byteArrayOutputStream.write(bytes);
            stringBuilder.append((char)bytes[0]);
        }

        String reconstructedDataName = stringBuilder.toString();
        CRC32 x = new CRC32();
        x.update(byteArrayOutputStream.toByteArray());
        long calculatedCRC32 = x.getValue();
        bytes = new byte[4];
        byteArrayInputStream.read(bytes);
        long reconstructedCRC32 = 0L;

        for(int i = 0; i < 4; ++i) {
            reconstructedCRC32 <<= 8;
            reconstructedCRC32 |= (long)(bytes[i] & 255);
        }

        if (reconstructedCRC32 != calculatedCRC32) {
            System.out.println("CRC32: Fail - Transmission ignored");
            System.out.println("Calculated CRC32: " + calculatedCRC32);
            System.out.println("Provided CRC32: " + reconstructedCRC32);
            return false;
        } else {
            // Erstelle Datei
            String fileName = this.dir + "/" + reconstructedDataName;
            File f = new File(fileName);
            String extension = "";
            String name = "";
            int i = fileName.lastIndexOf(46);
            if (i >= 0) {
                extension = fileName.substring(i + 1);
                name = fileName.substring(0, i);
            }

            String fileNameInc;
            for(int inc = 1; f.exists(); f = new File(fileNameInc)) {
                fileNameInc = name + inc + "." + extension;
                ++inc;
            }

            System.out.println("Server starts receiving data.");
            // Empfange Daten
            FileOutputStream fileOutputStream = new FileOutputStream(f);
            while(f.length() != reconstructedDataLength) {
                try {
                    byteArrayInputStream = new ByteArrayInputStream(myARQ.data_ind_req());
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
                fileOutputStream.write(byteArrayInputStream.readAllBytes());
            }

            CRC32 y = new CRC32();
            y.update((new FileInputStream(f)).readAllBytes());

            // Empfange Schlusspaket
            try {
                while(byteArrayInputStream.available() == 0){
                    byteArrayInputStream = new ByteArrayInputStream(myARQ.data_ind_req((int) y.getValue()));
                }
                System.out.println("Letztes Paket erhalten");
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }

            // Überprüfe FileCRC32
            bytes = new byte[4];
            byteArrayInputStream.read(bytes);
            long fileCRC32 = 0L;

            for(int m = 0; m < 4; ++m) {
                fileCRC32 <<= 8;
                fileCRC32 |= (long)(bytes[m] & 255);
            }

            if (fileCRC32 != y.getValue()) {
                System.out.println("Server: FileACK false");
                return false;
            } else {
                fileOutputStream.close();
                this.myARQ.closeConnection();
                return true;
            }
        }
    }
}
