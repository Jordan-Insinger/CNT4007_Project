import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class Handler implements Runnable 
{
    private InputStream is;
    private OutputStream os;
    
    private Peer peer;
    private Peer clientPeer;

    private Message message;
    private MessageLogger messageLogger;

    private int index;
    private boolean isClient;

    public Handler(Socket clientSocket, Peer peer, boolean isClient){
        this.peer = peer;
        clientPeer = null;
        this.isClient = isClient;
        try{
            is = clientSocket.getInputStream();
            os = clientSocket.getOutputStream();
        }catch(IOException e){
            e.printStackTrace();
        }
        message = new Message(peer.getPeerID());
        messageLogger = new MessageLogger();        
    }

    public void run(){ //decide what to do based on input bytes
        processHandshakes(); //send and receive handshakes, and then enter loop, where it will stay until all files have file

        while(!peer.allHaveFile()){
            try{            
                byte[] length = new byte[4];
                is.read(length);
                int messageLength = ByteBuffer.wrap(length).getInt();

                byte[] msgType = new byte[1];
                is.read(msgType);
                int messageType = (int) msgType[0];

                byte[] payload;

                switch(messageType){
                    case 0: //choke
                        processChoke();
                        break;
                    case 1: //unchoke
                        processUnchoke();
                        break;
                    case 2: //interested
                        processInterested();
                        break;
                    case 3: //not interested
                        processNotInterested();
                        break;
                    case 4: //have
                        payload = new byte[messageLength];
                        is.read(payload);
                        processHave(payload);
                        break;
                    case 5: //bitfield
                        payload = new byte[messageLength];
                        is.read(payload);
                        processBitfield(payload);
                        break;
                    case 6: //request
                        payload = new byte[messageLength];
                        is.read(payload);
                        processRequest(payload);
                        break;
                    case 7: //piece
                        payload = new byte[messageLength];
                        is.read(payload);
                        processPiece(payload);
                        break;
                }
            }catch(IOException e) {
                e.printStackTrace();
            }
        }
        try{
            is.close();
            os.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private static void printHandshake(byte[] handshake){ // handles receiving messages from client
        String receivedMessage = new String(handshake, 0, 18);
        int id = ByteBuffer.wrap(handshake, 28, 4).getInt();
        System.out.println("Received Message: " + receivedMessage);
        System.out.println("Received Handshake from client: " + id);
    }

    private static void printByteMessage(byte[] message){
        System.out.println("Printing Byte Message:");
        for (byte value : message) {
            System.out.print((value & 0xFF) + " ");
        }
    }

    private void processHandshakes(){
        try{
            byte[] incomingHandshake = new byte[32];
            if(isClient){
                is.read(incomingHandshake);
                printHandshake(incomingHandshake);
                os.write(message.handshake(peer.getPeerID()));
                os.flush();
            }else{
                os.write(message.handshake(peer.getPeerID()));
                os.flush();
                is.read(incomingHandshake);
                printHandshake(incomingHandshake);
            }

            //todo, check string header and incoming peer id to make sure they seem correct

            int incomingPeerID = //convert last 4 bytes into int for peerID, could probably do it prettier
                ((incomingHandshake[28] & 0xFF) << 24) |
                ((incomingHandshake[29] & 0xFF) << 16) |
                ((incomingHandshake[30] & 0xFF) << 8)  |
                ((incomingHandshake[31] & 0xFF) << 0);
            
            getPeerFromID(incomingPeerID);

            os.write(message.bitfieldMessage(peer));
            os.flush();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void getPeerFromID(int incomingID){
        for(Peer peer : peer.getPeerList()){
            if(peer.getPeerID() == incomingID){
                clientPeer = peer;
            }
        }
    }

    private void processChoke(){
        System.out.println("Received a choke message from peer: " + clientPeer.getPeerID());
        clientPeer.setChoked(true); //todo, where to actually do this
        messageLogger.log_Choke(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processUnchoke(){
        System.out.println("Received an unchoke message from peer: " + clientPeer.getPeerID());
                        
        //based on peer bitfield, decide what index to requst and send, might need to adjust, as might not actually need anything if it has the file already
        int indexToRequest = peer.calculateRequest(clientPeer.getBitfield());
        clientPeer.setChoked(false);
        messageLogger.log_Unchoke(peer.getPeerID(), clientPeer.getPeerID());
        try{
            if(indexToRequest != -1){ //if -1, then peer has file already, dont need to request
                os.write(message.requestMessage(indexToRequest));
                os.flush();
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void processInterested(){
        System.out.println("Received an interested message from peer: " + clientPeer.getPeerID());
        clientPeer.setInterested(true);
        messageLogger.log_Interested(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processNotInterested(){
        System.out.println("Received a not interested message from peer: " + clientPeer.getPeerID());
        clientPeer.setInterested(false);
        messageLogger.log_Not_Interested(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processHave(byte[] payload){
        System.out.println("Received a have message from peer:" + clientPeer.getPeerID());
        index = ((payload[0] & 0xFF) << 24) |
                ((payload[1] & 0xFF) << 16) |
                ((payload[2] & 0xFF) << 8)  |
                ((payload[3] & 0xFF) << 0);

        clientPeer.updateBitfield(index);
        messageLogger.log_Have_Message(peer.getPeerID(), clientPeer.getPeerID(), index);

        try{
            if(peer.isPeerInterested(clientPeer.getBitfield())){
                os.write(message.interestedMessage());
                os.flush();
            }else{
                os.write(message.notinterestedMessage());
                os.flush();
            }
        }catch(IOException e){
            e.printStackTrace();
        }        
    }

    private void processBitfield(byte[] payload){
        System.out.println("Received a bitfield message from peer: " + clientPeer.getPeerID());
        for(byte b : payload){
            System.out.print(b);
        }
        System.out.println();
        byte[] bitfield = clientPeer.getBitfield();

        System.out.print("This peer's bitfield: ");
        for(byte b : bitfield){
            System.out.print(b);
        }
        System.out.println();

        try{
            // if this peer has any pieces and is valid, it sends a bitfield back
            if(peer.isValidBitfield(peer.getBitfield())){
                os.write(message.bitfieldMessage(peer));
                os.flush();
            }
            //compare bitfields and return result, and send corresponding message
            boolean interested = peer.isPeerInterested(bitfield);
            if(interested) {
                os.write(message.interestedMessage());
                os.flush();
            }else{
                os.write(message.notinterestedMessage());
                os.flush();
            }  
        }catch(IOException e){
            e.printStackTrace();
        }                      
    }

    private void processRequest(byte[] payload){ //todo
        System.out.println("Received a request message from peer:" + clientPeer.getPeerID());
        index = ((payload[0] & 0xFF) << 24) | //can probably use a more condensed way to do this
                ((payload[1] & 0xFF) << 16) |
                ((payload[2] & 0xFF) << 8)  |
                ((payload[3] & 0xFF) << 0);
        
    }

    private void processPiece(byte[] payload){ //todo
        System.out.println("Received a piece message from peer:" + clientPeer.getPeerID());
    }
}