import java.util.Vector;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.net.*;
import java.io.*;

public class peerProcess {

    private int NumberOfPreferredNeighbors;
    private int UnchokingInterval;
    private int OptimisticUnchokingInterval;
    private String FileName;
    private int FileSize;
    private int PieceSize;

    public Vector<Peer> peerList = new Vector<Peer>();

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
                peer.setHasFile(Integer.parseInt(tokens[3]));
                peer.setGlobalConfig(NumberOfPreferredNeighbors, UnchokingInterval, OptimisticUnchokingInterval,
                        FileName, FileSize, PieceSize);
                peer.fileSize = FileSize;
                peer.fileName = FileName;
                peer.pieceSize = PieceSize;
                peer.NumberOfPreferredNeighbors = NumberOfPreferredNeighbors;
                peer.OptimisticUnchokingInterval = OptimisticUnchokingInterval;
                peer.UnchokingInterval = UnchokingInterval;
                peer.initializeBitfield(peer.getHasFile());
                peerList.add(peer);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Error has Occurred");
            e.printStackTrace();
        }
    }

    void setAllPeerLists() {
        for (int i = 0; i < peerList.size(); i++) {
            peerList.get(i).setPeerList(peerList);
        }
    }

    private static byte[] receiveHandshake(Socket clientSocket) {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            byte[] buffer = new byte[1024];

            inputStream.read(buffer);
            return buffer;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] receiveMessage(Socket clientSocket) {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            byte[] buffer = new byte[1024];
            inputStream.read(buffer);
            return buffer;
            /*
             * byte[] lengthBytes = new byte[4];
             * inputStream.read(lengthBytes);
             * int messageLength = new DataInputStream(new
             * java.io.ByteArrayInputStream(lengthBytes)).readInt();
             * 
             * byte[] messageBytes = new byte[messageLength];
             * inputStream.read(messageBytes);
             * 
             * return messageBytes;
             */

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;

    }

    private static void sendMessage(Socket socket, byte[] message) {
        try {

            OutputStream oStream = socket.getOutputStream();
            oStream.write(message);
            oStream.flush();

        } catch (IOException e) {

            e.printStackTrace();

        }
    }

    private static void printHandshake(byte[] handshake) { // handles receiving messages from client
        String receivedMessage = new String(handshake, 0, 18);
        int id = ByteBuffer.wrap(handshake, 28, 4).getInt();
        System.out.println("Received Message: " + receivedMessage);
        System.out.println("Received Handshake from client: " + id);
    }

    private static void printByteMessage(byte[] message) {
        System.out.println("Printing Byte Message:");
        for (byte value : message) {
            System.out.print((value & 0xFF) + " ");
        }
    }

    public static void main(String[] args) throws IOException {
        // FILEPATHS
        String configCommon = "configFiles/Common.cfg";
        String configPeerInfo = "configFiles/PeerInfo.cfg";

        int initiatorID = Integer.parseInt(args[0]);
        peerProcess peerProc = new peerProcess();
        peerProc.readConfigFile(configCommon);
        peerProc.readPeerInfo(configPeerInfo, initiatorID);

        // if first peer, listen to port 6008 and make no connections
        // find initiating peer:
        int initiatingPeerIndex = -1;
        for (int i = 0; i < peerProc.peerList.size(); i++) {
            if (initiatorID == peerProc.peerList.get(i).getPeerID())
                initiatingPeerIndex = i;
        }

        // create server socket and tcp connection to every peer before it
        for (int i = 0; i < peerProc.peerList.size(); i++) {
            Peer peer_ = peerProc.peerList.get(i);

            if (peer_.getPeerID() < initiatorID) {

                try {
                    // create tcp connection to peer
                    Socket socket = new Socket("localhost", peer_.getListeningPort());

                    // INITIATE HANDSHAKE
                    Message message = new Message();
                    byte[] handshake = message.handshake(initiatorID);
                    sendMessage(socket, handshake); 

                    // RECEIVE HANDSHAKE BACK FROM SERVER PEER:
                    byte[] receivedHandshake = receiveHandshake(socket);
                    printHandshake(receivedHandshake);

                    // SEND BITFIELD MESSAGE TO SERVER PEER IF THE BITFIELD IS NOT ALL 0's
                
                    byte[] bitfieldMessage = message.bitfieldMessage(peer_);
                    sendMessage(socket, bitfieldMessage);

                    // pass client peer over to the handler to receive messages back
                    Handler handle = new Handler(socket, peer_);
                    handle.run();  
                    System.out.println("Exiting Handler");


                    /*ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                        Handler messageHandler = new Handler(peer_, socket, in, out);
                    messageHandler.run();*/


                    // receive bitfield message from server
                    //byte[] receivedBitfield = receiveMessage(socket);
                    //printByteMessage(receivedBitfield);

                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else if (peer_.getPeerID() == initiatorID) {
                // create serverSocket
                try {
                    System.out.println("listening on port: " + peer_.getListeningPort());
                    ServerSocket serverSocket = new ServerSocket(peer_.getListeningPort());

                    while (true) {
                        Socket clientSocket = serverSocket.accept(); // listening to port, waiting for tcp
                                                                     // connection request from another peer
                        byte[] receivedHandshake = receiveHandshake(clientSocket);
                        printHandshake(receivedHandshake);

                        // send handshake back to client peer
                        Message message = new Message();
                        byte[] handshake = message.handshake(initiatorID);
                        sendMessage(clientSocket, handshake);

                        // start receiving messages from other peers
                        Handler handle = new Handler(clientSocket, peer_);
                        handle.run();

                        // =============== START RECEIVING MESSAGES FROM CLIENT PEERS =================
                        /*ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                        Handler messageHandler = new Handler(peer_, clientSocket, in, out);
                        messageHandler.run();*/
                        
                        //byte[] receivedBitfield = receiveMessage(clientSocket);
                        //printByteMessage(receivedBitfield);

                        // check if this server peer has any pieces, if it does, send a bitfield message
                        // back
                        //byte[] bitfield = message.bitfieldMessage(peerProc.peerList.get(initiatingPeerIndex));
                        //sendMessage(clientSocket, bitfield);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else
                break;
        }

    }
}