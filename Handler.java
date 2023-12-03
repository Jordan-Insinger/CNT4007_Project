import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class Handler implements Runnable {
    private ObjectOutputStream os;
    private ObjectInputStream is;

    private Peer peer;
    private Peer clientPeer;

    private Message message;
    private MessageLogger messageLogger;

    private int index;

    public Handler(ObjectOutputStream os, ObjectInputStream is, Peer peer){
        this.peer = peer;
        clientPeer = null;
        message = new Message(peer.getPeerID());
        messageLogger = new MessageLogger(); 
        this.os = os;
        this.is = is;
    }

    public void run(){ //decide what to do based on input bytes

        sendHandshake(); //initial handshake message
        receiveHandshake(); //check that return handshake is valid, then sends bitfield

        while(!peer.allHaveFile()){ //until confirms that all peers have the file, stay in handler loop
            try{         
                byte[] incoming = (byte[]) is.readObject();
                printByteMessage(incoming);

                byte[] length = new byte[4];
                System.arraycopy(incoming, 0, length, index, 4);
                int messageLength = ByteBuffer.wrap(length).order(ByteOrder.BIG_ENDIAN).getInt();

                int messageType = incoming[4];

                byte[] payload;
                if(messageLength != 1){
                    payload = new byte[messageLength-1];
                    System.arraycopy(incoming, 5, payload, 0, messageLength-1);
                }else{
                    payload = new byte[0];
                }

                // byte[] length = new byte[4];
                // is.read(length);
                // int messageLength = ByteBuffer.wrap(length).order(ByteOrder.BIG_ENDIAN).getInt();

                // byte[] msgType = new byte[1];
                // is.read(msgType);
                // int messageType = (int) msgType[0];

                // byte[] payload;
                // if(messageLength > 1){
                //     payload = new byte[messageLength-1];
                // }else{
                //     payload = new byte[0];
                // }
                
                // is.read(payload);
                
                System.out.println("Message length: " + messageLength + "  Message type: " + messageType);
                System.out.println("IN\n");
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
                        processHave(payload);
                        break;
                    case 5: //bitfield
                        processBitfield(payload);
                        break;
                    case 6: //request
                        processRequest(payload);
                        break;
                    case 7: //piece
                        processPiece(payload);
                        break;
                }
            }catch(IOException e) {
                e.printStackTrace();
            }catch(ClassNotFoundException e){
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

    private void sendMessage(byte[] toWrite) throws IOException{
        os.writeObject(toWrite);
        os.flush();
    }

    private static void printHandshake(byte[] handshake){ //test print debugging
        String receivedMessage = new String(handshake, 0, 18);
        int id = ByteBuffer.wrap(handshake, 28, 4).getInt();
        System.out.println("Received Message: " + receivedMessage);
        System.out.println("Received Handshake from client: " + id);
    }

    private static void printByteMessage(byte[] message){
        System.out.println("Printing Byte Message:");
        for (byte value : message) {
            //System.out.print((value & 0xFF) + " ");
            System.out.print(value + " ");
        }
        System.out.println("");
    }

    private void sendHandshake(){
        try{
            os.writeObject(message.handshake(peer.getPeerID()));
            os.flush();
        }catch(IOException e){
            e.printStackTrace();
        }
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
        if(getPeerFromID(incomingPeerID) == 1 && incomingHeader.equals("P2PFILESHARINGPROJ")){
            messageLogger.log_TCP_Connected(peer.getPeerID(), clientPeer.getPeerID());
            peer.addChoked(clientPeer);
            peer.setOutputStream(os, clientPeer);
            try{
                sendMessage(message.bitfieldMessage(peer));   
                
            }catch(IOException e){
                e.printStackTrace();
            }         
        }
    }

    private int getPeerFromID(int incomingID){
        for(Peer peer : peer.getPeerList()){
            if(peer.getPeerID() == incomingID){
                clientPeer = peer;
                return 1;
            }
        }
        return -1;
    }

    private void processChoke(){
        System.out.println("Received a choke message from peer: " + clientPeer.getPeerID());
        peer.addChoked(clientPeer); //todo, where to actually do this
        peer.removeUnchoked(clientPeer);
        messageLogger.log_Choke(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processUnchoke(){
        System.out.println("Received an unchoke message from peer: " + clientPeer.getPeerID());
        messageLogger.log_Unchoke(peer.getPeerID(), clientPeer.getPeerID());
                        
        //based on peer bitfield, decide what index to requst and send, might need to adjust, as might not actually need anything if it has the file already
        int indexToRequest = peer.calculateRequest(clientPeer.getBitfield());
        peer.removeChoked(clientPeer);
        peer.addUnchoked(clientPeer);
        try{
            if(indexToRequest != -1){ //if -1, then peer has file already, dont need to request
                os.writeObject(message.requestMessage(indexToRequest));
                os.flush();
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void processInterested(){
        System.out.println("Received an interested message from peer: " + clientPeer.getPeerID());
        peer.addInterested(clientPeer);
        messageLogger.log_Interested(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processNotInterested(){
        System.out.println("Received a not interested message from peer: " + clientPeer.getPeerID());
        peer.removeInterested(clientPeer);
        System.out.println("stuck?");
        messageLogger.log_Not_Interested(peer.getPeerID(), clientPeer.getPeerID());
        System.out.println("PROBLEM");
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
                os.writeObject(message.interestedMessage());
                os.flush();
            }else{
                os.writeObject(message.notinterestedMessage());
                os.flush();
            }
        }catch(IOException e){
            e.printStackTrace();
        }        
    }

    private void processBitfield(byte[] payload){
        System.out.print("\nReceived a bitfield message from peer " + clientPeer.getPeerID() + ":\n");
        for(byte b : payload){
            System.out.print(b);
        }
        System.out.println("");

        byte[] bitfield = peer.getBitfield();
        System.out.println("\nPeer " + peer.getPeerID() + " bitfield:");
        for(byte b : bitfield){
            System.out.print(b);
        }
        System.out.println("\n");

        clientPeer.initializeBitfield(clientPeer.getHasFile());

        try{
            //compare bitfields and return result, and send corresponding message
            boolean interested = peer.isPeerInterested(payload);
            System.out.println("Interested? " + interested + "\n");
            if(interested) {
                os.writeObject(message.interestedMessage());
                os.flush();
            }else{
                os.writeObject(message.notinterestedMessage());
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
        try{
            if(!peer.getChokedList().contains(clientPeer)){
                byte[] tosend = peer.getPiece(index);
                sendMessage(message.pieceMessage(tosend));
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void processPiece(byte[] payload){ //todo
        System.out.println("Received a piece message from peer:" + clientPeer.getPeerID());
        index = ((payload[0] & 0xFF) << 24) | //can probably use a more condensed way to do this
                ((payload[1] & 0xFF) << 16) |
                ((payload[2] & 0xFF) << 8)  |
                ((payload[3] & 0xFF) << 0);

        byte[] pieceArr = new byte[payload.length-4];
        System.arraycopy(payload, 4, pieceArr, 0, payload.length-4);

        peer.setPiece(index,pieceArr);
        peer.incrementPieces();

        messageLogger.log_Piece_Downloaded(peer.getPeerID(), clientPeer.getPeerID(), index, peer.getNumPieces());
        clientPeer.updateBytesDownloaded(payload.length);
        peer.updateBitfield(index);

        for(int i = 0; i < peer.getPeerList().size(); i++){
            //send have msg to other peers
        }

        if(peer.getNumPieces() == peer.fileSize / peer.pieceSize){
            peer.setHasFile(true);
            messageLogger.log_Piece_Downloaded(peer.getPeerID(), clientPeer.getPeerID(), index, peer.getNumPieces());
            peer.downloadFile();
        }

    }
}