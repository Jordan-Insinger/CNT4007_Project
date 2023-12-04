import java.util.Vector;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import java.lang.Thread;

public class peerProcess {

    private int NumberOfPreferredNeighbors;
    private int UnchokingInterval;
    private int OptimisticUnchokingInterval;
    private String FileName;
    private int FileSize;
    private int PieceSize;

    private int initiator;

    private Vector<Peer> peersList;
    private Vector<Handler> handlerList;
    private Peer currPeer;
    private Logger logger;

    ServerSocket serverSocket;

    public volatile boolean shutdownFlag;

    peerProcess(int peerID){
        peersList = new Vector<Peer>();
        handlerList = new Vector<Handler>();
        logger = new Logger(peerID);
        shutdownFlag = false;
        initiator = peerID;
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
                peer.initializeBitfield(has);

                if(has){
                    peer.setFile("./configFiles/project_config_file_small/project_config_file_small/" + tokens[0] + "/" + FileName);
                    if(peer.getPeerID() == initiator){
                        peer.downloadFileToPeer();
                    }
                }

                if(currID == peer.getPeerID()){
                    currPeer = peer;
                }else{
                    peersList.add(peer); //all other peers, excluding current peer with given CLA ID integer
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found in readPeerInfo");
            e.printStackTrace();
        }
    }

    public void startServer(int listeningPort){
        serverSocket = null;
        try{
            serverSocket = new ServerSocket(listeningPort);
            while(!shutdownFlag){
                Socket clientSocket = serverSocket.accept();
                ObjectOutputStream os = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream is = new ObjectInputStream(clientSocket.getInputStream());
                Handler handler = new Handler(clientSocket, os, is, currPeer, this);
                handlerList.add(handler);
                new Thread(handler).start();
            }
        }catch(IOException e){
            //System.err.println("Error opening server socket on listening port " + listeningPort + ".");
            //e.printStackTrace();
        }finally{
            try{
                serverSocket.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    public void connectToLowerPeers(){
        Socket socket = null;
        for(Peer peer : peersList){
            if(shutdownFlag){
                break;
            }
            if(peer.getPeerID() < currPeer.getPeerID()){
                try{
                    socket = new Socket(peer.getHostName(), peer.getListeningPort());
                    ObjectOutputStream os = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
                    Handler handler = new Handler(socket, os, is, currPeer, this);
                    handlerList.add(handler);
                    logger.logTCPConnection(currPeer.getPeerID(), peer.getPeerID());
                    new Thread(handler).start();
                }catch(IOException e){
                    System.err.println("Error connecting to " + peer.getPeerID() + " from " + currPeer.getPeerID() + ".");
                    e.printStackTrace();
                }
            }
        }
    }

    public void shutdown(){
        shutdownFlag = true;
        System.out.println("Shutting down...");
        try{
            if(serverSocket != null && !serverSocket.isClosed()){
                serverSocket.close();
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        for(Handler handler : handlerList){
            handler.shutdown();
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
        peerProcess peerProc = new peerProcess(initiatorID);

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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            peerProc.shutdown();
        })); //can end program with Ctrl+C in command line
    }
}
