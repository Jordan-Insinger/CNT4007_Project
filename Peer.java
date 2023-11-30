import java.beans.PersistenceDelegate;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Vector;

public class Peer {

    // Common.cfg
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
    private int hasFile;

    // Other needed Peer variables
    private byte[] bitfield;

    private Vector<Peer> peerList;
    // private Hashtable<Integer,Peer> peerList;

    private Peer[] preferredNeighbors;
    // private int _blank_

    // Constructor
    public Peer(int peerID) {
        this.peerID = peerID;
        // readConfigFile();
    }

    // Getters
    public int getPeerID() {
        return peerID;
    }

    public String getHostName() {
        return hostName;
    }

    public int getListeningPort() {
        return listeningPort;
    }

    public int getHasFile() {
        return hasFile;
    }

    public byte[] getBitfield() {
        return bitfield;
    }

    // Setters
    public void setPeerID(int peerID) {
        this.peerID = peerID;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public void setListeningPort(int listeningPort) {
        this.listeningPort = listeningPort;
    }

    public void setHasFile(int hasFile) {
        this.hasFile = hasFile;
    }

    public void setGlobalConfig(int NumberOfPreferredNeighbors, int UnchokingInterval, int OptimisticUnchokingInterval,
            String fileName, int fileSize, int pieceSize) {
        this.NumberOfPreferredNeighbors = NumberOfPreferredNeighbors;
        this.UnchokingInterval = UnchokingInterval;
        this.OptimisticUnchokingInterval = OptimisticUnchokingInterval;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
    }

    public void setPeerList(Vector<Peer> peerList) {
        this.peerList = peerList;
    }

    // Helper Functions
    public void sendMessage(byte[] message, ObjectOutputStream out) {
        System.out.println("TEST");
        try {
            for (byte b : message) {
                System.out.print(String.format("%02X ", b));
            }
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.out.println("IO Exception.");
            e.printStackTrace();
        }
    }

    // TO DO
    public boolean allHaveFile() {
        // find out if all Peers have downloaded full file
        // Vector of Peers and check that for all of them hasFile = 1
        return false;
    }

    // TO DO
    public void chokeFunc() {

    }

    // TO DO
    public void unchokeFunc() {

    }

    boolean isValidBitfield(byte[] bitfield) { // checks if the bitfield for a peer has any pieces,
        // if it has no pieces there is no point to sending the bitfield message
        for (byte b : bitfield) {
            if (b != 0)
                return true;
        }
        return false;
    }

    void initializeBitfield(int hasFile) {
        bitfield = new byte[(fileSize / pieceSize) / 8]; // sets all bytes to 0

        if (hasFile == 1) { // if has file, set all bytes in bitfield to 1
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

            // make sure previous pieces already in bitfield are saved
            for (int i = 0; i < bitfield.length; i++) {
                int temp = tempBitfield[i] & bitfield[i];
                tempBitfield[i] = (byte) temp;
            }

            bitfield = tempBitfield;
        }
    }

    // return true if this peer is interested in the pieces the incoming peer has
    public boolean interestedFunc(byte[] incomingBitfield) {
        // if bitfields are the same, dont need to do any calculation
        if (!incomingBitfield.equals(bitfield)) {
            for (int i = 0; i < bitfield.length; i++) {
                if (incomingBitfield[i] > bitfield[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    // every peer needs the common.cfg settings, run it within each peer?
}
