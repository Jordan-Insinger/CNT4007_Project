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
    private Vector<Peer> chokedList;
    private Vector<Peer> interestedList;
    private Peer currOptimistic;

    private int numDownloadedBytes;
    private int bitfieldSize;

    private ScheduledExecutorService scheduler;

    private Message message;
    private MessageLogger messageLogger;

    private OutputStream os;


    // Constructor
    public Peer(int peerID) {
        this.peerID = peerID;
        numDownloadedBytes = 0;
        currOptimistic = null;
        bitfieldSize = (fileSize * pieceSize) / 8;
        message = new Message();
        messageLogger = new MessageLogger();
        interestedList = new Vector<Peer>();
        chokedList = new Vector<Peer>();
        preferredNeighbors = new Vector<Peer>();
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

    public Vector<Peer> getPeerList(){
        return peerList;
    }

    public Vector<Peer> getInterestedList(){
        return interestedList;
    }

    public Vector<Peer> getChokedList(){
        return chokedList;
    }

    public OutputStream getOutputStream(){
        return os;
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
    
    public void setNeighbors(Vector<Peer> peerList){
        this.peerList = peerList;
        this.peerList.remove(this);
    }

    public void addInterested(Peer peer){
        interestedList.add(peer);
    }

    public void removeInterested(Peer peer){
        interestedList.remove(peer);
    }

    public void addChoked(Peer peer){
        chokedList.add(peer);
    }

    public void removeChoked(Peer peer){
        chokedList.remove(peer);
    }

    public void setOutputStream(OutputStream os, Peer inpeer){
        int index = peerList.indexOf(inpeer);
        peerList.get(index).os = os;
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
        if(interestedList.size() != 0){
            System.out.print("InterestedList: ");
            for(Peer temp : interestedList){
                System.out.print(temp.getPeerID() + " ");
            }
            System.out.println("\n");
        }

        if(this.hasFile && interestedList.size() != 0){
            Collections.shuffle(interestedList);
        }else{
            Collections.sort(interestedList, (peer1,peer2) -> {
                if(peer1.numDownloadedBytes / UnchokingInterval == peer2.numDownloadedBytes / UnchokingInterval){ //compare download rates
                    if(Math.random() > 0.5){
                        return 1;
                    }
                    return -1;
                }else{
                    return Double.compare(peer1.numDownloadedBytes / UnchokingInterval, peer2.numDownloadedBytes / UnchokingInterval);
                }
            });
            resetBytesDownloaded();
        }

        //System.out.println("After sorting");

        Vector<Peer> newPreferredNeighbors = new Vector<Peer>();
        Vector<Peer> compare = preferredNeighbors;
        for(int i = 0; i < Math.min(interestedList.size(), NumberOfPreferredNeighbors); i++){
            Peer curr = interestedList.get(i);
            //System.out.println("curr id: " + curr.getPeerID());
            if(chokedList.contains(curr)){
                //System.out.println("check init");
                try{
                    int index = peerList.indexOf(curr);
                    sendMessage(peerList.get(index).getOutputStream(), message.unchokeMessage());
                }catch(IOException e){
                    e.printStackTrace();
                }
                removeChoked(curr);
            }
            //System.out.println("check");            
            newPreferredNeighbors.add(curr);
            compare.remove(curr);
            //System.out.println("check2");
        }
        

        //System.out.println("after newPreferredNeighbors loop, ");

        for(int i = 0; i < compare.size(); i++){
            Peer curr = compare.get(i);
            if(!curr.equals(currOptimistic) && (chokedList.isEmpty() || !chokedList.contains(curr))){
                try{
                    int index = peerList.indexOf(curr);
                    sendMessage(peerList.get(index).getOutputStream(), message.chokeMessage());
                }catch(IOException e){
                    e.printStackTrace();
                }
                addChoked(curr);
            }
        }
        boolean changed = false;
        if(!preferredNeighbors.isEmpty() && !newPreferredNeighbors.isEmpty()){
            changed = !preferredNeighbors.equals(newPreferredNeighbors); //if the new and old are not equal, preferred neighbors changed, update vector send log
        }

        if(changed){
            preferredNeighbors = newPreferredNeighbors;
            messageLogger.log_Change_Preferred_Neighbors(peerID, preferredNeighbors);
        }

        System.out.print("Preferred neighbors: ");
            for(Peer temp : preferredNeighbors){
                System.out.print(temp.getPeerID() + " ");
            }
        System.out.println("\n");

        //after calculate new preferred neighbors, reset numBytesDownloaded for next cycle and reselection
        resetBytesDownloaded();
    }

    public void reselectOptimistic(){
        Vector<Peer> possible = new Vector<Peer>();
        for(int i = 0; i < peerList.size(); i++){
            Peer curr = peerList.get(i);
            if(interestedList.contains(curr) && chokedList.contains(curr)){
                possible.add(curr);
            }
        }
        System.out.println("fyck");
        try{
            if(!possible.isEmpty()){
                Collections.shuffle(possible); //randomize, then pick first index
                currOptimistic = possible.get(0);
                System.out.println("New Currently Optimistic " + currOptimistic.getPeerID());
                sendMessage(currOptimistic.getOutputStream(), message.unchokeMessage());
                messageLogger.log_Change_Unchoked_Neighbor(peerID, currOptimistic.peerID);
            }          
        }catch(IOException e){
            e.printStackTrace();
        }        
        System.out.println("\nCurrent optimistically unchoked peer: " + currOptimistic.getPeerID());
    }

    void updateBytesDownloaded(int bytesToAdd){
        numDownloadedBytes += bytesToAdd;
    }

    void resetBytesDownloaded(){
        for(Peer peer : peerList){
            peer.numDownloadedBytes = 0;
        }
    }

    void sendMessage(OutputStream os, byte[] message){
        try{
            os.write(message);
            os.flush();
        }catch(IOException e){
            System.err.println("Error when printing a choke or unchoke message");
            e.printStackTrace();
        }
    }

    /*****************************END CHOKING*************************************/
}