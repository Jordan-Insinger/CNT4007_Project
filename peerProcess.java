import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Hashtable;
import java.util.Vector;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.net.ServerSocket;
import java.net.*;
import java.io.*;
import java.lang.Thread;

public class peerProcess {

    private int NumberOfPreferredNeighbors;
    private int UnchokingInterval;
    private int OptimisticUnchokingInterval;
    private String FileName;
    private int FileSize;
    private int PieceSize;

    private Hashtable<Integer,Peer> peersList;
    private Vector<Peer> peersListVec;
    private Peer currPeer;
    private MessageLogger messageLogger;

    peerProcess(){
        peersList = new Hashtable<Integer,Peer>();
        peersListVec
        messageLogger = new MessageLogger();
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
            System.err.println("Error ocurred in readConfigFile()");
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

                int booleanValue = Integer.parseInt(tokens[3]);
                boolean has = (booleanValue == 1);
                peer.setHasFile(has);

                peer.setGlobalConfig(NumberOfPreferredNeighbors, UnchokingInterval, OptimisticUnchokingInterval,
                    FileName, FileSize, PieceSize);
                peer.initializeBitfield(peer.getHasFile());

                if(has){
                    peer.setFile("./configFiles/project_config_file_small/project_config_file_small/" + tokens[0] + "/" + FileName);
                }

                if(currID == peer.getPeerID()){
                    currPeer = peer;
                }else{
                    peersList.put(peer.getPeerID(), peer); //all other peers, excluding current peer with given CLA ID integer
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found in readPeerInfo");
            e.printStackTrace();
        }
    }

    // private static void printHandshake(byte[] handshake){ // handles receiving messages from client
    //     String receivedMessage = new String(handshake, 0, 18);
    //     int id = ByteBuffer.wrap(handshake, 28, 4).getInt();
    //     System.out.println("Received Message: " + receivedMessage);
    //     System.out.println("Received Handshake from client: " + id);
    // }

    // private static void printByteMessage(byte[] message){
    //     System.out.println("Printing Byte Message:");
    //     for (byte value : message) {
    //         System.out.print((value & 0xFF) + " ");
    //     }
    // }

    // private static void getPeerBitfields(Peer peer, peerProcess peerProc) {
    //     for(int i = 0; i < peerProc.peerList.size(); i++) {
    //         Pair<Integer, byte[]> pair = new Pair<Integer,byte[]>(peerProc.peerList.get(i).getPeerID(), peerProc.peerList.get(i).getBitfield());
    //         peer.peer_bitfields.add(pair);
    //     }
    // }

    public void startServer(int listeningPort){
        ServerSocket serverSocket = null;
        try{
            serverSocket = new ServerSocket(listeningPort);
            while(true){
                Socket clientSocket = serverSocket.accept();
                ObjectOutputStream os = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream is = new ObjectInputStream(clientSocket.getInputStream());
                Handler handler = new Handler(os, is, currPeer);
                new Thread(handler).start();
            }
        }catch(IOException e){
            System.err.println("Error opening server socket on listening port " + listeningPort + ".");
            e.printStackTrace();
        }//finally{
            //try{
                //serverSocket.close();
                //currPeer.shutdown(); //end choking reselection timers on finish
            //}catch(){
                //System.err.println("Problem closing server connection for " + currPeer.getPeerID());
                //e.printStackTrace();
            //}
        //}
    }

    public void connectToLowerPeers(){
        Socket socket = null;
        for(Peer peer : peersList){
            if(peer.getPeerID() < currPeer.getPeerID()){
                try{
                    socket = new Socket("localhost", peer.getListeningPort());
                    ObjectOutputStream os = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
                    Handler handler = new Handler(os, is, currPeer);
                    messageLogger.log_TCP_Connection(currPeer.getPeerID(), peer.getPeerID());
                    new Thread(handler).start();
                }catch(IOException e){
                    System.err.println("Error connecting to " + peer.getPeerID() + " from " + currPeer.getPeerID() + ".");
                    e.printStackTrace();
                }finally{
                    //socket.close();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if(args.length != 1){
            System.out.println("Wrong number of arguments. Expected format:\njava peerProcess [peerID]");
            return;
        }
        // FILEPATHS
        String configCommon = "./configFiles/project_config_file_small/project_config_file_small/Common.cfg";
        String configPeerInfo = "./configFiles/project_config_file_small/project_config_file_small/PeerInfo.cfg";

        // String configCommon = "./project_config_file_large/project_config_file_large/Common.cfg";
        // String configPeerInfo = "./project_config_file_large/project_config_file_large/PeerInfo.cfg";

        int initiatorID = Integer.parseInt(args[0]);
        peerProcess peerProc = new peerProcess();

        //Read required info, set up Peer list with given parameters in peerProcess class
        peerProc.readConfigFile(configCommon);
        peerProc.readPeerInfo(configPeerInfo, initiatorID);
        peerProc.currPeer.setNeighbors(peerProc.peersList);

        //start server on current Peer's listening port, calculated in readPeerInfo from config file        
        new Thread(() -> {
            peerProc.startServer(peerProc.currPeer.getListeningPort());
        }).start();

        //using peerList gotten from readPeerInfo, iterate through and make new Handlers for each peer with lower ID
        new Thread(() -> {
            peerProc.connectToLowerPeers();;
        }).start();

        //reselect preferred neighbors and optimistically unchoked neighbor every x seconds, parsed in config file
        peerProc.currPeer.setChokeTimers();
    }
}
