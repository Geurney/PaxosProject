package paxos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class Server {
	/**
	 * Server ID
	 */
	private int ID;

	/**
	 * Local host name
	 */
	private InetAddress hostname;

	/**
	 * Local host port
	 */
	private int port;

	/**
	 * Server Address
	 */
	private final ArrayList<String[]> serverAddress;

	/**
	 * FAIL WAIT AFTER_PREPARE AFTER_SENDACCEPT
	 */
	private static enum STATUSTYPE {
		FAIL, WAIT, AFTER_PREPARE, AFTER_SENDACCEPT
	};

	private volatile STATUSTYPE STATUS;

	/**
	 * Normal Recovery
	 */
	private static enum MODETYPE {
		NORMAL, RECOVERY
	};

	private volatile MODETYPE MODE;

	/**
	 * BallotNum, ID
	 */
	private int[] BallotNum;

	/**
	 * Accept BallotNum, ID
	 */
	private int[] AcceptNum;

	/**
	 * Index, Content, BallotNum,
	 */
	private String[] AcceptVal;

	/**
	 * Ack counter
	 */
	private int ACKCount;

	/**
	 * Accept Counter
	 */
	private int ACPCount;

	/**
	 * Majority
	 * 
	 * @throws IOException
	 */
	private static int MAJORITY = 3;

	/**
	 * CLI Thread
	 */
	private CLIThread CIL;

	/**
	 * Communication Thread
	 */
	private COMMThread COMM;

	/**
	 * Log
	 */
	private ArrayList<String> log;

	/**
	 * Client Msg
	 */
	private String[] clientMsg;

	/**
	 * Max ACK Number received
	 */
	private int[] MaxACKNum;

	/**
	 * Max ACK Value
	 */
	private String[] MaxACKVal;

	private ServerTimer serverTimer;

	private static boolean Debug = false;

	public Server(ArrayList<String[]> config) throws IOException {
		serverAddress = config;
		ID = Integer.parseInt(serverAddress.get(0)[0]);
		hostname = InetAddress.getByName(serverAddress.get(6)[0]);
		port = Integer.parseInt(serverAddress.get(6)[1]);
		// Status and Mode
		STATUS = STATUSTYPE.WAIT;
		MODE = MODETYPE.NORMAL;
		// BallotNum
		BallotNum = new int[3];
		BallotNum[0] = 0;
		BallotNum[1] = 0;
		BallotNum[2] = -1;
		// Accept Num and Accept Val
		AcceptNum = new int[3];
		AcceptNum[0] = 0;
		AcceptNum[1] = 0;
		AcceptNum[2] = -1;
		AcceptVal = new String[2];
		// ACK count
		ACKCount = 0;
		// ACP count
		ACPCount = 0;
		// log
		log = new ArrayList<String>();
		// Client Msg
		clientMsg = new String[2];
		// MaxACKNum and MaxACKVal
		MaxACKNum = new int[3];
		MaxACKNum[0] = -1;
		MaxACKNum[1] = -1;
		MaxACKNum[2] = -1;
		MaxACKVal = new String[3];
		// Thread
		COMM = new COMMThread();
		CIL = new CLIThread();
		serverTimer = new ServerTimer();

	}

	public void start() {
		CIL.start();
		COMM.start();
	}

	/**
	 * Command Line Interface Thread
	 *
	 */
	private class CLIThread extends Thread {
		@SuppressWarnings("resource")
		@Override
		public void run() {
			String command;
			Scanner sc = null;
			sc = new Scanner(System.in);
			System.out.println("Site" + ID + ": Please enter a command:");
			while (true) {
				command = sc.nextLine();
				synchronized (STATUS) {
					switch (STATUS) {
					case FAIL:
						fail_process(command);
						break;
					default:
						default_process(command);
						break;
					}
				}
			}
		}

		/**
		 * CLI Process in FAIL status process command
		 * 
		 * @param cmd
		 */
		private void fail_process(String cmd) {
			if (cmd.equals("Fail")) {
				return;
			} else if (cmd.equals("Restore")) {
				Socket socket;
				try {
					socket = new Socket(hostname, port);
				} catch (NumberFormatException | IOException e) {
					e.printStackTrace();
					return;
				}
				PrintWriter out = null;
				try {
					out = new PrintWriter(socket.getOutputStream(), true);
					out.println("wake");
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		/**
		 * CLI Process in Other status process command
		 * 
		 * @param cmd
		 */
		private void default_process(String cmd) {
			if (cmd.equals("Fail")) {
				clientMsg = new String[2];
				MaxACKNum[0] = -1;
				MaxACKNum[1] = -1;
				ACKCount = 0;
				ACPCount = 0;
				STATUS = STATUSTYPE.FAIL;
			}
		}
	}

	/**
	 * Communication Thread
	 * 
	 */
	private class COMMThread extends Thread {
		@SuppressWarnings("resource")
		@Override
		public void run() {
			ServerSocket serverSocket;
			try {
				serverSocket = new ServerSocket(port, 5, hostname);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			while (true) {
				Socket socket;
				try {
					socket = serverSocket.accept();
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}
				BufferedReader in;
				try {
					in = new BufferedReader(new InputStreamReader(
							socket.getInputStream()));
				} catch (IOException e) {
					e.printStackTrace();
					try {
						socket.close();
						socket = null;
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					continue;
				}
				String input;
				try {
					input = in.readLine();
				} catch (IOException e) {
					e.printStackTrace();
					try {
						socket.close();
						socket = null;
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					continue;
				}
				synchronized (STATUS) {
                    System.out.println("INPUT: " + input);
					if (Debug) {
						
						System.out.println("CURRENT STATE: " + STATUS);

						System.out.println("	BallotNum: " + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2]);
						System.out.println("	AcceptNum: " + AcceptNum[0] + ","
								+ AcceptNum[1] + "," + AcceptNum[2]);
						System.out.println("	AcceptVal: " + AcceptVal[0] + ","
								+ AcceptVal[1]);
					}
					switch (STATUS) {
					case FAIL: {
						String cmd[] = input.split("\"");
						String operation = cmd[0];
						if (operation.equals("wake")) {
							String msg = "help\"" + ID;
							STATUS = STATUSTYPE.WAIT;
                            serverTimer = new ServerTimer();
                            serverTimer.startTimer();
							MODE = MODETYPE.RECOVERY;
							for (int i = 0; i < 5; i++) {
								if (i != ID)
									send(msg, i);
							}

							System.out.println("	" + "Send out help to all...");

						}
					}
						break;
					case WAIT:
						wait_process(input);
						break;
					case AFTER_PREPARE:
						prepare_process(input);
						break;
					case AFTER_SENDACCEPT:
						sendaccept_process(input);
						break;
					default:
						break;
					}
				}
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (Debug) {
					System.out.println("****BallotNum: " + BallotNum[0] + ","
							+ BallotNum[1] + "," + BallotNum[2]);
					System.out.println("****AcceptNum: " + AcceptNum[0] + ","
							+ AcceptNum[1] + "," + AcceptNum[2]);
					System.out.println("****AcceptVal: " + AcceptVal[0] + ","
							+ AcceptVal[1]);
				}
			}
		}

		/**
		 * Send msg to Server
		 * 
		 * @param msg
		 * @param serverID
		 */
		private void send(String msg, int serverID) {
			Socket socket;
			try {
				socket = new Socket(serverAddress.get(serverID + 1)[0],
						Integer.parseInt(serverAddress.get(serverID + 1)[1]));
			} catch (NumberFormatException | IOException e1) {
				e1.printStackTrace();
				return;
			}
			PrintWriter out = null;
			try {
				out = new PrintWriter(socket.getOutputStream(), true);
				out.println(msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}

		/**
		 * Send Reply message to Client.
		 * 
		 * @param address
		 *            Client Address
		 * @param msg
		 *            Msg to send
		 */
		private void reply(String address, String msg) {
			String[] address_split = address.split("\'");
			Socket socket;
			try {
				socket = new Socket(address_split[0],
						Integer.parseInt(address_split[1]));
			} catch (NumberFormatException | IOException e) {
				e.printStackTrace();
				return;
			}
			PrintWriter out = null;
			try {
				out = new PrintWriter(socket.getOutputStream(), true);
				out.println(msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (Debug) {
				System.out.println("	send " + msg + " to client "
						+ address_split[0]);
			}
		}

		/**
		 * Process help
		 * 
		 * @param serverID
		 *            Help request source
		 * @param AcceptVal
		 */
		private void process_help(int serverID) {
			// help"ID
			StringBuffer msg = new StringBuffer();
			msg.append("log\"");
			for (String i : log) {
				msg.append(i);
				msg.append("\"");
			}
			msg.deleteCharAt(msg.length() - 1);
			send(msg.toString(), serverID);
			if (Debug) {
				System.out.println("	" + " send " + msg + " to " + serverID);
			}
		}

		/**
		 * Process read request. In all status
		 * 
		 * @param address
		 */
		private void process_read(String address) {
			String msg;
			if (MODE == MODETYPE.RECOVERY) {
				msg = "Unable to read. Server is recovering...";
			} else {
				StringBuffer sb = new StringBuffer();
				sb.append("Log\"");
				for (String i : log) {
					sb.append(i);
					sb.append("\"");
				}
				sb.deleteCharAt(sb.length() - 1);
				msg = sb.toString();
			}
			reply(address, msg);
			System.out.println("	Send log to client.");
		}

		/**
		 * Process log
		 * 
		 * @param cmd
		 */
		private void process_log(String[] cmd) {
			// log"Hello'1,1,0"Good'2,1,1"Howareyou'3,2,2"Imfine'4,3,3
			// Hello'1,1,0"Good'2,1,1
			// cmd.length-1=4 receive log length
			// log.size=2 my log length
			if (MODE == MODETYPE.RECOVERY) {
				MODE = MODETYPE.NORMAL;
			}
			if (log.size() == 0) {
				if (cmd.length - 1 > 0) {
					for (int i = 1; i < cmd.length; i++) {
						log.add(cmd[i]);
					}
					String[] lastEntry = log.get(log.size() - 1).split("\'");
					String[] lastEntry_num = lastEntry[1].split(",");
					int[] lastNum = { Integer.parseInt(lastEntry_num[0]),
							Integer.parseInt(lastEntry_num[1]),
							Integer.parseInt(lastEntry_num[2]) };
					if (AcceptNum[2] < lastNum[2]) {
						AcceptNum[0] = lastNum[0];
						AcceptNum[1] = lastNum[1];
						AcceptNum[2] = lastNum[2];
						AcceptVal[0] = lastEntry[0];
						AcceptVal[1] = lastEntry[1];
						if (BallotNum[2] < AcceptNum[2]) {
							BallotNum[0] = AcceptNum[0];
							BallotNum[1] = AcceptNum[1];
							BallotNum[2] = AcceptNum[2];
						}
					}

				}
			} else {
				if (cmd.length - 1 >= log.size()) {
					String[] myAccept_string = log.get(log.size() - 1).split(
							"\'")[1].split(",");
					String[] yourAccept_string = cmd[log.size()].split("\'")[1]
							.split(",");
					int[] myAccept = { Integer.parseInt(myAccept_string[0]),
							Integer.parseInt(myAccept_string[1]) };
					int[] yourAccept = {
							Integer.parseInt(yourAccept_string[0]),
							Integer.parseInt(yourAccept_string[1]) };
					if (yourAccept[0] > myAccept[0]
							|| (yourAccept[0] == myAccept[0] && yourAccept[1] > myAccept[1])) {
						log.set(log.size() - 1, cmd[log.size()]);
					}
					int index = log.size() + 1;
					for (int i = index; i < cmd.length; i++) {
						log.add(cmd[i]);
					}
					String[] lastEntry = log.get(log.size() - 1).split("\'");
					String[] lastEntry_num = lastEntry[1].split(",");
					int[] lastNum = { Integer.parseInt(lastEntry_num[0]),
							Integer.parseInt(lastEntry_num[1]),
							Integer.parseInt(lastEntry_num[2]) };
					if (AcceptNum[2] < lastNum[2]) {
						AcceptNum[0] = lastNum[0];
						AcceptNum[1] = lastNum[1];
						AcceptNum[2] = lastNum[2];
						AcceptVal[0] = lastEntry[0];
						AcceptVal[1] = lastEntry[1];
						if (BallotNum[2] < AcceptNum[2]) {
							BallotNum[0] = AcceptNum[0];
							BallotNum[1] = AcceptNum[1];
							BallotNum[2] = AcceptNum[2];
						}
					}
				}
			}
		}

		/**
		 * Send help
		 */
		private void sendHelp() {
			String msg = "help\"" + ID;
			for (int i = 0; i < 5; i++) {
				if (i != ID)
					send(msg, i);
			}
		}

		/**
		 * In wait status
		 * 
		 * @param input
		 */
		private void wait_process(final String input) {
			String cmd[] = input.split("\"");
			String operation = cmd[0];
			switch (operation) {
			case "post": {
				// post"192.168.21.11'8001"Hello how are you?
				clientMsg[0] = cmd[1]; // 192.168.21.11'8001
				clientMsg[1] = cmd[2]; // Hello how are you?
				if (clientMsg[1].length() > 140) {
					clientMsg[1] = clientMsg[1].substring(0, 140);
				}
				BallotNum[0] = BallotNum[0] + 1;
				BallotNum[1] = ID;
				BallotNum[2] = log.size();
				MaxACKNum[0] = -1;
				MaxACKNum[1] = -1;
				MaxACKNum[2] = -1;
				MaxACKVal = new String[2];
				// prepare"1,1,1
				String msg = "prepare\"" + BallotNum[0] + "," + BallotNum[1]
						+ "," + BallotNum[2];
				for (int i = 0; i < 5; i++) {
					if (i != ID)
						send(msg, i);
				}
				ACKCount = 1;
				serverTimer.cancel();
				STATUS = STATUSTYPE.AFTER_PREPARE;
				serverTimer = new ServerTimer();
				serverTimer.startTimer();
				System.out.println("	Prepare <" + BallotNum[0] + ","
						+ BallotNum[1] + "," + BallotNum[2] + ">");
				if (Debug) {
					System.out.println("	" + " send " + msg + " to all");
					System.out.println("	STATE CHANGE TO " + STATUS);
				}
			}
				break;
			// prepare"b[0],b[1],b[2]
			case "prepare": {
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]),
						Integer.parseInt(ballot_string[2]) };
				if (ballot[2] == log.size()) {
					if (ballot[0] > BallotNum[0]
							|| (ballot[0] == BallotNum[0] && ballot[1] > BallotNum[1])) {
						BallotNum[0] = ballot[0];
						BallotNum[1] = ballot[1];
						BallotNum[2] = ballot[2];
						String msg = "ack\"" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "\""
								+ AcceptNum[0] + "," + AcceptNum[1] + ","
								+ AcceptNum[2] + "\"" + AcceptVal[0] + "\'"
								+ AcceptVal[1] + "\'";
						send(msg, BallotNum[1]);
						System.out.println("    ACK <" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "> <"
								+ AcceptNum[0] + "," + AcceptNum[1] + ","
								+ AcceptNum[2] + "> <" + AcceptVal[0] + " <"
								+ AcceptVal[1] + ">>" + " to " + BallotNum[1]);
					} else {
						// do nothing
					}
				} else if (ballot[2] == log.size() - 1) {
					if (ballot[0] > BallotNum[0]
							|| (ballot[0] == BallotNum[0] && ballot[1] > BallotNum[1])) {
						BallotNum[0] = ballot[0];
						BallotNum[1] = ballot[1];
						BallotNum[2] = ballot[2];
						String msg = "ack\"" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "\""
								+ AcceptNum[0] + "," + AcceptNum[1] + ","
								+ AcceptNum[2] + "\"" + AcceptVal[0] + "\'"
								+ AcceptVal[1] + "\'";
						send(msg, BallotNum[1]);
						System.out.println("    ACK <" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "> <"
								+ AcceptNum[0] + "," + AcceptNum[1] + ","
								+ AcceptNum[2] + "> <" + AcceptVal[0] + " <"
								+ AcceptVal[1] + ">>" + " to " + BallotNum[1]);
					} else {
						process_help(ballot[1]);
					}
				} else if (ballot[2] < log.size() - 1) {
					process_help(ballot[1]);
				} else if (ballot[2] > log.size()) {
					sendHelp();
				}
			}
				break;
			case "accept":
				// accept"1,1,1"Hello'1,1,1
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]),
						Integer.parseInt(ballot_string[2]) };
				if (ballot[2] == log.size()) {
					if (ballot[0] > BallotNum[0]
							|| (ballot[0] == BallotNum[0] && ballot[1] >= BallotNum[1])) {
						if (!(ballot[0] == AcceptNum[0] && ballot[1] == AcceptNum[1])) {
							AcceptNum[0] = ballot[0];
							AcceptNum[1] = ballot[1];
							AcceptNum[2] = ballot[2];
							String[] msg = cmd[2].split("\'");
							AcceptVal[0] = msg[0];
							AcceptVal[1] = msg[1];
							BallotNum[0] = ballot[0];
							BallotNum[1] = ballot[1];
							BallotNum[2] = ballot[2];
							for (int i = 0; i < 5; i++) {
								if (i != ID)
									send(input, i);
							}
							ACPCount = 2;
							serverTimer.cancel();
							STATUS = STATUSTYPE.AFTER_SENDACCEPT;
							serverTimer = new ServerTimer();
							serverTimer.startTimer();
							System.out.println("	ACCEPT <" + ballot[0] + ","
									+ ballot[1] + "," + ballot[2] + "> " + "<"
									+ msg[0] + "<" + msg[1] + ">>");
							if (Debug) {
								System.out
										.println("	STATE CHANGE TO " + STATUS);
							}
						} else {
							if (Debug) {
								System.out.println("	has already accepted.");
							}
						}
					} else {
						if (Debug) {
							System.out.println("	ignore small accepted.");
						}
					}
				} else if (ballot[2] == log.size() - 1) {
					if (ballot[0] > BallotNum[0]
							|| (ballot[0] == BallotNum[0] && ballot[1] >= BallotNum[1])) {
                    if (!(ballot[0] == AcceptNum[0] && ballot[1] ==
						 AcceptNum[1])) {
						AcceptNum[0] = ballot[0];
						AcceptNum[1] = ballot[1];
						AcceptNum[2] = ballot[2];
						String[] msg = cmd[2].split("\'");
						AcceptVal[0] = msg[0];
						AcceptVal[1] = msg[1];
						BallotNum[0] = ballot[0];
						BallotNum[1] = ballot[1];
						BallotNum[2] = ballot[2];
						for (int i = 0; i < 5; i++) {
							if (i != ID)
								send(input, i);
						}

						ACPCount = 2;
						serverTimer.cancel();
						STATUS = STATUSTYPE.AFTER_SENDACCEPT;
						serverTimer = new ServerTimer();
						serverTimer.startTimer();
						System.out.println("	ACCEPT <" + ballot[0] + ","
								+ ballot[1] + "," + ballot[2] + "> " + "<"
								+ msg[0] + "<" + msg[1] + ">>");
						if (Debug) {
							System.out.println("	STATE CHANGE TO " + STATUS);
						}
                      }
					}
				} else if (ballot[2] < log.size() - 1) {
					if (Debug) {
						System.out.println("	ignore previous accepted.");
					}
				} else if (ballot[2] > log.size()) {
					sendHelp();
				}
				break;
			case "ack": // do nothing
				break;
			case "help":
				// help"ID
				process_help(Integer.parseInt(cmd[1]));
				break;
			case "log":
				process_log(cmd);
				break;
			case "read":
				process_read(cmd[1]);
				break;
			}
		}

		/**
		 * In prepare status. Waiting for ack.
		 * 
		 * @param input
		 */
		private void prepare_process(final String input) {
			String cmd[] = input.split("\"");
			String operation = cmd[0];
			switch (operation) {
			case "post": {
				String msg = "Retry Post. Mutiple concurrent Posts at one server.";
				reply(cmd[1], msg);
			}
				break;
			case "prepare": {
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]),
						Integer.parseInt(ballot_string[2]) };
				if (ballot[2] == log.size()) {
					if (ballot[0] > BallotNum[0]
							|| (ballot[0] == BallotNum[0] && ballot[1] > BallotNum[1])) {
						String msg = "Retry Post. Competition failed due to another prepare.";
						reply(clientMsg[0], msg);
						clientMsg = new String[2];
						BallotNum[0] = ballot[0];
						BallotNum[1] = ballot[1];
						BallotNum[2] = ballot[2];
						String ack = "ack\"" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "\""
								+ AcceptNum[0] + "," + AcceptNum[1] + ","
								+ AcceptNum[2] + "\"" + AcceptVal[0] + "\'"
								+ AcceptVal[1];
						send(ack, BallotNum[1]);
						System.out.println("	ACK <" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "> <"
								+ AcceptNum[0] + "," + AcceptNum[1] + ","
								+ AcceptNum[2] + "> <" + AcceptVal[0] + " <"
								+ AcceptVal[1] + ">>" + " to " + BallotNum[1]);
						serverTimer.cancel();
						STATUS = STATUSTYPE.WAIT;
						serverTimer = new ServerTimer();
						serverTimer.startTimer();
						if (Debug) {
							System.out.println("	STATE CHANGE TO " + STATUS);
						}
					} else {
						if (Debug) {
							System.out.println("	ignored small prepare");
						}
					}
				} else if (ballot[2] < log.size()) {
					process_help(ballot[1]);
				} else if (ballot[2] > log.size()) {
					String msg = "Retry Post. Missing Entry in Log.";
					reply(clientMsg[0], msg);
					clientMsg = new String[2];
					sendHelp();
					serverTimer.cancel();
					STATUS = STATUSTYPE.WAIT;
					serverTimer = new ServerTimer();
					serverTimer.startTimer();
					if (Debug) {
						System.out.println("	STATE CHANGE TO " + STATUS);
					}
				}
			}
				break;
			case "accept": {
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]),
						Integer.parseInt(ballot_string[2]) };
				if (ballot[2] == log.size()) {
					if (ballot[0] > BallotNum[0]
							|| (ballot[0] == BallotNum[0] && ballot[1] > BallotNum[1])) {
						String msg = "Retry Post. Competition failed due to another accept.";
						reply(clientMsg[0], msg);
						clientMsg = new String[2];
						AcceptNum[0] = ballot[0];
						AcceptNum[1] = ballot[1];
						AcceptNum[2] = ballot[2];
						String[] val = cmd[2].split("\'");
						AcceptVal[0] = val[0];
						AcceptVal[1] = val[1];
						BallotNum[0] = AcceptNum[0];
						BallotNum[1] = AcceptNum[1];
						BallotNum[2] = AcceptNum[2];
						for (int i = 0; i < 5; i++) {
							if (i != ID)
								send(input, i);
						}
						ACPCount = 2;
						serverTimer.cancel();
						serverTimer = new ServerTimer();
						serverTimer.startTimer();
						STATUS = STATUSTYPE.AFTER_SENDACCEPT;
						System.out.println("	ACCEPT <" + ballot[0] + ","
								+ ballot[1] + "," + ballot[2] + "> " + "<"
								+ val[0] + "<" + val[1] + ">>");
						if (Debug) {
							System.out.println("	STATE CHANGE TO " + STATUS);
						}
					} else {
						if (Debug) {
							System.out.println("	ignore small accept.");
						}
					}
				} else if (ballot[2] < log.size()) {
					if (Debug) {
						System.out.println("	ignore prevoius accepted.");
					}
				} else if (ballot[2] > log.size()) {
					String msg = "Retry Post. Missing Entry in Log.";
					reply(clientMsg[0], msg);
					clientMsg = new String[2];
					sendHelp();
					serverTimer.cancel();
					STATUS = STATUSTYPE.WAIT;
					serverTimer = new ServerTimer();
					serverTimer.startTimer();
					if (Debug) {
						System.out.println("	STATE CHANGE TO " + STATUS);
					}
				}
			}
				break;
			case "ack":
//				System.out.println(input);
				String[] ballot_string = cmd[1].split(",");
				String[] accept_string = cmd[2].split(",");
				String[] val = cmd[3].split("'");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]),
						Integer.parseInt(ballot_string[2]) };
				int[] accept = { Integer.parseInt(accept_string[0]),
						Integer.parseInt(accept_string[1]),
						Integer.parseInt(accept_string[2]) };
				if (ballot[0] == BallotNum[0] && ballot[1] == BallotNum[1]) {
					ACKCount++;
					if (accept[2] != -1 && accept[2] >= BallotNum[2]) {
						if (accept[0] > MaxACKNum[0]
								|| (accept[0] == MaxACKNum[0] && accept[1] > MaxACKNum[1])) {
							MaxACKNum[0] = accept[0];
							MaxACKNum[1] = accept[1];
							MaxACKNum[1] = accept[2];
							MaxACKVal[0] = val[0];
							MaxACKVal[1] = val[1];
						}
					}
					if (ACKCount == MAJORITY) {
						if (MaxACKNum[0] == -1) {
							AcceptVal[0] = clientMsg[1];
						} else {
							AcceptVal[0] = MaxACKVal[0];
							String msg = "Retry Post. Competition Failed due to not bottom.";
							reply(clientMsg[0], msg);
							clientMsg = new String[2];
						}
						AcceptNum[0] = BallotNum[0];
						AcceptNum[1] = BallotNum[1];
						AcceptNum[2] = BallotNum[2];
						AcceptVal[1] = BallotNum[0] + "," + BallotNum[1] + ","
								+ BallotNum[2];
						ACPCount = 1;
						String msg = "accept\"" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "\""
								+ AcceptVal[0] + "\'" + AcceptVal[1];
						for (int i = 0; i < 5; i++) {
							if (i != ID)
								send(msg, i);
						}
						System.out.println("	Majority ack, send ACCEPT <" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "> "
								+ "<" + AcceptVal[0] + "<" + AcceptVal[1]
								+ ">>");
						serverTimer.cancel();
						STATUS = STATUSTYPE.AFTER_SENDACCEPT;
						serverTimer = new ServerTimer();
						serverTimer.startTimer();
						if (Debug) {
							System.out.println("	STATE CHANGE TO " + STATUS);
						}
					}
				} else if (ballot[2] < BallotNum[2]) {
					if (Debug) {
						System.out.println("	ignored small ack.");
					}
				} else if (ballot[2] > BallotNum[2]) {
					if (Debug) {
						System.out.println("	impossible bigger ack.");
					}
				}
				break;
			case "help":
				process_help(Integer.parseInt(cmd[1]));
				break;
			case "log":
				process_log(cmd);
				break;
			case "read":
				process_read(cmd[1]);
				break;
			}
		}

		/**
		 * In send accept status. wait for accept
		 * 
		 * @param input
		 */
		private void sendaccept_process(final String input) {
			String cmd[] = input.split("\"");
			String operation = cmd[0];
			switch (operation) {
			case "post": {
				String msg = "Retry Post. Mutiple concurrent Posts at one server.";
				reply(cmd[1], msg);
			}
				break;
			case "prepare": {
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]),
						Integer.parseInt(ballot_string[2]) };
				if (ballot[2] == log.size()) {
					if (ballot[0] > BallotNum[0]
							|| (ballot[0] == BallotNum[0] && ballot[1] > BallotNum[1])) {
						// if (clientMsg[0] != null) {
						// String msg =
						// "Retry Post. Competition failed due to another prepare.";
						// reply(clientMsg[0], msg);
						// clientMsg = new String[2];
						// }
						BallotNum[0] = ballot[0];
						BallotNum[1] = ballot[1];
						BallotNum[2] = ballot[2];
						String ack = "ack\"" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "\""
								+ AcceptNum[0] + "," + AcceptNum[1] + ","
								+ AcceptNum[2] + "\"" + AcceptVal[0] + "\'"
								+ AcceptVal[1];
						send(ack, BallotNum[1]);
						System.out.println("	ACK <" + BallotNum[0] + ","
								+ BallotNum[1] + "," + BallotNum[2] + "> <"
								+ AcceptNum[0] + "," + AcceptNum[1] + ","
								+ AcceptNum[2] + "> <" + AcceptVal[0] + " <"
								+ AcceptVal[1] + ">>" + " to " + BallotNum[1]);
						serverTimer.cancel();
						STATUS = STATUSTYPE.WAIT;
						serverTimer = new ServerTimer();
						serverTimer.startTimer();
						if (Debug) {
							System.out.println("	STATE CHANGE TO " + STATUS);
						}
					} else {
						if (Debug) {
							System.out.println("	ignored small prepare");
						}
					}
				} else if (ballot[2] < log.size()) {
					process_help(ballot[1]);
				} else if (ballot[2] > log.size()) {
					if (clientMsg[0] != null) {
						String msg = "Retry Post. Missing Entry in Log.";
						reply(clientMsg[0], msg);
						clientMsg = new String[2];
					}
					sendHelp();
					serverTimer.cancel();
					STATUS = STATUSTYPE.WAIT;
					serverTimer = new ServerTimer();
					serverTimer.startTimer();
					if (Debug) {
						System.out.println("	STATE CHANGE TO " + STATUS);
					}
				}
			}
				break;
			case "accept":
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]),
						Integer.parseInt(ballot_string[2]) };
				if (ballot[0] > BallotNum[0]
						|| (ballot[0] == BallotNum[0] && ballot[1] > BallotNum[1])) {
					AcceptNum[0] = ballot[0];
					AcceptNum[1] = ballot[1];
					AcceptNum[2] = ballot[2];
					String[] msg = cmd[2].split("\'");
					AcceptVal[0] = msg[0];
					AcceptVal[1] = msg[1];
					BallotNum[0] = ballot[0];
					BallotNum[1] = ballot[1];
					BallotNum[2] = ballot[2];
					for (int i = 0; i < 5; i++) {
						if (i != ID)
							send(input, i);
					}

					ACPCount = 2;
					System.out.println("	ACCEPT <" + BallotNum[0] + ","
							+ BallotNum[1] + "," + BallotNum[2] + "> " + "<"
							+ AcceptVal[0] + "<" + AcceptVal[1] + ">>");

				} else if (ballot[0] == BallotNum[0]
						&& ballot[1] == BallotNum[1]) {
					ACPCount++;
					if (ACPCount == MAJORITY) {
						String l = AcceptVal[0] + "\'" + AcceptVal[1];
						if (log.size() - 1 == BallotNum[2]) {
							log.set(BallotNum[2], l);
//							System.out.println("*****Change BallotNum in log");
						} else {
							log.add(l);
						}

						System.out.println("	Decide: " + AcceptVal[0] + " ID: "
								+ AcceptVal[1]);

						if (clientMsg[0] != null) {
							String msg = null;
							if (clientMsg[1].equals(AcceptVal[0])) {
								msg = "Successfully insert to log "
										+ BallotNum[2];
//								if(BallotNum[1]!=ID) System.out.println("*****Insert with BallotNum changed!");
							} else {
								msg = "Retry Post. Competition failed due to another prepare.";
//								System.out.println("*****Reject after receiving majority accept!");
							}
							reply(clientMsg[0], msg);
							clientMsg = new String[2];
							System.out.println("	Server " + ID + " send " + msg
									+ " to client.");
						}
						serverTimer.cancel();
						STATUS = STATUSTYPE.WAIT;
						serverTimer = new ServerTimer();
						serverTimer.startTimer();
						if (Debug) {
							System.out.println("	STATUS Change to " + STATUS);
						}
					}
				}
				break;
			case "help":
				process_help(Integer.parseInt(cmd[1]));
				break;
			case "log":
				process_log(cmd);
				break;
			case "read":
				process_read(cmd[1]);
				break;
			}
		}
	}

	private class ServerTimer extends Timer {
		private ServerTimerTask serverTimerTask;

		public ServerTimer() {
			this.serverTimerTask = new ServerTimerTask();
		}
		
		public void startTimer() {
			this.schedule(this.serverTimerTask, 300000);
		}

		private class ServerTimerTask extends TimerTask {

			@Override
			public void run() {
				synchronized (STATUS) {
                    if(STATUS != STATUSTYPE.FAIL){
					System.out.println("TIME OUT!");
					STATUS = STATUSTYPE.WAIT;
					if (log.size() == 0) {
						// BallotNum
						BallotNum = new int[3];
						BallotNum[0] = 0;
						BallotNum[1] = 0;
						BallotNum[2] = -1;
						// Accept Num and Accept Val
						AcceptNum = new int[3];
						AcceptNum[0] = 0;
						AcceptNum[1] = 0;
						AcceptNum[2] = -1;
						AcceptVal = new String[2];
					} else {
						String[] lastEntry = log.get(log.size() - 1)
								.split("\'");
						String[] lastEntry_num = lastEntry[1].split(",");
						int[] lastNum = { Integer.parseInt(lastEntry_num[0]),
								Integer.parseInt(lastEntry_num[1]),
								Integer.parseInt(lastEntry_num[2]) };
						AcceptNum[0] = lastNum[0];
						AcceptNum[1] = lastNum[1];
						AcceptNum[2] = lastNum[2];
						AcceptVal[0] = lastEntry[0];
						AcceptVal[1] = lastEntry[1];
						BallotNum[0] = AcceptNum[0];
						BallotNum[1] = AcceptNum[1];
						BallotNum[2] = AcceptNum[2];

					}
					clientMsg = new String[2];
					this.cancel();
				}
              }
			}
		}

	}
}
