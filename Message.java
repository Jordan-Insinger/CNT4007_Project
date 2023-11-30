import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class Message {
    private ByteArrayOutputStream oStream;
    private ByteBuffer length;
    int peerID;

    public Message(int peerID) {
        length = ByteBuffer.allocate(4); // 4 bytes allocated for length field in each message except handshake
        this.peerID = peerID;
    }

     byte[] handshake(int peerID_) throws IOException 
    {
        oStream = new ByteArrayOutputStream();    // used for byte operations
        byte[] header =  "P2PFILESHARINGPROJ".getBytes();   // 18-byte String
        byte[] zeros = new byte[10];    // 10-byte zero bits
        int peerID = peerID_; // 4-byte integer ID

        oStream.write(header);
        oStream.write(zeros);
        byte[] bytes = ByteBuffer.allocate(4).putInt(peerID).array();
        oStream.write(bytes);

        oStream.flush();
        oStream.close();
        return oStream.toByteArray();
    }

     byte[] chokeMessage() throws IOException 
    {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
        messageType[0] = 0;
        length.putInt(messageType.length);
        messageLength = length.array();

        try 
        {
            oStream.write(messageLength);
            oStream.write(messageType);
        }
        catch (IOException e) 
        {
            e.printStackTrace();
        }
            
        oStream.close();
        return oStream.toByteArray();
    }

    byte[] unchokeMessage() throws IOException 
    {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];

        messageType[0] = 1;
        length.putInt(messageType.length);
        messageLength = length.array();        

        try 
        {
            oStream.write(messageLength);
            oStream.write(messageType);
        }
        catch (IOException e) 
        {
            e.printStackTrace();
        }
            
        oStream.close();
        return oStream.toByteArray();
    }

    byte[] interestedMessage() throws IOException 
    {
        oStream = new ByteArrayOutputStream();
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
        byte[] peerID = new byte[4];
         
        messageType[0] = 2;
        peerID = ByteBuffer.allocate(4).putInt(this.peerID).array();
        length.putInt(messageType.length + peerID.length);
        messageLength = length.array();

        try 
        {
            oStream.write(messageLength);
            oStream.write(messageType);
            oStream.write(peerID);
        }
        catch (IOException e) 
        {
            e.printStackTrace();
        }
            
        oStream.close();        
        return oStream.toByteArray();
    }

    byte[] notinterestedMessage() throws IOException 
    {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
        byte[] peerID = new byte[4];
         
        messageType[0] = 3;
        peerID = ByteBuffer.allocate(4).putInt(this.peerID).array();
        length.putInt(messageType.length + peerID.length);
        messageLength = length.array();

        try 
        {
            oStream.write(messageLength);
            oStream.write(messageType);
            oStream.write(peerID);
        }
        catch (IOException e) 
        {
            e.printStackTrace();
        }

        oStream.close();
        return oStream.toByteArray();
    }

    byte[] haveMessage() throws IOException 
    {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 4;
        byte[] payload = new byte[4]; // 4-byte piece index field

        length.putInt(messageType.length + payload.length);
        messageLength = length.array();

        try 
        {
            oStream.write(messageLength);
            oStream.write(messageType);
        }
        catch (IOException e) 
        {
            e.printStackTrace();
        }

        oStream.close();
        return oStream.toByteArray();
    }

    byte[] bitfieldMessage(Peer peer) throws IOException 
    {
        oStream = new ByteArrayOutputStream();    // used for byte operations
        byte[] payload = peer.getBitfield();
        byte[] messageLength = new byte[4];
        byte[] peerID = new byte[4];
        byte[] messageType = new byte[1];
         
        peerID = ByteBuffer.allocate(4).putInt(peer.getPeerID()).array();
        messageType[0] = 5;
        
        length.putInt(messageType.length + peerID.length + payload.length);
        messageLength = length.array();    

        try 
        {
            oStream.write(messageLength);
            oStream.write(messageType);
            oStream.write(peerID);
            oStream.write(payload);   
        }
        catch (IOException e) 
        {
            e.printStackTrace();
        }

        oStream.close();
        return oStream.toByteArray();
    }

    byte[] requestMessage(int indexToRequest) throws IOException 
    {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 6;
        byte[] payload = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(indexToRequest).array(); // 4-byte piece index field

        length.putInt(messageType.length + payload.length);
        messageLength = length.array();

        try 
        {
            oStream.write(messageLength);
            oStream.write(messageType);
            oStream.write(payload);
        }
        catch (IOException e) 
        {
            e.printStackTrace();
        }

        oStream.close();
        return oStream.toByteArray();
    }

    byte[] pieceMessage() throws IOException 
    {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 7;
        byte[] payload = new byte[4]; // 4-byte piece index field

        length.putInt(messageType.length + payload.length);
        messageLength = length.array();

        try 
        {
            oStream.write(messageLength);
            oStream.write(messageType);
        }
        catch (IOException e) 
        {
            e.printStackTrace();
        }

        oStream.close();
        return oStream.toByteArray();
    }
}
