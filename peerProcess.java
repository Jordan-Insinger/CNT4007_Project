import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Hashtable;
import java.util.Vector;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.net.ServerSocket;
import java.net.*;
import java.io.*;

public class peerProcess {

    private int NumberOfPreferredNeighbors;
    private int UnchokingInterval;
    private int OptimisticUnchokingInterval;
    private String FileName;
    private int FileSize;
    private int PieceSize;

    ServerSocket serverSocket;
    Message message;
    MessageLogger messageLogger;

    private Vector<Peer> peerList;
    private Map<Peer,Integer> bytesDownloaded;
    private Peer currOptimistic;
    private Vector<Peer> preferredNeighbors;

    private int ID;
    private boolean hasFile;
    private byte[] bitfield;

    peerProcess(){
        message = new Message();
        messageLogger = new MessageLogger();
        peerList = new Vector<Peer>();
        preferredNeighbors = new Vector<Peer>();
        bytesDownloaded = new HashMap<>();
    }

    void setBasic(int ID){
        this.ID = ID;
        for(int i = 0; i < peerList.size(); i++){
            Peer curr = peerList.get(i);
            if(curr.getPeerID() == ID){
                this.hasFile = curr.getHasFile();
                this.bitfield = curr.getBitfield();
                break;
            }
        }
    }

    void readConfigFile(String filePath) {

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {

            String line;
            String[] params = new String[6];

            int index = 0;
            while ((line = reader.readLine()) != null) {
                // Split the line into words and numbers based on the space character
                String[] parts = line.split(" ");
                params[index] = parts[1];

                index++;
            }

            // ===== Initializing global config values ===== //
            NumberOfPreferredNeighbors = Integer.parseInt(params[0]);
            UnchokingInterval = Integer.parseInt(params[1]);
            OptimisticUnchokingInterval = Integer.parseInt(params[2]);
            FileName = params[3];
            FileSize = Integer.parseInt(params[4]);
            PieceSize = Integer.parseInt(params[5]);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void readPeerInfo(String filepath, int currID) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                // Split the line into words and numbers based on the space character
                String[] tokens = line.split(" ");

                Peer peer = new Peer(Integer.parseInt(tokens[0]));
                peer.setHostName(tokens[1]);
                peer.setListeningPort(Integer.parseInt(tokens[2]));
                peer.setHasFile(Boolean.parseBoolean(tokens[3]));
                peer.setGlobalConfig(NumberOfPreferredNeighbors, UnchokingInterval, OptimisticUnchokingInterval,
                    FileName, FileSize, PieceSize);
                peer.initializeBitfield(peer.getHasFile());
                peerList.add(peer);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Error has Occurred");
            e.printStackTrace();
        }
    }

    // void setAllPeerLists() {
    //     for (int i = 0; i < peerList.size(); i++) {
    //         peerList.get(i).setPeerList(peerList);
    //     }
    // }

    public boolean allHaveFile(){
        for(int i = 0; i < peerList.size(); i++){
            if(!peerList.get(i).getHasFile()){
                return false;
            }
        }
        return true;
    }

    public void startTimers(){
        Thread preferred = new Thread(new Runnable() {
            @Override
            public void run(){
                while(true){
                    try{
                        Instant startInstant = Instant.now();
                        reselectPreferred(startInstant);
                        Thread.sleep(UnchokingInterval);
                    }catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }
        });
        preferred.start();

        Thread optimistic = new Thread(new Runnable() {
            @Override
            public void run(){
                while(true){
                    try{
                        Instant startInstant = Instant.now();
                        reselectOptimistic(startInstant);
                        Thread.sleep(OptimisticUnchokingInterval);
                    }catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }
        });
        optimistic.start();
    }

    public void reselectPreferred(Instant startInstant){
        Vector<Peer> interested = new Vector<Peer>();
        for(int i = 0; i < peerList.size(); i++){
            Peer curr = peerList.get(i);
            if(curr.getInterested()){
                interested.add(curr);
            }
        }

        if(hasFile){
            Collections.shuffle(interested);
        }else{
            Instant finishInstant = Instant.now();
            int time = Duration.between(startInstant,finishInstant).getNano();

            Collections.sort(peerList, (x,y) -> {
                return Double.compare(downloadRate(x, time), downloadRate(y, time)); //might need to change for random tie breaking
            });
        }

        Vector<Peer> newPreferredNeighbors = new Vector<Peer>();

        try{
            for(int i = 0; i < Math.min(interested.size(), NumberOfPreferredNeighbors); i++){
                Peer curr = interested.get(i);
                if(curr.getChoked()){
                    sendMessage(curr.getSocket(), message.unchokeMessage());
                    newPreferredNeighbors.add(curr);
                    preferredNeighbors.remove(curr);
                }
            }

            for(int i = 0; i < preferredNeighbors.size(); i++){
                Peer curr = preferredNeighbors.get(i);
                if(!curr.getOptimisticallyUnchoked()){
                    sendMessage(curr.getSocket(), message.chokeMessage());
                    //add logic to turn off sending pieces
                }
            }
            preferredNeighbors = newPreferredNeighbors;
            messageLogger.log_Change_Preferred_Neighbors(ID, preferredNeighbors);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public double downloadRate(Peer peer, int time){
        return bytesDownloaded.getOrDefault(peer,0) / time;
    }

    public void reselectOptimistic(Instant startInstant){
        Vector<Peer> possible = new Vector<Peer>();
        for(int i = 0; i < peerList.size(); i++){
            Peer curr = peerList.get(i);
            if(curr.getInterested() && curr.getChoked()){
                possible.add(curr);
            }
        }

        Collections.shuffle(possible); //randomize, then pick first index

        if(!possible.get(0).equals(currOptimistic)){ 
            //if currently optimistically unchoked (OU) peer does not change, do nothing
            //otherwise, choke previous and unchoke new OU peer
            try{
                sendMessage(currOptimistic.getSocket(),message.chokeMessage());
                currOptimistic = possible.get(0);
                sendMessage(currOptimistic.getSocket(), message.unchokeMessage());
                messageLogger.log_Change_Unchoked_Neighbor(ID, currOptimistic.getPeerID());
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    void updateBytesDownloaded(Peer peer, int amt){
        bytesDownloaded.put(peer,bytesDownloaded.getOrDefault(peer,0));
    }

    private static byte[] receiveHandshake(Socket clientSocket){
        try{
            InputStream inputStream = clientSocket.getInputStream();
            byte[] buffer = new byte[1024];

            inputStream.read(buffer);
            return buffer;
        }catch(IOException e){
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] receiveMessage(Socket clientSocket){
        try {
            InputStream inputStream = clientSocket.getInputStream();
            byte[] buffer = new byte[1024];
            inputStream.read(buffer);
            return buffer;
            /*
             * byte[] lengthBytes = new byte[4];
             * inputStream.read(lengthBytes);
             * int messageLength = new DataInputStream(new
             * java.io.ByteArrayInputStream(lengthBytes)).readInt();
             * 
             * byte[] messageBytes = new byte[messageLength];
             * inputStream.read(messageBytes);
             * 
             * return messageBytes;
             */

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;

    }

    private static void sendMessage(Socket socket, byte[] message){
        try{
            OutputStream oStream = socket.getOutputStream();
            oStream.write(message);
            oStream.flush();
        }catch (IOException e){
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

    //////////////////////////////MAIN///////////////////////////////////////////////

    public static void main(String[] args) throws IOException {
        // FILEPATHS
        String configCommon = "configFiles/Common.cfg";
        String configPeerInfo = "configFiles/PeerInfo.cfg";

        int initiatorID = Integer.parseInt(args[0]);
        peerProcess peerProc = new peerProcess();
        peerProc.readConfigFile(configCommon);
        peerProc.readPeerInfo(configPeerInfo, initiatorID);
        peerProc.setBasic(Integer.parseInt(args[0]));

        // if first peer, listen to port 6008 and make no connections
        // find initiating peer:
        int initiatingPeerIndex = -1;
        for (int i = 0; i < peerProc.peerList.size(); i++) {
            if (initiatorID == peerProc.peerList.get(i).getPeerID())
                initiatingPeerIndex = i;
        }

        // create server socket and tcp connection to every peer before it
        for (int i = 0; i < peerProc.peerList.size(); i++) {
            Peer peer_ = peerProc.peerList.get(i);

            if (peer_.getPeerID() < initiatorID) {

                try {
                    // create tcp connection to peer
                    Socket socket = new Socket("localhost", peer_.getListeningPort());

                    peerProc.peerList.get(i).setSocket(socket);

                    // INITIATE HANDSHAKE
                    Message message = new Message();
                    byte[] handshake = message.handshake(initiatorID);
                    sendMessage(socket, handshake); 

                    // RECEIVE HANDSHAKE BACK FROM SERVER PEER:
                    byte[] receivedHandshake = receiveHandshake(socket);
                    printHandshake(receivedHandshake);

                    // SEND BITFIELD MESSAGE TO SERVER PEER
                    byte[] bitfieldMessage = message.bitfieldMessage(peerProc.peerList.get(initiatingPeerIndex));
                    System.out.println("BITFIELD MESSAGE BEING SENT: ");
                    printByteMessage(bitfieldMessage);
                    sendMessage(socket, bitfieldMessage);                    

                    // pass client peer over to the handler to receive messages back
                    Handler handle = new Handler(socket, peerProc.peerList.get(initiatingPeerIndex));
                    handle.run();  //move peerProcess object into handler to use byteAddress map? 


                    /*ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                        Handler messageHandler = new Handler(peer_, socket, in, out);
                    messageHandler.run();*/


                    // receive bitfield message from server
                    //byte[] receivedBitfield = receiveMessage(socket);
                    //printByteMessage(receivedBitfield);

                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else if (peer_.getPeerID() == initiatorID) {
                // create serverSocket
                try {
                    System.out.println("listening on port: " + peer_.getListeningPort());
                    ServerSocket serverSocket = new ServerSocket(peer_.getListeningPort());
                    peerProc.messageLogger.log_TCP_Connection(peerProc.ID, peer_.getPeerID());
                    peerProc.serverSocket = serverSocket;
                    
                    //peerProc.peerList.remove(peer_); //remove "main" Peer from peerList so it doesnt do operations on itself?
                    
                    
                    while (true) {
                        Socket clientSocket = serverSocket.accept(); // listening to port, waiting for tcp
                                                                     // connection request from another peer
                        byte[] receivedHandshake = receiveHandshake(clientSocket);
                        printHandshake(receivedHandshake);

                        // send handshake back to client peer
                        Message message = new Message();
                        byte[] handshake = message.handshake(initiatorID);
                        sendMessage(clientSocket, handshake);

                        // start receiving messages from other peers
                        Handler handle = new Handler(clientSocket, peer_);
                        handle.run();

                        // =============== START RECEIVING MESSAGES FROM CLIENT PEERS =================
                        /*ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                        Handler messageHandler = new Handler(peer_, clientSocket, in, out);
                        messageHandler.run();*/
                        
                        //byte[] receivedBitfield = receiveMessage(clientSocket);
                        //printByteMessage(receivedBitfield);

                        // check if this server peer has any pieces, if it does, send a bitfield message
                        // back
                        //byte[] bitfield = message.bitfieldMessage(peerProc.peerList.get(initiatingPeerIndex));
                        //sendMessage(clientSocket, bitfield);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else
                break;
        }

    }
}