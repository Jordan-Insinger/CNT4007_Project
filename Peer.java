import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.Vector;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
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
    private byte[][] file;

    private Vector<Peer>  peerList;
    private Hashtable<Integer,Peer> chokedList;
    private Hashtable<Integer,Peer> unchokedList;
    private Hashtable<Integer,Peer> interestedList;
    private Peer currOptimistic;

    private int numDownloadedBytes;
    private int numPieces;
    private int bitfieldSize;

    private ScheduledExecutorService scheduler;

    private Message message;
    private MessageLogger messageLogger;

    private ObjectOutputStream os;


    // Constructor
    public Peer(int peerID) {
        this.peerID = peerID;
        numDownloadedBytes = 0;
        currOptimistic = null;
        message = new Message();
        messageLogger = new MessageLogger();
        interestedList = new Hashtable<Integer,Peer>();
        chokedList = new Hashtable<Integer,Peer>();
        unchokedList = new Hashtable<>();
        numPieces = 0;
    }

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

    public Hashtable<Integer,Peer> getInterestedList(){
        return interestedList;
    }

    public Hashtable<Integer,Peer> getChokedList(){
        return chokedList;
    }

    public ObjectOutputStream getObjectOutputStream(){
        return os;
    }

    public byte[] getPiece(int index){
        return file[index];
    }

    public int getNumPieces(){
        return numPieces;
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
        interestedList.put(peer.getPeerID(), peer);
    }

    public void removeInterested(Peer peer){
        interestedList.remove(peer.getPeerID());
    }

    public void addChoked(Peer peer){
        chokedList.put(peer.getPeerID(), peer);
    }

    public void removeChoked(Peer peer){
        chokedList.remove(peer.getPeerID());
    }

    public void addUnchoked(Peer peer){
        unchokedList.put(peer.getPeerID(), peer);
    }

    public void removeUnchoked(Peer peer){
        unchokedList.remove(peer.getPeerID());
    }

    public void setFile(String path){
        file = new byte[numPieces][];
        try{
            byte[] incoming = Files.readAllBytes(Paths.get(path));
            for(int i = 0, fileIndex = 0; i < incoming.length; i += pieceSize, fileIndex++){
                byte piece[] = Arrays.copyOfRange(incoming,i,i+pieceSize);
                file[fileIndex] = piece;
            }     
            downloadFile();
        }catch(IOException e){
            e.printStackTrace();
        }   
    }

    public void setPiece(int index, byte[] arr){
        file[index] = arr;
    }

    public void incrementPieces(){
        numPieces++;
    }

    public void downloadFile(){
        FileOutputStream fileOutputStream = null;
        try {
            File newFile = new File("./Project/peer_"+ peerID);
            newFile.mkdirs();
            File fileLoc = new File(newFile, fileName);
            fileLoc.createNewFile();
            fileOutputStream = new FileOutputStream(fileLoc);

            for(int i = 0; i < numPieces; i++){
                fileOutputStream.write(file[i]);
            }

        } catch (FileNotFoundException e) {
            System.out.println("Error saving downloaded file to disk.");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Error writing pieces to file.");
            e.printStackTrace();
        } finally {
            if(fileOutputStream != null) {
                try {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch(IOException e) {
                    System.out.println("Error closing file output stream");
                    e.printStackTrace();
                }
            }
        }
    }

    public void setOutputStream(ObjectOutputStream os, Peer inpeer){
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
        numPieces = (int) Math.ceil((double)fileSize / pieceSize);
        bitfieldSize = (fileSize * pieceSize) / 8;
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
        for(int i = 0; i < bitfieldSize; i++){
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
        Enumeration<Integer> enumeration = interestedList.keys();
        Vector<Peer> interested = new Vector<Peer>();
        while(enumeration.hasMoreElements()){ 
            interested.add(interestedList.get(enumeration.nextElement()));
        }

        if(this.hasFile && !interestedList.isEmpty()){
            Collections.shuffle(interested);
        }else{
            Collections.sort(interested, (peer1,peer2) -> {
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

        Vector<Peer> preferredNeighbors = new Vector<Peer>();
        for(int i = 0; i < interested.size(); i++){
            preferredNeighbors.add(interested.get(i));
        }

        messageLogger.log_Change_Preferred_Neighbors(peerID, preferredNeighbors);

        for(Peer peer : preferredNeighbors){
            if(chokedList.containsKey(peer.getPeerID())){
                try{
                    removeChoked(peer);
                    addUnchoked(peer);
                    int index = peerList.indexOf(peer);
                    sendMessage(peerList.get(index).getObjectOutputStream(), message.unchokeMessage());
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }

        Enumeration<Integer> unchoked = unchokedList.keys();
        while(unchoked.hasMoreElements()){
            int currID = unchoked.nextElement();
            boolean check = false;
            for(Peer peer : preferredNeighbors){
                if(peer == unchokedList.get(currID)){
                    check = true;
                    break;
                }
            }

            if(check == false){
                try{
                    sendMessage(unchokedList.get(currID).getObjectOutputStream(), message.chokeMessage());
                    addChoked(unchokedList.get(currID));
                    removeInterested(unchokedList.get(currID));
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }


        for(int i = 0; i < Math.min(interested.size(), NumberOfPreferredNeighbors); i++){
            Peer curr = interested.get(i);
            if(chokedList.contains(curr.getPeerID())){
                try{
                    // Peer temp = null;
                    // for(Peer peer : peerList){
                    //     if(peer.getPeerID() == curr.getPeerID()){
                    //         temp = peer;
                    //     }
                    // }
                    int index = peerList.indexOf(curr);
                    sendMessage(peerList.get(index).getObjectOutputStream(), message.unchokeMessage());
                }catch(IOException e){
                    e.printStackTrace();
                }
                removeChoked(curr);
            }
        }

        //after calculate new preferred neighbors, reset numBytesDownloaded for next cycle and reselection
        resetBytesDownloaded();
    }

    public void reselectOptimistic(){
        Vector<Peer> possible = new Vector<Peer>();
        for(int i = 0; i < peerList.size(); i++){
            Peer curr = peerList.get(i);
            if(interestedList.containsKey(curr.getPeerID()) && chokedList.containsKey(curr.getPeerID())){
                possible.add(curr);
            }
        }
        try{
            if(!possible.isEmpty()){
                Collections.shuffle(possible); //randomize, then pick first index
                currOptimistic = possible.get(0);
                System.out.println("New Currently Optimistic " + currOptimistic.getPeerID());
                sendMessage(currOptimistic.getObjectOutputStream(), message.unchokeMessage());
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

    void sendMessage(ObjectOutputStream os, byte[] message){
        try{
            os.writeObject(message);
            os.flush();
        }catch(IOException e){
            System.err.println("Error when printing a choke or unchoke message");
            e.printStackTrace();
        }
    }

    /*****************************END CHOKING*************************************/
}