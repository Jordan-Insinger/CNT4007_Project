import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Vector;

public class Handler {
    private OutputStream oStream;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private ByteBuffer length;

    private peerProcess peerProc;

    private Socket clientSocket;
    private Peer peer;

    public Handler(Socket clientSocket, Peer peer, peerProcess peerProc) {
        this.clientSocket = clientSocket;
        this.peer = peer;
        this.peerProc = peerProc;
    }

    public void run() { // decide what to do based on input bytes
        boolean temp = true;
        while (temp) {
            try {
                InputStream is = clientSocket.getInputStream();
                OutputStream os = clientSocket.getOutputStream();
                byte[] length = new byte[4];
                is.read(length);
                int messageLength = ByteBuffer.wrap(length).getInt();
                byte[] incomingMsg = new byte[messageLength];
                is.read(incomingMsg);
                int messageType = (int) incomingMsg[0];

                int receivedPeerID = ByteBuffer.wrap(incomingMsg, 1, 4).getInt();

        

                Message message = new Message(peer.getPeerID());

                Vector<Peer> copyPeerList = peerProc.getPeers();

                switch (messageType) {
                    case 0: // choke

                    case 1: // unchoke
                        System.out.println("Received an unchoke message from peer: " + receivedPeerID);
                        int indexToRequest = -1;
                        for (int i = 0; i < copyPeerList.size(); i++) {
                            if (copyPeerList.get(i).getPeerID() == receivedPeerID) {
                                copyPeerList.get(i).setChoked(false);
                                indexToRequest = peerProc.calculateRequest(copyPeerList.get(i));
                            }
                        }

                        os.write(message.requestMessage(indexToRequest)); // todo, add random indexing of available
                                                                          // bitfield
                        os.flush();

                    case 2: // interested
                        System.out.println("Received an interested message from peer: " + receivedPeerID);
                        for (int i = 0; i < copyPeerList.size(); i++) {
                            if (copyPeerList.get(i).getPeerID() == receivedPeerID) {
                                copyPeerList.get(i).setInterested(true);
                                temp = false; // just to exit the loop for testing
                            }
                        }
                        peerProc.updatePeerList(copyPeerList);
                        break;

                    case 3: // not interested
                        System.out.println("Received a not interested message from peer: " + receivedPeerID);
                        if (peer.isPeerInterested(receivedPeerID)) {
                            peer.noLongerInterested(receivedPeerID);
                            temp = false;
                        }
                        break;

                    case 4: // have

                    case 5: // bitfield
                        System.out.println("Received a bitfield message from peer: " + receivedPeerID);
                        for (byte b : incomingMsg) {
                            System.out.print(b);
                        }
                        System.out.println();
                        byte[] bitfield = peer.getBitfield();

                        System.out.print("This peer's bitfield: ");
                        for (byte b : bitfield) {
                            System.out.print(b);
                        }
                        System.out.println();

                        // if this peer has any pieces, it sends a bitfield back
                        if (peer.isValidBitfield(bitfield)) {
                            byte[] sent_bitfield = message.bitfieldMessage(peer);
                            os.write(sent_bitfield);
                            os.flush();
                        }

                        // check if the received bitfield form the other peer contains any new pieces
                        boolean interested = false;
                        for (int i = 5; i < incomingMsg.length; i++) {
                            if (bitfield[i - 5] == 0 && incomingMsg[i] == 1) {
                                System.out.println("Interested in piece");
                                byte[] interestedMessage = message.interestedMessage();
                                interested = true;
                                os.write(interestedMessage);
                                os.flush();
                                break;
                            }
                        }
                        if (!interested) {
                            byte[] notinterestedMessage = message.notinterestedMessage();
                            os.write(notinterestedMessage);
                            os.flush();
                        }

                        break;
                    case 6: // request
                        break;

                    case 7: // piece
                        break;

                    // handshake message
                    default:
                        byte[] buffer = new byte[32];
                        is.read(buffer);
                        // i believe we need to do some check here to see if its the correct peer
                        String receivedMessage = new String(buffer, 0, 18);
                        int id = ByteBuffer.wrap(buffer, 28, 4).getInt();
                        System.out.println("Received Handshake: " + receivedMessage + " From peer:" + id);
                        
                        // if this is the initiating peer, send a handshake back, otherwise move on
                        if(peer.getPeerID() == peerProc.mainPeer.getPeerID()) {
                        byte[] handshakeMsg = message.handshake(id);
                        os.write(handshakeMsg);
                        }

                        // send bitfield
                        byte[] bitfieldMessage = message.bitfieldMessage(peer);
                        os.write(bitfieldMessage);
                        os.flush();
                 

                        break;
                }
                peerProc.updatePeerList(copyPeerList);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}