import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;

public class MessageLogger {
    public void log_TCP_Connection(int peerProcessID_1, int peerProcessID_2){ 
        //pass time value from peerProcess?
        String filepath = "./Project/log_peer_" + peerProcessID_1 + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID_1 + " makes a connection to Peer " + peerProcessID_2 + ".";
        write_Log_Message(filepath, message);
    }

    public void log_Change_Preferred_Neighbors(int peerProcessID, Vector<Peer> preferredNeighbors){
        String filepath = "./Project/log_peer_" + peerProcessID + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID + " has the preferred neighbors ";

        for(int i = 0; i < preferredNeighbors.size(); i++){
            if(i == preferredNeighbors.size()-1){
                message = message + preferredNeighbors.get(i).getPeerID() + ".";
            }else{
                message = message + preferredNeighbors.get(i).getPeerID() + ",";
            }
        }
        write_Log_Message(filepath, message);
    }

    public void log_Change_Unchoked_Neighbor(int peerProcessID_1, int peerProcessID_2){
        String filepath = "./Project/log_peer_" + peerProcessID_1 + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID_1 + " has the optimistically unchoked neighbor " + peerProcessID_2 + ".";
        write_Log_Message(filepath, message);
    }

    public void log_Unchoke(int peerProcessID_Unchoked, int peerProcessID_Unchokes){
        String filepath = "./Project/log_peer_" + peerProcessID_Unchoked + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID_Unchoked + " is unchoked by " + peerProcessID_Unchokes + ".";
        write_Log_Message(filepath, message);
    }

    public void log_Choke(int peerProcessID_Choked, int peerProcessID_Chokes){
        String filepath = "./Project/log_peer_" + peerProcessID_Choked + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID_Choked + " is unchoked by " + peerProcessID_Chokes + ".";
        write_Log_Message(filepath, message);
    }

    public void log_Have_Message(int peerProcessID_Receiver, int peerProcessID_Sender, int pieceIndex){
        String filepath = "./Project/log_peer_" + peerProcessID_Receiver + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID_Receiver + " received the 'have' message from " + peerProcessID_Sender + " for the piece " + pieceIndex + ".";
        write_Log_Message(filepath, message);
    }

    public void log_Interested(int peerProcessID_Receiver, int peerProcessID_Sender){
        String filepath = "./Project/log_peer_" + peerProcessID_Receiver + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID_Receiver + " received the 'interested' message from " + peerProcessID_Sender + ".";
        write_Log_Message(filepath, message);
    }

    public void log_Not_Interested(int peerProcessID_Receiver, int peerProcessID_Sender){
        String filepath = "./Project/log_peer_" + peerProcessID_Receiver + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID_Receiver + " received the 'not interested' message from " + peerProcessID_Sender + ".";
        write_Log_Message(filepath, message);
    }

    public void log_Piece_Downloaded(int peerProcessID_Receiver, int peerProcessID_Sender, int pieceIndex, int numPieces){
        String filepath = "./Project/log_peer_" + peerProcessID_Receiver + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID_Receiver + " has downloaded the piece " + pieceIndex + " from " + peerProcessID_Sender + ". Now the number of pieces it has is " + numPieces + ".";
        write_Log_Message(filepath, message);
    }

    public void log_Complete_Download(int peerProcessID){
        String filepath = "./Project/log_peer_" + peerProcessID + ".log";
        String message = "[" + get_Time_String() + "]: Peer " + peerProcessID + " has downloaded the complete file.";
        write_Log_Message(filepath, message);
    }

    private void write_Log_Message(String filepath,String message){
        try{
            FileWriter fstream = new FileWriter(filepath,true); //true for append mode
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(message);
            out.newLine();
            out.close();
            fstream.close();
        } catch (Exception e) {
            System.err.println("Error when writing to file named " + filepath);
            e.getMessage();
        }
    }

    private String get_Time_String(){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy|HH.mm.ss");
        return LocalDateTime.now().format(formatter);
    }
}