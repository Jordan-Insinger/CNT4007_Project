import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.HashSet;
public class Peer{

    //Common.cfg
    int NumberOfPreferredNeighbors;
    int UnchokingInterval;
    int OptimisticUnchokingInterval;
    String fileName;
    int fileSize;
    int pieceSize;

    // PeerInfo.cfg
    private int peerID;
    private String hostName;
    private int listeningPort;
    private boolean hasFile;

    // Other needed Peer variables
    private byte[] bitfield;
    //Vector<Pair<Integer, byte[]>> peer_bitfields; //not sure if this is needed, each Peer has a bitfield variable, and already have peerList
    private Set<Integer> interestedPeers;

    private Vector<Peer> peerList;
    private Vector<Peer> preferredNeighbors;
    private Peer currOptimistic;

    private int numDownloadedBytes;
    private int bitfieldSize;

    private boolean interested;
    private boolean choked;
    private boolean optimisticallyUnchoked;

    private Socket socket;
    private ScheduledExecutorService scheduler;

    private Message message;
    private MessageLogger messageLogger;


    // Constructor
    public Peer(int peerID) {
        this.peerID = peerID;
        //peer_bitfields = new Vector<>();
        interestedPeers = new HashSet<>();
        numDownloadedBytes = 0;
        interested = false;
        choked = true;
        optimisticallyUnchoked = false;
        currOptimistic = null;
        bitfieldSize = (fileSize * pieceSize) / 8;
        message = new Message();
        messageLogger = new MessageLogger();
    }

    // // mark a peer as interested
    // public void markPeerAsInterested(int peerID) {
    //     interestedPeers.add(peerID);
    // }
    // // check if a peer is interested
    // public boolean isPeerInterested(int peerID) {
    //     return interestedPeers.contains(peerID);
    // }
    // // remove peer from the set when it is no longer interested
    // public void noLongerInterested(int peerID) {
    //     interestedPeers.remove(peerID);
    // }

    // Getters
    public int getPeerID() {
        return peerID;
    }

    public String getHostName(){
        return hostName;
    }

    public int getListeningPort(){
        return listeningPort;
    }

    public boolean getHasFile(){
        return hasFile;
    }

    public byte[] getBitfield(){
        return bitfield;
    }

    public int getNumDownloaded(){
        return numDownloadedBytes;
    }

    public boolean getInterested(){
        return interested;
    }

    public boolean getChoked(){
        return choked;
    }

    public boolean getOptimisticallyUnchoked(){
        return optimisticallyUnchoked;
    }

    public Socket getSocket(){
        return socket;
    }

    public Vector<Peer> getPeerList(){
        return peerList;
    }

    //Setters
    public void setPeerID(int peerID){
        this.peerID = peerID;
    }  

    public void setHostName(String hostName){
        this.hostName = hostName;
    }    

    public void setListeningPort(int listeningPort){
        this.listeningPort = listeningPort;
    }
    
    public void setHasFile(boolean hasFile){
        this.hasFile = hasFile;
    }

    public void setNumDownloaded(int numDownloadedBytes){
        this.numDownloadedBytes = numDownloadedBytes;
    }

    public void setInterested(boolean interested){
        this.interested = interested;
    }

    public void setChoked(boolean choked){
        this.choked = choked;
    }

    public void setSocket(Socket socket){
        this.socket = socket;
    }
    
    public void setNeighbors(Vector<Peer> peerList){
        this.peerList = peerList;
    }

    public void setOptimisticallyUnchoked(boolean optimisticallyUnchoked){
        this.optimisticallyUnchoked = optimisticallyUnchoked;
    }

    public void setGlobalConfig(int NumberOfPreferredNeighbors, int UnchokingInterval, int OptimisticUnchokingInterval, String fileName, int fileSize, int pieceSize){
        this.NumberOfPreferredNeighbors = NumberOfPreferredNeighbors;
        this.UnchokingInterval = UnchokingInterval;
        this.OptimisticUnchokingInterval = OptimisticUnchokingInterval;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
    }

    public boolean isValidBitfield(byte[] bitfield){ // checks if the bitfield for a peer has any pieces,
        // if it has no pieces there is no point to sending the bitfield message
        for(byte b : bitfield){
            if (b != 0)
                return true;
        }
        return false;
    }

    public void initializeBitfield(boolean hasFile){
        this.bitfield = new byte[(fileSize / pieceSize) / 8];  // sets all bytes to 0

        if(this.hasFile){   // if has file, set all bytes in bitfield to 1
            for(int i = 0; i < this.bitfield.length; i++){
                this.bitfield[i] = (byte) 0x01;
            }
        }
    }

    // called when get 'have' message
    public void updateBitfield(int index){
        int bitfieldIndex = (index / 8) - 1;
        int byteIndex = index % 8;
        this.bitfield[bitfieldIndex] |= 0b1000000 >> byteIndex;
        /*
        update given piece bit, start from the left
        0000 1100 0101 1100

        leftmost bit is bit 0 of byte 0
        first 1 value is bit 4 of byte 0, etc

        if index 10 is given,
        0101 1100 | 0010000 = 0111 0000
        */
    }

    //return true if this peer is interested in the pieces the incoming peer has
    public boolean isPeerInterested(byte[] incomingBitfield){
        //if bitfields are the same, dont need to do any calculation
        if(!incomingBitfield.equals(this.bitfield)){
            for(int i = 0; i < this.bitfield.length; i++){
                if(incomingBitfield[i] > this.bitfield[i]){
                    return true;
                }
            }
        }
        return false;
    }

    public int calculateRequest(byte[] incomingBitfield){
        if(this.hasFile){
            return -1;
        }
        Vector<Integer> indices = new Vector<Integer>();
        for(int i = 0; i < bitfieldSize*8; i++){
            if(incomingBitfield[i] == 1 && this.bitfield[i] == 0){
                indices.add(i);
            }
        }
        Collections.shuffle(indices);
        return indices.get(0);
    }

    public boolean allHaveFile(){
        for(int i = 0; i < peerList.size(); i++){
            if(!peerList.get(i).getHasFile()){
                return false;
            }
        }
        return true;
    }

    /*********************************CHOKING*******************************************/

    public void setChokeTimers(){
        scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(() -> 
            reselectPreferred(),0,UnchokingInterval, TimeUnit.SECONDS
        );
        scheduler.scheduleAtFixedRate(() -> 
            reselectOptimistic(),0,OptimisticUnchokingInterval,TimeUnit.SECONDS
        );
    }

    public void shutdown(){
        scheduler.shutdown();
    }

    public void reselectPreferred(){
        Vector<Peer> interestedPeers = new Vector<Peer>();
        for(int i = 0; i < peerList.size(); i++){
            Peer curr = peerList.get(i);
            if(curr.getInterested()){
                interestedPeers.add(curr);
            }
        }

        if(this.hasFile){
            Collections.shuffle(interestedPeers);
        }else{
            Collections.sort(interestedPeers, (peer1,peer2) -> {
                if(peer1.numDownloadedBytes / UnchokingInterval == peer2.numDownloadedBytes / UnchokingInterval){ //compare download rates
                    if(Math.random() > 0.5){
                        return 1;
                    }
                    return -1;
                }else{
                    return Double.compare(peer1.numDownloadedBytes / UnchokingInterval, peer2.numDownloadedBytes / UnchokingInterval);
                }
            });
        }

        Vector<Peer> newPreferredNeighbors = new Vector<Peer>();
        Vector<Peer> compare = preferredNeighbors;
        try{
            for(int i = 0; i < Math.min(interestedPeers.size(), NumberOfPreferredNeighbors); i++){
                Peer curr = interestedPeers.get(i);
                if(curr.getChoked()){
                    sendMessage(curr.socket, message.unchokeMessage());
                    curr.choked = false;
                    newPreferredNeighbors.add(curr);
                    preferredNeighbors.remove(curr);
                }
            }

            for(int i = 0; i < preferredNeighbors.size(); i++){
                Peer curr = preferredNeighbors.get(i);
                if(!curr.optimisticallyUnchoked){
                    sendMessage(curr.socket, message.chokeMessage());
                    curr.choked = true;
                }
            }
            boolean changed = !compare.equals(newPreferredNeighbors); //if the new and old are not equal, preferred neighbors changed, update vector send log

            if(changed){
                preferredNeighbors = newPreferredNeighbors;
                messageLogger.log_Change_Preferred_Neighbors(peerID, preferredNeighbors);
            }

            //after calculate new preferred neighbors, reset numBytesDownloaded for next cycle and reselection
            resetBytesDownloaded();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public void reselectOptimistic(){
        Vector<Peer> possible = new Vector<Peer>();
        for(int i = 0; i < peerList.size(); i++){
            Peer curr = peerList.get(i);
            if(curr.interested && curr.choked){
                possible.add(curr);
            }
        }

        Collections.shuffle(possible); //randomize, then pick first index

        try{
            if(currOptimistic != null){
                if(possible.size() != 0 && possible.get(0).equals(currOptimistic)){ //if unchanged and no other possible, do nothing and return early
                    return;
                }
                sendMessage(currOptimistic.socket, message.chokeMessage()); //else choke old optimistic peer
            }
            if(possible.size() != 0){ //if none possible, optimistic peer not updated
                currOptimistic = possible.get(0);
                sendMessage(currOptimistic.getSocket(), message.unchokeMessage());
                messageLogger.log_Change_Unchoked_Neighbor(peerID, currOptimistic.peerID);
            }            
        }catch(IOException e){
            e.printStackTrace();
        }        
    }

    void updateBytesDownloaded(int bytesToAdd){
        numDownloadedBytes += bytesToAdd;
    }

    void resetBytesDownloaded(){
        for(Peer peer : peerList){
            peer.numDownloadedBytes = 0;
        }
    }

    void sendMessage(Socket socket, byte[] message){
        try{
            OutputStream os = socket.getOutputStream();
            os.write(message);
            os.flush();
            os.close();
        }catch(IOException e){
            System.err.println("Error when printing a choke or unchoke message");
            e.printStackTrace();
        }
    }

    /*****************************END CHOKING*************************************/
}