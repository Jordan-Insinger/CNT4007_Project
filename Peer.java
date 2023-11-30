import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Set;
import java.util.Set;
import java.util.Vector;
import java.util.Set;
import java.util.HashSet;
public class Peer {
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
    Vector<Pair<Integer, byte[]>> peer_bitfields;
    private Set<Integer> interestedPeers;

    private Vector<Peer> peerList;
    // private Hashtable<Integer,Peer> peerList;

    private int numDownloadedBytes;
    private double downloadSpeed;


    private boolean interested;
    private boolean choked;
    private boolean optimisticallyUnchoked;

    private Socket socket;


    // Constructor
    public Peer(int peerID) {
        this.peerID = peerID;
        peer_bitfields = new Vector<>();
        interestedPeers = new HashSet<>();
        numDownloadedBytes = 0;
        downloadSpeed = 0;
        interested = false;
        choked = true;
        optimisticallyUnchoked = false;
        peer_bitfields = new Vector<>();
        interestedPeers = new HashSet<>();
        //  readConfigFile();
    }

    // mark a peer as interested
    public void markPeerAsInterested(int peerID) {
        interestedPeers.add(peerID);
    }
    // check if a peer is interested
    public boolean isPeerInterested(int peerID) {
        return interestedPeers.contains(peerID);
    }
    // remove peer from the set when it is no longer interested
    public void noLongerInterested(int peerID) {
        interestedPeers.remove(peerID);
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

    public double getDownloadSpeed(){
        return downloadSpeed;
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

    public void setDownloadSpeed(double downloadSpeed){
        this.downloadSpeed = downloadSpeed;
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

    // TO DO
        // find out if all Peers have downloaded full file
        // Vector of Peers and check that for all of them hasFile = 1

    // Helper Functions
    // public void sendMessage(byte[] message, ObjectOutputStream out){
    //     System.out.println("TEST");
    //     try{
    //         for (byte b : message) {
    //             System.out.print(String.format("%02X ", b));
    //         }
    //         out.writeObject(message);
    //         out.flush();
    //     } catch(IOException e) {
    //         System.out.println("IO Exception.");
    //         e.printStackTrace();
    //     }        
    // }

    boolean isValidBitfield(byte[] bitfield) { // checks if the bitfield for a peer has any pieces,
        // if it has no pieces there is no point to sending the bitfield message
        for (byte b : bitfield) {
            if (b != 0)
                return true;
        }
        return false;
    }

    void initializeBitfield(boolean hasFile) {
        bitfield = new byte[(fileSize / pieceSize) / 8];  // sets all bytes to 0

        if(hasFile) {   // if has file, set all bytes in bitfield to 1
            for (int i = 0; i < bitfield.length; i++) {
                bitfield[i] = (byte) 0x01;
            }
        }
    }

    // called when get 'have' message
    public void updateBitfield(byte[] incomingBitfield) {
        if (incomingBitfield.length > bitfield.length) {
            // if new bitfield is bigger than current, make this Peer's bitfield bigger, and
            // initialize to zero
            byte[] tempBitfield = new byte[incomingBitfield.length];

            //make sure previous pieces already in bitfield are saved
            for(int i = 0; i < bitfield.length; i++){
                int temp = tempBitfield[i] & bitfield[i];
                tempBitfield[i] = (byte) temp;
            }

            bitfield = tempBitfield;
        }
    }

    //return true if this peer is interested in the pieces the incoming peer has
    public boolean interestedFunc(byte[] incomingBitfield){
        //if bitfields are the same, dont need to do any calculation
        if(!incomingBitfield.equals(bitfield)){
            for(int i = 0; i < bitfield.length; i++){
                if(incomingBitfield[i] > bitfield[i]){
                    return true;
                }
            }
        }
        return false;
    }

    // every peer needs the common.cfg settings, run it within each peer?
}
