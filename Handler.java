import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.BitSet;

public class Handler 
{
    private OutputStream oStream;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private ByteBuffer length;
    
    private Socket clientSocket;
    private Peer peer;

    public Handler(Socket clientSocket, Peer peer){
        this.clientSocket = clientSocket;
        this.peer = peer;
    }
    public void run(){ //decide what to do based on input bytes
        boolean temp = true;
        while(temp) {
        try{
            InputStream is = clientSocket.getInputStream();
            OutputStream os = clientSocket.getOutputStream();
            byte[] length = new byte[4];
            is.read(length);
            int messageLength = ByteBuffer.wrap(length).getInt();
            byte[] incomingMsg = new byte[messageLength];
            is.read(incomingMsg); 
            int messageType = (int) incomingMsg[0];
       
            int receivedPeerID = ByteBuffer.wrap(incomingMsg, 1, 4).getInt();

            Message message = new Message(peer.getPeerID());
            switch(messageType){
                case 0: //choke

                case 1: //unchoke

                case 2: //interested
                    System.out.println("Received an interested message from peer: " + receivedPeerID);
                    peer.markPeerAsInterested(receivedPeerID);
                    temp = false;
                    break;

                case 3: //not interested
                    if(peer.isPeerInterested(receivedPeerID)) {
                        peer.noLongerInterested(receivedPeerID);
                        temp = false;
                    }

                case 4: //have

                case 5: //bitfield
                System.out.println("Received a bitfield message from peer: " + receivedPeerID);
                for(byte b : incomingMsg){
                    System.out.print(b);
                }
                System.out.println();
                byte[] bitfield = peer.getBitfield();

                System.out.print("This peer's bitfield: ");
                for(byte b : bitfield){
                    System.out.print(b);
                }
                System.out.println();
                
                // if this peer has any pieces, it sends a bitfield back
                if(peer.isValidBitfield(bitfield)) {
                    byte[] sent_bitfield = message.bitfieldMessage(peer);
                    os.write(sent_bitfield);
                    os.flush();
                }

                // check if the received bitfield form the other peer contains any new pieces
                for(int i = 5; i < incomingMsg.length; i++) {
                        if(bitfield[i - 5] == 0 && incomingMsg[i] == 1) {
                            System.out.println("Interested in piece");
                            byte[] interestedMessage = message.interestedMessage();
                            os.write(interestedMessage);
                            os.flush();
                            break;
                        }
                    }
            
                break;
                case 6: //request

                case 7: //piece
                default: 
                break;
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
    }
}