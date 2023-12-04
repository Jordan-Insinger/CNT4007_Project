# P2P Project

## Group Number:  54
## Team Members + Contributions:

ID: 79229157 - Brayden Bevels: Reselecting preferred neighbors and the optimistically unchoked peer. Logger class for logging Peer processes to a log file. Have, request, and piece message sending and parsing.

ID: 65732071 - Jordan Insinger:  Current Peer and neighbor peer current progress upkeep using bitfields. File reading and writing. Created tcp connection bewteen peers.

ID: 52175756 - Seth Raber - I worked discussing how the function worked properly but worked specifically on the interested functions helped with the interested functions specifically as well as worked on the messages for the log.

 ## Youtube Video:


## What we Achieved:    

* All required messages and their handling are receieved and sent correctly.  
* All files get downloaded to their respective folders with no loss of data along with a log file that displays every received message and the progress of the file being sent over the TCP connection.  
* Each peer connects to all peers with ID's less than the current peer, and all config is read from a given config file along with the file to send.  
* Preferred neighbors and the optimistically unchoked neighbor get reselected every n and m seconds, respectively, with the values of n and m gotten from the Global Config file.  
* The eight different types of messages are all sent and parsed. These are: choked, unchoked, interested, not interested, have, bitfield, request, and piece messages.  
* Each peer keeps track of every other peer it is connected to via a bitfield, which updates the bit corresponding to the piece being downloaded when it gets a hava message.  
* Once all peers have the file, the program is finished, but the shutdown is not graceful. 

## Initial creation
Follow steps in order:

1. “tar -xvf 54.tar”  - to unzip the file to the folder named "54".
2. "cd ./54" - to move to root project directory 
3. “javac *.java” - to compile all needed java files
4. “Java peerProcess [PEER_ID]” - Will run a file using our peerProcess implementation. By default, it reads from the folder at "./configFiles/project_config_file_small/project_config_file_small."
