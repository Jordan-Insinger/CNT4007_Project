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
    private Socket socket;
    private ByteBuffer length;
    
    private Peer peer;

    public Handler() 
    {
        oStream = new ByteArrayOutputStream();
        length = ByteBuffer.allocate(4);
    }

    public Handler(Peer _peer, Socket _socket, ObjectInputStream _in, ObjectOutputStream _out)
    {
        this.peer = _peer;
        this.socket = _socket;
        this.in = _in;
        this.out = _out;
       
        oStream = new ByteArrayOutputStream();
        length = ByteBuffer.allocate(4);
    }

    public void run(){ //decide what to do based on input bytes
        while(true)
        {
            if(peer.getHasFile() == 1 && peer.allHaveFile())
            {
                System.out.println("NOT good");
                System.exit(0);
            }
            else
            {
                try
                {
                    //typecast incoming object to byte[] for parsing
                    try 
                    {
                        byte[] incomingMsg = (byte[]) in.readObject();
                        
                        System.out.println("Reading from bitstream: ");
                        for(byte b : incomingMsg) 
                        {
                            System.out.print(String.format("%02X ", b));
                        }
                        
                        //get mesage length as an int for calculation
                        int messageLength = 0;
                        for(int i = 0; i < 4; i++)
                        {
                            messageLength = (messageLength << 8) + (incomingMsg[i] & 0xFF);
                        }

                        //byte 5 (4 0-indexed) contains message type, int 0 to 7
                        int cmp = incomingMsg[4];
                        switch(cmp)
                        {
                            case 0: //choke
                                
                            case 1: //unchoke

                            case 2: //interested

                            case 3: //not interested

                            case 4: //have

                            case 5: //bitfield
                                System.out.println("received a bitfield Message!");
                                break;
                            
                            case 6: //request

                            case 7: //piece
                        }
                    }
                    catch (java.io.EOFException e) 
                    {
                        e.printStackTrace();
                        break;
                    }
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                    break;
                }  
                catch(ClassNotFoundException e) {
                    e.printStackTrace();
                    break;
                }
                
            }
        }
    }

}