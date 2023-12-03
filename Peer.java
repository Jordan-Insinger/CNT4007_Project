import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.Vector;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
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
    private BitSet bitfield;
    private byte[] bitfieldArr;
    private byte[][] file;

    private Vector<Peer>  peerList;
    private Vector<Peer> prevPreferredNeighbors;
    private Hashtable<Integer,Peer> chokedList;
    private Hashtable<Integer,Peer> unchokedList;
    private Hashtable<Integer,Peer> interestedList;
    private Hashtable<Integer,Peer> hasFileList;
    private Peer currOptimistic;

    private long numDownloadedBytes;
    private int numPieces;
    private int targetNumPieces;
    private int bitfieldSize;

    private ScheduledExecutorService scheduler;

    private Message message;
    private Logger logger;

    private ObjectOutputStream os;


    // Constructor
    public Peer(int peerID) {
        this.peerID = peerID;
        numDownloadedBytes = 0;
        currOptimistic = null;
        message = new Message();
        logger = new Logger(peerID);
        interestedList = new Hashtable<Integer,Peer>();
        chokedList = new Hashtable<Integer,Peer>();
        unchokedList = new Hashtable<Integer,Peer>();
        hasFileList = new Hashtable<Integer,Peer>();
        prevPreferredNeighbors = new Vector<Peer>();
        numPieces = 0;
    }

    public void setGlobalConfig(int NumberOfPreferredNeighbors, int UnchokingInterval, int OptimisticUnchokingInterval, String fileName, int fileSize, int pieceSize){
        this.NumberOfPreferredNeighbors = NumberOfPreferredNeighbors;
        this.UnchokingInterval = UnchokingInterval;
        this.OptimisticUnchokingInterval = OptimisticUnchokingInterval;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        targetNumPieces = (int) Math.ceil((double)fileSize / pieceSize);
        bitfieldSize = (int) Math.ceil((double)targetNumPieces / 8);
        file = new byte[targetNumPieces][pieceSize];
        bitfield = new BitSet(targetNumPieces);
        bitfieldArr = new byte[bitfieldSize];
    }
    
    /*********************************GETTERS*************************************/
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
        return bitfieldArr;
    }

    public long getNumDownloaded(){
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

    public BitSet getBitFieldBitSet(){
        return bitfield;
    }

    public Hashtable<Integer,Peer> getHasFileList(){
        return hasFileList;
    }
    /*******************************END GETTERS***********************************/


    /********************************SETTERS**************************************/
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
        for(Peer curr : this.peerList){
            if(curr.getHasFile()){
                hasFileList.put(curr.getPeerID(), curr);
            }
        }
    }

    public void setOutputStream(ObjectOutputStream os, Peer inpeer){
        int index = peerList.indexOf(inpeer);
        peerList.get(index).os = os;
    }
    /****************************END SETTERS**************************************/


    /**********************NEIGHBOR PEER INFORMATION******************************/
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

    public void addHasFile(Peer peer){
        hasFileList.put(peer.getPeerID(), peer);
    }

    public boolean isPeerInterested(byte[] incomingBitfield){
        BitSet bits = BitSet.valueOf(incomingBitfield);

        for(int i = 0; i < targetNumPieces*8; i++){
            if(bits.get(i) && !bitfield.get(i)){
                return true;
            }
        }
        return false;
    }
    /********************END NEIGHBOR PEER INFORMATION****************************/


    /**************************FILE OPERATIONS************************************/
    public void setFile(String path){
        try{
            InputStream inputStream = Files.newInputStream(Paths.get(path));  
            byte[] piece = new byte[pieceSize];
            int fileIndex = 0;
            int bytesRead;
            while((bytesRead = inputStream.read(piece)) != -1){
                file[fileIndex] = Arrays.copyOf(piece,bytesRead);
                fileIndex++;
                Arrays.fill(piece, (byte) 0);
            }
            inputStream.close();
            downloadFileToPeer();
        }catch(IOException e){
            e.printStackTrace();
        }   
    }

    public void downloadFileToPeer(){
        FileOutputStream fileOutputStream = null;
        try{
            File newFile = new File("./Project/peer_"+ peerID);
            newFile.mkdirs();
            File fileLoc = new File(newFile, fileName);
            fileOutputStream = new FileOutputStream(fileLoc);

            for(int i = 0; i < targetNumPieces; i++){
                byte[] temp = file[i];
                fileOutputStream.write(temp);
            }

        }catch (FileNotFoundException e){
            System.out.println("Error saving downloaded file to disk.");
            e.printStackTrace();
        }catch (IOException e) {
            System.out.println("Error writing pieces to file.");
            e.printStackTrace();
        }finally {
            if(fileOutputStream != null){
                try{
                    fileOutputStream.flush();
                    fileOutputStream.close();
                }catch(IOException e){
                    System.out.println("Error closing file output stream");
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean allHaveFile(){
        for(Peer peer : peerList){
            if(!peer.hasFile){
                return false;
            }
        }
        return true;
    }

    public void checkHasFile(){
        if(numPieces == targetNumPieces){
            setHasFile(true);
        }
    }

    public void setPiece(int index, byte[] arr){
        file[index] = arr;
    }

    public void incrementPieces(){
        numPieces++;
    }
    /**************************END FILE OPERATIONS********************************/


    /************************BITFIELD OPERATIONS**********************************/
    public boolean isValidBitfield(byte[] bitfield){ 
        //checks if the bitfield for a peer has any pieces
        //if it has no pieces there is no point to sending the bitfield message
        for(byte b : bitfield){
            if (b != 0)
                return true;
        }
        return false;
    }

    public void initializeBitfield(boolean hasFile){
        //bitfield.set(0, bitfieldSize*8, false);
        //bitfield = new byte[bitfieldSize];  // sets all bytes to 0
        bitfieldArr = new byte[bitfieldSize];
        if(hasFile){   // if has file, set all bits in bitfield to 1
            bitfield.set(0, bitfieldSize*8, true);
            bitfieldArr = bitfield.toByteArray();
        }else{
            bitfield.set(0, bitfieldSize*8, false);
        }
        
    }

    public void updateBitfield(int index){ // called when get 'have' message
        bitfield.set(index);
        bitfieldArr = bitfield.toByteArray();
    }

    //https://www.baeldung.com/java-bitset
    //using bitset to compare bitfields
    public int calculateRequest(Peer inpeer){
        Vector<Integer> indices = new Vector<Integer>();

        BitSet copy = (BitSet) bitfield.clone();
        BitSet temp = inpeer.getBitFieldBitSet();
        copy.flip(0,targetNumPieces*8);
        copy.and(temp);

        for(int i = 0; i < targetNumPieces && i < targetNumPieces * 8 - (8 - targetNumPieces % 8); i++){
            if(copy.get(i) == true){
                 indices.add(i);
             }
        }

        if(indices.isEmpty()){
            return -1;
        }

        Collections.shuffle(indices);
        return indices.get(0);
    }
    /************************END BITFIELD OPERATIONS******************************/


    /*******************************CHOKING***************************************/
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

        if(interested.isEmpty()){
            return;
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
        for(int i = 0; i < Math.min(interested.size(), NumberOfPreferredNeighbors); i++){
            preferredNeighbors.add(interested.get(i));
        }

        for(Peer peer : preferredNeighbors){
            if(chokedList.containsKey(peer.getPeerID()) && !hasFileList.containsKey(peer.getPeerID())){
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
                if(peer == unchokedList.get(currID) || hasFileList.containsKey(peer.getPeerID())){
                    check = true;
                    break;
                }
            }

            if(check == false){
                try{
                    sendMessage(unchokedList.get(currID).getObjectOutputStream(), message.chokeMessage());
                    addChoked(unchokedList.get(currID));
                    removeChoked(unchokedList.get(currID));
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }

        for(int i = 0; i < Math.min(interested.size(), NumberOfPreferredNeighbors); i++){
            Peer curr = interested.get(i);
            if(chokedList.containsKey(curr.getPeerID()) && !hasFileList.containsKey(curr.getPeerID())){
                try{
                    int index = peerList.indexOf(curr);
                    sendMessage(peerList.get(index).getObjectOutputStream(), message.unchokeMessage());
                }catch(IOException e){
                    e.printStackTrace();
                }
                removeChoked(curr);
                addUnchoked(curr);
            }
        }

        if(!prevPreferredNeighbors.equals(preferredNeighbors)){
            // System.out.println("Preferred Neighbors:");
            // for(int i = 0; i < preferredNeighbors.size(); i++){
            //     System.out.print(preferredNeighbors.get(i).getPeerID() + " ");
            // }
            // System.out.println("\n");
            logger.logChangePreferredNeighbors(peerID, preferredNeighbors);
            prevPreferredNeighbors = preferredNeighbors;
        }

        //after calculate new preferred neighbors, reset numBytesDownloaded for next cycle and reselection
        resetBytesDownloaded();
    }

    public void reselectOptimistic(){
        Vector<Peer> possible = new Vector<Peer>();

        Enumeration<Integer> enumeration = interestedList.keys();
        while(enumeration.hasMoreElements()){
            int key = enumeration.nextElement();
            if(unchokedList.containsKey(key) && interestedList.containsKey(key) && hasFileList.containsKey(key)){
                possible.add(unchokedList.get(key));
            }
        }

        try{
            if(!possible.isEmpty()){
                Collections.shuffle(possible); //randomize, then pick first index
                currOptimistic = possible.get(0);
                //System.out.println("New Currently Optimistic " + currOptimistic.getPeerID());
                sendMessage(currOptimistic.getObjectOutputStream(), message.unchokeMessage());
                logger.logChangeUnchokedNeighbor(peerID, currOptimistic.peerID);
            }          
        }catch(IOException e){
            e.printStackTrace();
        }        
        //System.out.println("\nCurrent optimistically unchoked peer: " + currOptimistic.getPeerID());
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