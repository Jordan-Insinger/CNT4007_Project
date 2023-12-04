import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class Handler implements Runnable {
    private Socket socket;
    private ObjectOutputStream os;
    private ObjectInputStream is;

    private Peer peer;
    private Peer clientPeer;
    private peerProcess peerProc;

    private Message message;
    private Logger logger;

    private int index;
    private boolean first;

    public Handler(Socket socket, ObjectOutputStream os, ObjectInputStream is, Peer peer, peerProcess peerProc) {
        this.socket = socket;
        this.peer = peer;
        clientPeer = null;
        this.peerProc = peerProc;
        message = new Message(peer.getPeerID());
        logger = new Logger(peer.getPeerID());
        this.os = os;
        this.is = is;
        first = true;
    }

    public void run() { // decide what to do based on input bytes
        try {
            sendMessage(message.handshake(peer.getPeerID())); // initial handshake message
        } catch (IOException e) {
            e.printStackTrace();
        }
        receiveHandshake(); // check that return handshake is valid, then sends bitfield

        while (!peer.allHaveFile()) { // until confirms that all peers have the file, stay in handler loop
            try {
                byte[] incoming = (byte[]) is.readObject();
                // printByteMessage(incoming);

                byte[] length = new byte[4];
                System.arraycopy(incoming, 0, length, 0, 4);
                int messageLength = ByteBuffer.wrap(length).order(ByteOrder.BIG_ENDIAN).getInt();

                int messageType = incoming[4];

                byte[] payload;
                if (messageLength != 1) {
                    payload = new byte[messageLength - 1];
                    System.arraycopy(incoming, 5, payload, 0, messageLength - 1);
                } else {
                    payload = new byte[0];
                }

                switch (messageType) {
                    case 0: // choke
                        processChoke();
                        break;
                    case 1: // unchoke
                        processUnchoke();
                        break;
                    case 2: // interested
                        processInterested();
                        break;
                    case 3: // not interested
                        processNotInterested();
                        break;
                    case 4: // have
                        processHave(payload);
                        break;
                    case 5: // bitfield
                        first = false;
                        processBitfield(payload);
                        break;
                    case 6: // request
                        processRequest(payload);
                        break;
                    case 7: // piece
                        processPiece(payload);
                        break;
                }

                if (first) {
                    sendMessage(message.notinterestedMessage());
                    first = false;
                }
            } catch (StreamCorruptedException e) {

               
            } catch (IOException e) {
              
            } catch (ClassNotFoundException e) {
              
            }
        }
        System.exit(0);
        peerProc.shutdown();
    }

    public void shutdown() {
        try {
            is.close();
            os.close();
            socket.close();
            peer.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(byte[] toWrite) throws IOException {
        os.writeObject(toWrite);
        os.flush();
    }

    private static void printByteMessage(byte[] message) { // Command line debugging
        System.out.println("Printing Byte Message:");
        for (byte value : message) {
            // System.out.print((value & 0xFF) + " ");
            System.out.print(value + " ");
        }
        System.out.println("");
    }

    private void receiveHandshake() {
        byte[] incomingHandshake = new byte[32];
        try {
            incomingHandshake = (byte[]) is.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        byte[] header = new byte[18];
        System.arraycopy(incomingHandshake, 0, header, 0, 18);
        String incomingHeader = new String(header, StandardCharsets.UTF_8);

        int incomingPeerID = // convert last 4 bytes into int for peerID, could probably do it prettier
                ((incomingHandshake[28] & 0xFF) << 24) |
                        ((incomingHandshake[29] & 0xFF) << 16) |
                        ((incomingHandshake[30] & 0xFF) << 8) |
                        ((incomingHandshake[31] & 0xFF) << 0);

        // if incoming handshake is valid, send bitfield back
        if (getPeerFromID(incomingPeerID) && incomingHeader.equals("P2PFILESHARINGPROJ")) {
            logger.logTCPConnected(peer.getPeerID(), clientPeer.getPeerID());
            peer.addChoked(clientPeer);
            peer.removeUnchoked(clientPeer);
            peer.setOutputStream(os, clientPeer);
            try {
                if (peer.isValidBitfield(peer.getBitfield())) {
                    sendMessage(message.bitfieldMessage(peer));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean getPeerFromID(int incomingID) {
        for (Peer inpeer : peer.getPeerList()) {
            if (inpeer.getPeerID() == incomingID) {
                clientPeer = inpeer;
                return true;
            }
        }
        return false;
    }

    private void processChoke() {
        System.out.println("Received a choke message from peer: " + clientPeer.getPeerID());
        peer.addChoked(clientPeer); // todo, where to actually do this
        peer.removeUnchoked(clientPeer);
        logger.logChoked(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processUnchoke() {
        System.out.println("Received an unchoke message from peer: " + clientPeer.getPeerID());
        logger.logUnchoked(peer.getPeerID(), clientPeer.getPeerID());

        // based on peer bitfield, decide what index to requst and send, might need to
        // adjust, as might not actually need anything if it has the file already
        if (!peer.getHasFile()) {
            int indexToRequest = peer.calculateRequest(clientPeer);
            peer.removeChoked(clientPeer);
            peer.addUnchoked(clientPeer);
            try {
                if (indexToRequest != -1) {
                    sendMessage(message.requestMessage(indexToRequest));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processInterested() {
        System.out.println("Received an interested message from peer: " + clientPeer.getPeerID());
        peer.addInterested(clientPeer);
        logger.logInterested(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processNotInterested() {
        System.out.println("Received a not interested message from peer: " + clientPeer.getPeerID());
        peer.removeInterested(clientPeer);
        logger.logNotInterested(peer.getPeerID(), clientPeer.getPeerID());
    }

    private void processHave(byte[] payload) {
        System.out.println("Received a have message from peer: " + clientPeer.getPeerID());
        index = ((payload[0] & 0xFF) << 24) |
                ((payload[1] & 0xFF) << 16) |
                ((payload[2] & 0xFF) << 8) |
                ((payload[3] & 0xFF) << 0);

        clientPeer.updateBitfield(index);
        logger.logHave(peer.getPeerID(), clientPeer.getPeerID(), index);

        try {
            if (peer.isPeerInterested(clientPeer.getBitfield())) {
                sendMessage(message.interestedMessage());
            } else {
                sendMessage(message.notinterestedMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processBitfield(byte[] payload) {
        System.out.print("\nReceived a bitfield message from peer: " + clientPeer.getPeerID() + "\n");
        for (byte b : payload) {
            System.out.print(b);
        }
        System.out.println("");

        byte[] bitfield = peer.getBitfield();
        System.out.println("\nPeer " + peer.getPeerID() + " bitfield:");
        for (byte b : bitfield) {
            System.out.print(b);
        }
        System.out.println("");

        clientPeer.initializeBitfield(clientPeer.getHasFile());
        logger.logBitfield(peer.getPeerID(), clientPeer.getPeerID());

        try {
            // compare bitfields and return result, and send corresponding message
            boolean interested = peer.isPeerInterested(payload);
            System.out.println("Interested? " + interested + "\n");
            if (interested) {
                sendMessage(message.interestedMessage());
            } else {
                sendMessage(message.notinterestedMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processRequest(byte[] payload) { // todo
        System.out.println("Received a request message from peer: " + clientPeer.getPeerID());
        index = ((payload[0] & 0xFF) << 24) | // can probably use a more condensed way to do this
                ((payload[1] & 0xFF) << 16) |
                ((payload[2] & 0xFF) << 8) |
                ((payload[3] & 0xFF) << 0);
        try {
            if (!peer.getChokedList().contains(clientPeer)) {
                byte[] tosend = peer.getPiece(index);
                sendMessage(message.pieceMessage(index, tosend));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processPiece(byte[] payload) { // todo
        System.out.println("Received a piece message from peer: " + clientPeer.getPeerID());
        index = ((payload[0] & 0xFF) << 24) | // can probably use a more condensed way to do this
                ((payload[1] & 0xFF) << 16) |
                ((payload[2] & 0xFF) << 8) |
                ((payload[3] & 0xFF) << 0);

        byte[] pieceArr = new byte[payload.length - 4];
        System.arraycopy(payload, 4, pieceArr, 0, payload.length - 4);

        peer.setPiece(index, pieceArr);
        peer.updateBitfield(index);
        peer.incrementPieces();

        logger.logPieceDownloaded(peer.getPeerID(), clientPeer.getPeerID(), index, peer.getNumPieces());
        peer.updateBytesDownloaded(payload.length);
        try {
            for (Peer curr : peer.getPeerList()) {
                System.out.println(curr.getPeerID() + " : " + curr.getHasFile());
                if (curr.getObjectOutputStream() != null && curr.getPeerID() != peer.getPeerID()) {
                    System.out.println(curr.getPeerID() + " entered.\n");
                    curr.sendMessage(curr.getObjectOutputStream(), message.haveMessage(index));
                }
            }
            if (peer.getHasFile()) {
                System.out.println("Peer " + peer.getPeerID() + " has downloaded the complete file.");
                logger.logCompleteDownload(peer.getPeerID());
                peer.downloadFileToPeer();
                clientPeer.removeInterested(clientPeer);
                clientPeer.removeUnchoked(clientPeer);
                clientPeer.addChoked(clientPeer);
                // sendMessage(message.notinterestedMessage());
            } else {
                int indexToRequest = peer.calculateRequest(clientPeer);
                if (indexToRequest != -1) {
                    sendMessage(message.requestMessage(indexToRequest));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}