import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class Message {
    private ByteArrayOutputStream oStream;
    private ByteBuffer length;
    int peerID;

    public Message(){ //empty overloaded constructor, for use in Peer for choke and unchoke messages. 
        oStream = new ByteArrayOutputStream();
        length = ByteBuffer.allocate(4);
    }

    public Message(int peerID){
        oStream = new ByteArrayOutputStream();    // used for byte operations
        length = ByteBuffer.allocate(4); // 4 bytes allocated for length field in each message except handshake
        this.peerID = peerID;
    }

     byte[] handshake(int peerID_) throws IOException {
        byte[] header =  "P2PFILESHARINGPROJ".getBytes(StandardCharsets.UTF_8);   // 18-byte String
        byte[] zeros = new byte[10];    // 10-byte zero bits
        int peerID = peerID_; // 4-byte integer ID

        oStream.reset();
        oStream.write(header);
        oStream.write(zeros);
        byte[] bytes = ByteBuffer.allocate(4).putInt(peerID).array();
        oStream.write(bytes);

        oStream.close();
        return oStream.toByteArray();
    }

    byte[] chokeMessage() throws IOException {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
        messageType[0] = 0;
        length.putInt(messageType.length);
        messageLength = length.array();

        try{
            oStream.reset();
            oStream.write(messageLength);
            oStream.write(messageType);
        }catch(IOException e){
            e.printStackTrace();
        }
            
        oStream.close();
        return oStream.toByteArray();
    }

    byte[] unchokeMessage() throws IOException {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];

        messageType[0] = 1;
        length.putInt(messageType.length);
        messageLength = length.array();        

        try{
            oStream.reset();
            oStream.write(messageLength);
            oStream.write(messageType);
        }
        catch (IOException e){
            e.printStackTrace();
        }
            
        oStream.close();
        return oStream.toByteArray();
    }

    byte[] interestedMessage() throws IOException {
        byte[] messageType = new byte[1];
        messageType[0] = 2;
        byte[] messageLength = ByteBuffer.allocate(4).putInt(1).array(); 
        try{
            oStream.reset();
            oStream.write(messageLength);
            oStream.write(messageType);
        }
        catch(IOException e){
            e.printStackTrace();
        }
            
        oStream.close();        
        return oStream.toByteArray();
    }

    byte[] notinterestedMessage() throws IOException {
        ByteBuffer len = ByteBuffer.allocate(4);
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 3;
        len.putInt(messageType.length);
        messageLength = len.array();
        len.clear();

        try{
            oStream.write(messageLength);
            oStream.write(messageType);
            oStream.write(peerID);
        }catch(IOException e){
            e.printStackTrace();
        }

        oStream.close();
        return oStream.toByteArray();
    }

    byte[] haveMessage() throws IOException {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 4;
        byte[] payload = new byte[4]; // 4-byte piece index field

        length.putInt(messageType.length + payload.length);
        messageLength = length.array();

        try{
            oStream.reset();
            oStream.write(messageLength);
            oStream.write(messageType);
        }catch(IOException e){
            e.printStackTrace();
        }

        oStream.close();
        return oStream.toByteArray();
    }

    byte[] bitfieldMessage(Peer peer) throws IOException {
        byte[] payload = peer.getBitfield();
        byte[] messageType = new byte[1];

        messageType[0] = 5;  
        byte[] messageLengthAlt = ByteBuffer.allocate(4).putInt(payload.length + 1).array();              

        try{
            oStream.reset();
            oStream.write(messageLengthAlt);
            oStream.write(messageType);
            oStream.write(payload);  
        }catch(IOException e){
            e.printStackTrace();
        }
        oStream.close();
        return oStream.toByteArray();
    }

    byte[] requestMessage(int indexToRequest) throws IOException { //todo
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 6;
        byte[] payload = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(indexToRequest).array(); // 4-byte piece index field

        length.putInt(messageType.length + payload.length);
        messageLength = length.array();

        try{
            oStream.reset();
            oStream.write(messageLength);
            oStream.write(messageType);
            oStream.write(payload);
        }catch (IOException e){
            e.printStackTrace();
        }

        oStream.close();
        return oStream.toByteArray();
    }

    byte[] pieceMessage() throws IOException { //todo
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 7;
        byte[] payload = new byte[4]; // 4-byte piece index field

        length.putInt(messageType.length + payload.length);
        messageLength = length.array();

        try{
            oStream.reset();
            oStream.write(messageLength);
            oStream.write(messageType);
        }catch(IOException e){
            e.printStackTrace();
        }

        oStream.close();
        return oStream.toByteArray();
    }
}
