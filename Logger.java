import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;

public class Logger{
    private int peerID;
    private String fileName;

    Logger(int id){
        peerID = id;
        fileName = "log_peer_" + peerID + ".log";
    }


    public void logTCPConnection(int peerProcessID_1, int peerProcessID_2){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_1 + " makes a connection to Peer " + peerProcessID_2 + ".";
        writeLogMessage(message);
    }

    public void logTCPConnected(int peerProcessID_1, int peerProcessID_2){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_1 + " is connected from Peer " + peerProcessID_2 + ".";
        writeLogMessage(message);
    }

    public void logChangePreferredNeighbors(int peerProcessID, Vector<Peer> preferredNeighbors){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID + " has the new preferred neighbors ";

        for(int i = 0; i < preferredNeighbors.size(); i++){
            if(i == preferredNeighbors.size()-1){
                message = message + preferredNeighbors.get(i).getPeerID() + ".";
            }else{
                message = message + preferredNeighbors.get(i).getPeerID() + ", ";
            }
        }
        if(preferredNeighbors.isEmpty()){
            message = message + "[none].";
        }
        writeLogMessage(message);
    }

    public void logChangeUnchokedNeighbor(int peerProcessID_1, int peerProcessID_2){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_1 + " has the optimistically unchoked neighbor " + peerProcessID_2 + ".";
        writeLogMessage(message);
    }

    public void logUnchoked(int peerProcessID_Unchoked, int peerProcessID_Unchokes){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_Unchoked + " is unchoked by " + peerProcessID_Unchokes + ".";
        writeLogMessage(message);
    }

    public void logChoked(int peerProcessID_Choked, int peerProcessID_Chokes){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_Choked + " is choked by " + peerProcessID_Chokes + ".";
        writeLogMessage(message);
    }

    public void logRequest(int peerProcessID_1, int peerProcessID_2, int index){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_1 + " receives request for index " + index + " from " + peerProcessID_2 + ".";
        writeLogMessage(message);
    }

    public void logHave(int peerProcessID_Receiver, int peerProcessID_Sender, int pieceIndex){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_Receiver + " received the 'have' message from " + peerProcessID_Sender + " for the piece " + pieceIndex + ".";
        writeLogMessage(message);
    }

    public void logInterested(int peerProcessID_Receiver, int peerProcessID_Sender){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_Receiver + " received the 'interested' message from " + peerProcessID_Sender + ".";
        writeLogMessage(message);
    }

    public void logNotInterested(int peerProcessID_Receiver, int peerProcessID_Sender){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_Receiver + " received the 'not interested' message from " + peerProcessID_Sender + ".";
        writeLogMessage(message);
    }

    public void logPieceDownloaded(int peerProcessID_Receiver, int peerProcessID_Sender, int pieceIndex, int numPieces){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID_Receiver + " has downloaded the piece " + pieceIndex + " from Peer " + peerProcessID_Sender + ". Now the number of pieces it has is " + numPieces + ".";
        writeLogMessage(message);
    }

    public void logCompleteDownload(int peerProcessID){
        String message = "[" + getTimeString() + "]: Peer " + peerProcessID + " has downloaded the complete file.";
        writeLogMessage(message);
    }

    private void writeLogMessage(String message){
        try{
            FileWriter fstream = new FileWriter("./Project/" + fileName,true); //true for append mode
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(message);
            out.newLine();
            out.close();
            fstream.close();
        } catch (Exception e) {
            System.err.println("Error when writing to file named " + fileName);
            e.getMessage();
        }
    }

    private String getTimeString(){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy|HH.mm.ss");
        return LocalDateTime.now().format(formatter);
    }
}