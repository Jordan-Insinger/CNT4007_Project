import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
<<<<<<< Updated upstream
import java.util.BitSet;
=======
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;



public class Handler implements Runnable {
    private Socket socket;
    private ObjectOutputStream os;
    private ObjectInputStream is;
>>>>>>> Stashed changes

public class Handler 
{
    private OutputStream oStream;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private ByteBuffer length;
    
    private Socket clientSocket;
    private Peer peer;

<<<<<<< Updated upstream
    public Handler(Socket clientSocket, Peer peer){
        this.clientSocket = clientSocket;
=======
    private Message message;
    private Logger logger;
    private final Object lock = new Object();

    private int index;
    private boolean first;

    public Handler(Socket socket, ObjectOutputStream os, ObjectInputStream is, Peer peer, peerProcess peerProc){
        this.socket = socket;
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
=======

    private void sendMessage(byte[] toWrite) throws IOException{
        synchronized (lock) {
            try {
                os.writeObject(toWrite);
                os.flush();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void printByteMessage(byte[] message){ //Command line debugging
        System.out.println("Printing Byte Message:");
        for (byte value : message) {
            //System.out.print((value & 0xFF) + " ");
            System.out.print(value + " ");
        }
        System.out.println("");
    }

    private void receiveHandshake(){
        byte[] incomingHandshake = new byte[32];
        try{
            incomingHandshake = (byte[]) is.readObject();
        }catch(IOException e){
            e.printStackTrace();
        }catch(ClassNotFoundException e){
            e.printStackTrace();
        }

        byte[] header = new byte[18];
        System.arraycopy(incomingHandshake,0,header,0,18);
        String incomingHeader = new String(header, StandardCharsets.UTF_8);

        int incomingPeerID = //convert last 4 bytes into int for peerID, could probably do it prettier
            ((incomingHandshake[28] & 0xFF) << 24) |
            ((incomingHandshake[29] & 0xFF) << 16) |
            ((incomingHandshake[30] & 0xFF) << 8)  |
            ((incomingHandshake[31] & 0xFF) << 0);

        //if incoming handshake is valid, send bitfield back
        if(getPeerFromID(incomingPeerID) && incomingHeader.equals("P2PFILESHARINGPROJ")){
            logger.logTCPConnected(peer.getPeerID(), clientPeer.getPeerID());
            peer.addChoked(clientPeer);
            peer.removeUnchoked(clientPeer);
            peer.setOutputStream(os, clientPeer);
            try{
                if(peer.isValidBitfield(peer.getBitfield())){
                    sendMessage(message.bitfieldMessage(peer));   
                }
            }catch(IOException e){
                e.printStackTrace();
            }         
        }
    }

    private boolean getPeerFromID(int incomingID){
        for(Peer inpeer : peer.getPeerList()){
            if(inpeer.getPeerID() == incomingID){
                clientPeer = inpeer;
                return true;
            }
        }
        return false;
    }

    private void processChoke(){
        System.out.println("Received a choke message from peer: " + clientPeer.getPeerID());
        peer.addChoked(clientPeer); //todo, where to actually do this
        peer.removeUnchoked(clientPeer);
        logger.logChoked(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processUnchoke(){
        System.out.println("Received an unchoke message from peer: " + clientPeer.getPeerID());
        logger.logUnchoked(peer.getPeerID(), clientPeer.getPeerID());
                        
        //based on peer bitfield, decide what index to requst and send, might need to adjust, as might not actually need anything if it has the file already
        if(!peer.getHasFile()){
            int indexToRequest = peer.calculateRequest(clientPeer);
            peer.removeChoked(clientPeer);
            peer.addUnchoked(clientPeer);
            try{
                if(indexToRequest != -1){
                    sendMessage(message.requestMessage(indexToRequest));
                }
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    private void processInterested(){
        System.out.println("Received an interested message from peer: " + clientPeer.getPeerID());
        peer.addInterested(clientPeer);
        logger.logInterested(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processNotInterested(){
        System.out.println("Received a not interested message from peer: " + clientPeer.getPeerID());
        peer.removeInterested(clientPeer);
        logger.logNotInterested(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processHave(byte[] payload){
        System.out.println("Received a have message from peer: " + clientPeer.getPeerID());
        index = ((payload[0] & 0xFF) << 24) |
                ((payload[1] & 0xFF) << 16) |
                ((payload[2] & 0xFF) << 8)  |
                ((payload[3] & 0xFF) << 0);

        clientPeer.updateBitfield(index);
        logger.logHave(peer.getPeerID(), clientPeer.getPeerID(), index);

        try{
            if(peer.isPeerInterested(clientPeer.getBitfield())){
                sendMessage(message.interestedMessage());
            }else{
                sendMessage(message.notinterestedMessage());
            }
        }catch(IOException e){
            e.printStackTrace();
        }        
    }

    private void processBitfield(byte[] payload){
        System.out.print("\nReceived a bitfield message from peer: " + clientPeer.getPeerID() + "\n");
        for(byte b : payload){
            System.out.print(b);
        }
        System.out.println("");

        byte[] bitfield = peer.getBitfield();
        System.out.println("\nPeer " + peer.getPeerID() + " bitfield:");
        for(byte b : bitfield){
            System.out.print(b);
        }
        System.out.println("");

        clientPeer.initializeBitfield(clientPeer.getHasFile());

        try{
            //compare bitfields and return result, and send corresponding message
            boolean interested = peer.isPeerInterested(payload);
            System.out.println("Interested? " + interested + "\n");
            if(interested){
                sendMessage(message.interestedMessage());
            }else{
                sendMessage(message.notinterestedMessage());
            } 
        }catch(IOException e){
            e.printStackTrace();
        }                      
    }

    private void processRequest(byte[] payload){ //todo
        System.out.println("Received a request message from peer: " + clientPeer.getPeerID());
        index = ((payload[0] & 0xFF) << 24) | //can probably use a more condensed way to do this
                ((payload[1] & 0xFF) << 16) |
                ((payload[2] & 0xFF) << 8)  |
                ((payload[3] & 0xFF) << 0);
        try{
            if(!peer.getChokedList().contains(clientPeer)){
                byte[] tosend = peer.getPiece(index);
                sendMessage(message.pieceMessage(index, tosend));
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void processPiece(byte[] payload){ //todo
        System.out.println("Received a piece message from peer: " + clientPeer.getPeerID());
        index = ((payload[0] & 0xFF) << 24) | //can probably use a more condensed way to do this
                ((payload[1] & 0xFF) << 16) |
                ((payload[2] & 0xFF) << 8)  |
                ((payload[3] & 0xFF) << 0);
        
        byte[] pieceArr = new byte[payload.length-4];
        System.arraycopy(payload, 4, pieceArr, 0, payload.length-4);

        peer.setPiece(index,pieceArr);
        peer.updateBitfield(index);
        peer.incrementPieces();

        logger.logPieceDownloaded(peer.getPeerID(), clientPeer.getPeerID(), index, peer.getNumPieces());
        peer.updateBytesDownloaded(payload.length);
        try{
            for(Peer curr : peer.getPeerList()){
                System.out.println(curr.getPeerID() + " : " + curr.getHasFile());
                if(curr.getObjectOutputStream() != null && curr.getPeerID() != peer.getPeerID()){
                    System.out.println(curr.getPeerID() + " entered.\n");
                    curr.sendMessage(curr.getObjectOutputStream(), message.haveMessage(index));
                }
            }
            if(peer.getHasFile()){
                System.out.println("Peer " + peer.getPeerID() + " has downloaded the complete file.");
                logger.logCompleteDownload(peer.getPeerID());
                peer.downloadFileToPeer();
                clientPeer.removeInterested(clientPeer);
                clientPeer.removeUnchoked(clientPeer);
                clientPeer.addChoked(clientPeer);
                //sendMessage(message.notinterestedMessage());
            }else{
                int indexToRequest = peer.calculateRequest(clientPeer);
                if(indexToRequest != -1){
                    sendMessage(message.requestMessage(indexToRequest));
                }
            }
        }catch(IOException e){
            e.printStackTrace();
        }
>>>>>>> Stashed changes
    }
}