import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GBN extends ARQAbst {
/* Unfertig */
    int failCount = 0;
    int RTO = 1000;
    float Datenrate = 0;
    int timer = 0;
    int n = 1;
    List<byte[]> dataPaketList = new ArrayList<>();

    public GBN(Socket socket) {
        super(socket);
    }

    public GBN(Socket socket, int sessionID) {
        super(socket, sessionID);
    }

    @Override
    public boolean data_req(byte[] hlData, int hlSize, boolean lastTransmission) {
        long timeA;
        long timeB;
        
        byte[] bytes = generateDataPacket(hlData, hlSize);

        // Befülle Liste    
        dataPaketList.add(bytes);

        if(lastTransmission == true) {
            backData = 1;
        }

        for(int i = 0; i < 11; i++){
            timeA = System.currentTimeMillis();
            socket.sendPacket(bytes);
            if(dataPaketList.size() < n) {
                return true;
            }
            boolean serverAck = waitForAck(pNr);
            timeB = System.currentTimeMillis();
            
            timer++;
            if(timer % 3 == 0) {
                System.out.println("Aktuelle Datenrate: " + Datenrate + " Kbit/s");
            }
            
            if(serverAck == true){
                long time = timeB - timeA;
                if(time == 0) {
                    time = 1;
                }
                Datenrate = (bytes.length*8) / (time);
                if(failCount < 5 && RTO > 100) {
                    RTO -= 100;
                }

                return true;
            } else {

                failCount++;
                Datenrate = Datenrate/2;
                
                if(failCount < 5 && RTO < 500){
                    RTO += 100;
                }
            }
        }

        return false;
    }

    @Override
    protected boolean waitForAck(int packetNr) {
        if (failCount < 5) {
            socket.setTimeout(RTO);
        } else {
            socket.setTimeout(RTO + 80);
        }
        DatagramPacket ackPacket;

        try {
            ackPacket = socket.receivePacket();
        } catch (TimeoutException e) {
            
            return false;
        }

        if(getSessionID(ackPacket) == sessionID && getPacketNr(ackPacket) == pNr){
            if(backData == 1) {
                getAckData(ackPacket);
            }
            pNr = (pNr + 1) % pNumbers;
            return true;
        } else {
            System.out.println("Received pNr: " + getPacketNr(ackPacket) + " Correct pNr: " + pNr);
            return false;
        }
    }

    @Override
    protected int getPacketNr(DatagramPacket packet) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(packet.getData());
        byte[] bb = new byte[1];
        int value = 0;
        try{
            byteArrayInputStream.read();
            byteArrayInputStream.read();
            byteArrayInputStream.read(bb);
            for (byte b : bb) {
                value = (value << 8) + (b & 0xFF);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return value;
    }

    @Override
    protected void getAckData(DatagramPacket packet) {
        //gbn
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(packet.getData());
        byte[] bytes = new byte[4];
        for(int i = 0; i < 3; i++) {
            byteArrayInputStream.read();
        }
        this.n = byteArrayInputStream.read();
        try {
            byteArrayInputStream.read(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        long reconstructedCRC32 = 0;
        for (int i = 0; i < 4; i++) {
            reconstructedCRC32 <<= 8;
            reconstructedCRC32 |= (bytes[i] & 0xFF);
        }
        backData = (int)reconstructedCRC32;
    }

    @Override
    protected int getSessionID(DatagramPacket packet) {
        int sID;
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(packet.getData());
        int firstByte = byteArrayInputStream.read();
        int secondByte = byteArrayInputStream.read();
        sID = (firstByte << 8) | secondByte;

        return sID;
    }

    @Override
    protected byte[] generateDataPacket(byte[] sendData, int dataSize) {
        ByteArrayOutputStream packageWithHeader = new ByteArrayOutputStream();
        packageWithHeader.write((sessionID >> 8) & 0xFF);
        packageWithHeader.write(sessionID & 0xFF);
        packageWithHeader.write((byte) pNr);
        try {
            packageWithHeader.write(sendData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return packageWithHeader.toByteArray();
    }

    //**SERVER**

    @Override
    public byte[] data_ind_req(int... values) throws TimeoutException {
        DatagramPacket dataPacket;
        dataPacket = socket.receivePacket();

        if(checkStart(dataPacket) == true && sessionID == 0) {
            sessionID = getSessionID(dataPacket);
            pNr = getPacketNr(dataPacket);
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        if(getPacketNr(dataPacket) != pNr){
            sendAck(getPacketNr(dataPacket));
            System.out.println("Received pNr: " + getPacketNr(dataPacket) + " Correct pNr: " + pNr);
            return byteArrayOutputStream.toByteArray();
        }

        for(int i = 3; i < dataPacket.getLength(); i++){
            byteArrayOutputStream.write(dataPacket.getData()[i]);
        }

        if(values.length == 0) {
            sendAck(pNr);
        }
        //Server schreibt sein CRC32 und sendet es
        if(values.length == 1) {
            this.backData = values[0];
            sendAck(pNr);

            //Warte noch kurz
            socket.setTimeout(3000);
            long startTime = System.currentTimeMillis();
            long endTime = startTime + 3000;
            while (System.currentTimeMillis() < endTime) {
                try {
                    System.out.println("Waiting before closing");
                    dataPacket = socket.receivePacket();
                } catch (TimeoutException e) {
                    socket.setTimeout(0);
                    System.out.println("Server closing connection.");
                    return byteArrayOutputStream.toByteArray();
                }
                if(getSessionID(dataPacket) == sessionID) {
                    sendAck(getPacketNr(dataPacket));
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
            }
            System.out.println("Server closing connection.");
        }

        pNr = (pNr + 1) % pNumbers;

        return byteArrayOutputStream.toByteArray();
    }

    @Override
    byte[] generateAckPacket(int packetNr) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byte[] bytes = new byte[2];
            int sessionNr = sessionID;
            for (int i = 1; i >= 0; i--) {
                bytes[i] = (byte) (sessionNr & 0xFF);
                sessionNr >>= 8;
            }
            byteArrayOutputStream.write(bytes);
            byteArrayOutputStream.write((byte) packetNr);

            //GBN
            int b = 1;
            byteArrayOutputStream.write((byte) b);

            //CRC32
            if(backData != 0) {
                byte[] dataCRC32 = new byte[4];
                long l = backData;
                for (int i = 3; i >= 0; i--) {
                    dataCRC32[i] = (byte) (l & 0xFF);
                    l >>= 8;
                }
                byteArrayOutputStream.write(dataCRC32);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return (byteArrayOutputStream.toByteArray());
    }

    @Override
    void sendAck(int nr) {
        socket.sendPacket(generateAckPacket(nr));
    }

    @Override
    boolean checkStart(DatagramPacket packet) {
        byte[] bytes = new byte[5];
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(packet.getData());
        try {
            byteArrayInputStream.read();
            byteArrayInputStream.read();
            byteArrayInputStream.read();
            byteArrayInputStream.read(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String reconstructedIdentifier = new String(bytes, StandardCharsets.UTF_8);
        if(reconstructedIdentifier.equals("Start")) {
            return true;
        }

        return false;
    }

    @Override
    public void closeConnection() {
        backData = 0;
        pNr = 0;
        sessionID = 0;
    }
}
