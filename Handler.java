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
    private ByteArrayOutputStream oStream;
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
        try{
            InputStream is = clientSocket.getInputStream();

            byte[] length = new byte[4];
            is.read(length);
            int messageLength = ByteBuffer.wrap(length).getInt();
            byte[] incomingMsg = new byte[messageLength];
            is.read(incomingMsg); 
            int messageType = (int) incomingMsg[0];

            Message message = new Message();

            switch(messageType){
                case 0: //choke

                case 1: //unchoke

                case 2: //interested

                case 3: //not interested

                case 4: //have

                case 5: //bitfield
                                    
                case 6: //request

                case 7: //piece
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}