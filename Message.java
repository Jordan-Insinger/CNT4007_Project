import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class Message {
    private ByteArrayOutputStream oStream;
    private ByteBuffer length;

    public Message() {
        oStream = new ByteArrayOutputStream();    // used for byte operations
        length = ByteBuffer.allocate(4); // 4 bytes allocated for length field in each message except handshake
    }

     byte[] handshake(int peerID_) throws IOException 
    {
        byte[] header =  "P2PFILESHARINGPROJ".getBytes();   // 18-byte String
        byte[] zeros = new byte[10];    // 10-byte zero bits
        int peerID = peerID_; // 4-byte integer ID

        oStream.write(header);
        oStream.write(zeros);
        byte[] bytes = ByteBuffer.allocate(4).putInt(peerID).array();
        oStream.write(bytes);

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
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 2;
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

    byte[] notinterestedMessage() throws IOException 
    {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 3;
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
        byte[] payload = peer.getBitfield();
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 5;
        
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

    byte[] requestMessage() throws IOException 
    {
        byte[] messageLength = new byte[4];
        byte[] messageType = new byte[1];
         
        messageType[0] = 6;
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
