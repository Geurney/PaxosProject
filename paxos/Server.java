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

import sun.security.util.Length;
import jdk.internal.dynalink.beans.StaticClass;

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
	// private ArrayList<String[]> serverAddress = new ArrayList<String[]>();
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

	private String[] clientMsg;

	private int[] MaxACKNum;
	private String[] MaxACKVal;
	private String[] ProposeVal;

	public Server(ArrayList<String[]> config) throws IOException {
		serverAddress = config;
		ID = Integer.parseInt(serverAddress.get(0)[0]);
		hostname = InetAddress.getByName(serverAddress.get(6)[0]);
		port = Integer.parseInt(serverAddress.get(6)[1]);
		// Status and Mode
		STATUS = STATUSTYPE.WAIT;
		MODE = MODETYPE.NORMAL;
		// BallotNum
		BallotNum = new int[2];
		BallotNum[0] = 0;
		BallotNum[1] = 0;
		// Proposal Value
		ProposeVal = new String[3];
		// Accept Num and Accept Val
		AcceptNum = new int[2];
		AcceptNum[0] = 0;
		AcceptNum[1] = 0;
		AcceptVal = new String[3];
		// ACK count
		ACKCount = 0;
		// ACP count
		ACPCount = 0;
		// log
		log = new ArrayList<String>();
		// Client Msg
		clientMsg = new String[2];
		// MaxACKNum and MaxACKVal
		MaxACKNum = new int[2];
		MaxACKNum[0] = -1;
		MaxACKNum[1] = -1;
		MaxACKVal = new String[3];
		// Thread
		COMM = new COMMThread();
		CIL = new CLIThread();
	}

	public void start() {
		CIL.start();
		COMM.start();
	}

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
					System.out.println("INPUT: " + input);
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
					System.out.println("CURRENT STATE: " + STATUS);
					System.out.println("Client IP & port: "+ clientMsg[0]);
					// System.out.println("BallotNum: "+BallotNum[0]+","+BallotNum[1]);
					switch (STATUS) {
					case FAIL:
						fail_process(input);
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
						continue;
					}
				}
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		private void fail_process(String input) {
			String cmd[] = input.split("\"");
			String operation = cmd[0];
			if (operation.equals("wake")) {
				String msg = "help\"" + ID;
				STATUS = STATUSTYPE.WAIT;
				MODE = MODETYPE.RECOVERY;
				for (int i = 0; i < 5; i++) {
					if (i != ID)
						send(msg, i);
				}
				System.out.println("	" + ID + "send out help");
			}
		}

		private void reject(String address, String msg) {
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
		}

		private void process_post() {
			BallotNum[0] = BallotNum[0] + 1;
			BallotNum[1] = ID;
			ProposeVal[0] = String.valueOf(log.size());
			ProposeVal[1] = clientMsg[1];
			ProposeVal[2] = BallotNum[0] + "," + BallotNum[1];
			MaxACKNum[0] = -1;
			MaxACKNum[1] = -1;
			MaxACKVal = new String[3];
			String msg = "prepare\"" + BallotNum[0] + "," + BallotNum[1];
			ACKCount = 1;
			for (int i = 0; i < 5; i++) {
				if (i != ID)
					send(msg, i);
			}
			System.out.println("	" + ID + " send " + msg);
		}

		private void process_read(String[] address) {
			String ipAddress = address[0];
			int port = Integer.parseInt(address[1]);
			Socket socket;
			try {
				socket = new Socket(ipAddress, port);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			PrintWriter out;
			try {
				out = new PrintWriter(socket.getOutputStream(), true);
				String msg;
				if (MODE == MODETYPE.NORMAL) {
					StringBuffer sb = new StringBuffer();
					sb.append("Log\"");
					for (String i : log) {
						sb.append(i);
						sb.append("\"");
					}
					sb.deleteCharAt(sb.length() - 1);
					msg = sb.toString();
				} else {
					msg = "Retry Server is recoverying...";
				}
				out.println(msg);
				System.out.println(ID + " send back log to client!");
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void process_prepare(String[] ballot_string) {
			int[] ballot = { Integer.parseInt(ballot_string[0]),
					Integer.parseInt(ballot_string[1]) };
			if (ballot[0] > BallotNum[0]
					|| (ballot[0] == BallotNum[0] && ballot[1] > BallotNum[1])) {
				BallotNum[0] = ballot[0];
				BallotNum[1] = ballot[1];
				String msg = "ack\"" + BallotNum[0] + "," + BallotNum[1] + "\""
						+ AcceptNum[0] + "," + AcceptNum[1] + "\""
						+ AcceptVal[0] + "\'" + AcceptVal[1] + "\'"
						+ AcceptVal[2];
				send(msg, BallotNum[1]);
				System.out.println("	" + ID + " send " + msg);
			}
		}

		private void process_help(int serverID, String[] AcceptVal) {
			StringBuffer msg = new StringBuffer();
			msg.append("log\"");
			for (String i : log) {
				msg.append(i);
				msg.append("\"");
			}
			msg.deleteCharAt(msg.length() - 1);
			if (AcceptVal != null) {
				msg.append(AcceptVal);
			}
			send(msg.toString(), serverID);
			System.out.println("	" + ID + " send " + msg + " to " + serverID);

		}

		private void process_log(String[] cmd) {
			if (MODE == MODETYPE.NORMAL) {
				return;
			} else {
				int index = log.size();
				for (int i = index + 1; i < cmd.length - 1; i++) {
					log.add(cmd[i]);
				}
				if (cmd.length == log.size() + 1) {
					int InNum1 = Integer.parseInt(cmd[cmd.length - 1]
							.split("\'")[2].split(",")[0]);
					int InNum2 = Integer.parseInt(cmd[cmd.length - 1]
							.split("\'")[2].split(",")[1]);
					int LogNum1 = Integer.parseInt(log.get(log.size() - 1)
							.split("\'")[2].split(",")[0]);
					int LogNum2 = Integer.parseInt(log.get(log.size() - 1)
							.split("\'")[2].split(",")[1]);
					if (InNum1 > LogNum1
							|| (InNum1 == LogNum1 && InNum2 > LogNum2))
						log.add(cmd[cmd.length - 1]);
				} else
					log.add(cmd[cmd.length - 1]);
				// Set current ballotNum to the ballotNum in the last slot of
				// the log
				BallotNum[0] = Integer.parseInt(log.get(log.size() - 1).split(
						"\'")[2].split(",")[0]);
				BallotNum[1] = Integer.parseInt(log.get(log.size() - 1).split(
						"\'")[2].split(",")[1]);
				MODE = MODETYPE.NORMAL;
				System.out.println("Receive log: " + log.toString());
			}
		}

		private void process_ack(String[] ballot_string,
				String[] accept_string, String acceptValString) {
			int[] ballot = { Integer.parseInt(ballot_string[0]),
					Integer.parseInt(ballot_string[1]) };
			int[] accept = { Integer.parseInt(accept_string[0]),
					Integer.parseInt(accept_string[1]) };

			if (ballot[0] == BallotNum[0] && ballot[1] == BallotNum[1]) {
				ACKCount++;
				String[] val_string = acceptValString.split("\'");
				if (!val_string[0].equals("null"))
					if (Integer.parseInt(val_string[0]) > log.size() - 1) {
						if (accept[0] > MaxACKNum[0]
								|| (accept[0] == MaxACKNum[0] && accept[1] > MaxACKNum[1])) {
							MaxACKNum[0] = accept[0];
							MaxACKNum[1] = accept[1];
							MaxACKVal[0] = val_string[0];
							MaxACKVal[1] = val_string[1];
							MaxACKVal[2] = val_string[2];
						}
					}
				if (ACKCount >= MAJORITY) {
					if (MaxACKNum[0] == 0) {
						BallotNum[0] = MaxACKNum[0];
						BallotNum[1] = MaxACKNum[1];
						ProposeVal[0] = MaxACKVal[0];
						ProposeVal[1] = MaxACKVal[1];
						ProposeVal[2] = MaxACKVal[2];
					}
					AcceptNum[0] = BallotNum[0];
					AcceptNum[1] = BallotNum[1];
					AcceptVal[0] = ProposeVal[0];
					AcceptVal[1] = ProposeVal[1];
					AcceptVal[2] = ProposeVal[2];
					String val = ProposeVal[0] + "\'" + ProposeVal[1] + "\'"
							+ ProposeVal[2];
					String msg = "accept\"" + BallotNum[0] + "," + BallotNum[1]
							+ "\"" + val;
					for (int i = 0; i < 5; i++) {
						if (i != ID)
							send(msg, i);
					}
					System.out.println(ID + " send " + msg);
					ACPCount = 1;
					STATUS = STATUSTYPE.AFTER_SENDACCEPT;
					System.out.println("STATE CHANGE TO " + STATUS);
				}
			}
		}

		/*
		 * private void process_ack(String[] ACKNum, String[] ACKVal) { if
		 * (Integer.parseInt(ACKVal[0]) < log.size() - 1) { return; } compare
		 * ballot number[0] if(ballot[0] < BallotNum[0]) { return; }
		 * 
		 * ACKCount++; if (ACKCount >= MAJORITY) {
		 * 
		 * String val = AcceptVal[0] + "\'" + AcceptVal[1] + "\'" +
		 * AcceptVal[2]; String msg = "accept\"" + BallotNum[0] + "," +
		 * BallotNum[1] + "\"" + val; send(msg); ACPCount = 1; STATUS =
		 * STATUSTYPE.AFTER_SENDACCEPT; }
		 * 
		 * if (Integer.parseInt(Accept_string[0]) >= log.size() - 1) {
		 * AcceptVal[0] = Accept_string[0]; AcceptVal[1] = Accept_string[1];
		 * AcceptVal[2] = Accept_string[2]; } ACKCount++; if (ACKCount >=
		 * MAJORITY) { String val = AcceptVal[0] + "\'" + AcceptVal[1] + "\'" +
		 * AcceptVal[2]; String msg = "accept\"" + BallotNum[0] + "," +
		 * BallotNum[1] + "\"" + val; send(msg); ACPCount = 1; STATUS =
		 * STATUSTYPE.AFTER_SENDACCEPT; } }
		 */

		private void wait_process(final String input) {
			String cmd[] = input.split("\"");
			String operation = cmd[0];
			switch (operation) {
			case "post":
				clientMsg[0] = cmd[1];
				clientMsg[1] = cmd[2];
				process_post();
				STATUS = STATUSTYPE.AFTER_PREPARE;
				System.out.println("	STATE CHANGE TO " + STATUS);
				break;
			case "prepare":
				process_prepare(cmd[1].split(","));
				break;
			case "accept":
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]) };

				if (ballot[0] == AcceptNum[0] && ballot[1] == AcceptNum[1]) {
					System.out.println("Current accept is last round!");
					break;
				}
				if (ballot[0] > BallotNum[0]
						|| (ballot[0] == BallotNum[0] && ballot[1] >= BallotNum[1])) {
					AcceptNum[0] = ballot[0];
					AcceptNum[1] = ballot[1];
					String[] msg = cmd[2].split("\'");
					AcceptVal[0] = msg[0];
					AcceptVal[1] = msg[1];
					AcceptVal[2] = msg[2];
					ACPCount = 1;
					for (int i = 0; i < 5; i++) {
						if (i != ID)
							send(input, i);
					}
					STATUS = STATUSTYPE.AFTER_SENDACCEPT;
					System.out.println("	STATE CHANGE TO " + STATUS);
				}

				break;
			case "help":
				process_help(Integer.parseInt(cmd[1]), null);
				break;
			case "log":
				process_log(cmd);
				break;
			case "read":
				process_read(cmd[1].split("\'"));
				break;
			}
		}

		private void prepare_process(final String input) {
			String cmd[] = input.split("\"");
			String operation = cmd[0];
			switch (operation) {
			case "post": {
				String msg = "Retry After Prepare Post";
				reject(cmd[1], msg);
			}
				break;
			case "prepare": {
				process_prepare(cmd[1].split(","));
				STATUS = STATUSTYPE.WAIT;
				System.out.println("STATE CHANGE TO " + STATUS);
				String msg = "Retry After Prepare Prepare";
				reject(clientMsg[0], msg);
				clientMsg[0] = null;
				clientMsg[1] = null;
			}
				break;
			case "accept": {
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]) };
				if (ballot[0] > BallotNum[0]
						|| (ballot[0] == BallotNum[0] && ballot[1] >= BallotNum[1])) {
					AcceptNum[0] = ballot[0];
					AcceptNum[1] = ballot[1];
					String[] val = cmd[2].split("\'");
					AcceptVal[0] = val[0];
					AcceptVal[1] = val[1];
					AcceptVal[2] = val[2];
					ACPCount = 1;
					for (int i = 0; i < 5; i++) {
						if (i != ID)
							send(input, i);
					}
					STATUS = STATUSTYPE.AFTER_SENDACCEPT;
					System.out.println("STATE CHANGE TO " + STATUS);
					String msg = "Retry After Prepare Accept";
					reject(clientMsg[0], msg);
					clientMsg[0] = null;
					clientMsg[1] = null;
				}
			}
				break;
			case "ack":
				process_ack(cmd[1].split(","), cmd[2].split(","), cmd[3]);
				break;
			case "help":
				process_help(Integer.parseInt(cmd[1]), null);
				break;
			case "log":
				process_log(cmd);
				break;
			case "read":
				process_read(cmd[1].split("\'"));
				break;
			}
		}

		private void sendaccept_process(final String input) {
			String cmd[] = input.split("\"");
			String operation = cmd[0];
			switch (operation) {
			case "post": {
				String msg = "Retry After SendAccept Post";
				reject(cmd[1], msg);
			}
				break;
			case "prepare":
				process_prepare(cmd[1].split(","));
				STATUS = STATUSTYPE.WAIT;
				System.out.println("STATE CHANGE TO " + STATUS);
				String msg = "Retry After SendAccept Prepare";
				reject(clientMsg[0], msg);
				clientMsg[0] = null;
				clientMsg[1] = null;
				break;

			case "accept":
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]) };
				if (ballot[0] > BallotNum[0]
						|| (ballot[0] == BallotNum[0] && ballot[1] > BallotNum[1])) {
					AcceptNum[0] = ballot[0];
					AcceptNum[1] = ballot[1];
					String[] val = cmd[2].split("\'");
					AcceptVal[0] = val[0];
					AcceptVal[1] = val[1];
					AcceptVal[2] = val[2];
					ACPCount = 1;
					for (int i = 0; i < 5; i++) {
						if (i != ID)
							send(input, i);
					}
					System.out.println(ID + " send " + input);
					String msg2 = "Retry After SendAccept Accept";
					reject(clientMsg[0], msg2);
					clientMsg[0] = null;
					clientMsg[1] = null;
				} else {
					ACPCount++;
					if (ACPCount >= MAJORITY) {
						String val = AcceptVal[0] + "\'" + AcceptVal[1] + "\'"
								+ AcceptVal[2];
						if (log.size() - 1 == AcceptNum[0])
							log.set(AcceptNum[0], val);
						else
							log.add(val);
						System.out.println(ID + "successfully insert "
								+ log.get(Integer.parseInt(AcceptVal[0])));
						if(clientMsg[0]!=null)
						sendBack("You posted msg to Log[" + AcceptVal[0] + "]",
								clientMsg[0]);
						System.out.println(ID + " send MsgID to client!");
						STATUS = STATUSTYPE.WAIT;
						System.out.println("STATE CHANGE TO " + STATUS);
						clientMsg[0] = null;
						clientMsg[1] = null;
					}
				}

				break;
			case "help":
				process_help(Integer.parseInt(cmd[1]), AcceptVal);
				break;
			case "log":
				process_log(cmd);
				break;
			case "read":
				process_read(cmd[1].split("\'"));
				break;
			}
		}

		private void sendBack(String msg, String client) {
			System.out.println("sendBack "+client);
			Socket socket;
			try {
				socket = new Socket(client.split("\'")[0],
						Integer.parseInt(client.split("\'")[1]));
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
	}
}
