package paxos;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class ServerDrive {

		public static void main(String[] args) throws IOException {
			Server server1 = new Server(readFile("config1.txt"));

			server1.start();

		}
		/**
		 * Read Server Address from File.
		 * 
		 * @param fileName
		 * @return
		 * @throws IOException
		 */
		public static ArrayList<String[]> readFile(String fileName)
				throws IOException {
			FileReader fileReader = new FileReader(fileName);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			ArrayList<String[]> ProcessAddress = new ArrayList<String[]>();
			String[] ID = new String[1];
			ID[0] = bufferedReader.readLine();
			ProcessAddress.add(ID);
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				String[] addr = line.split(" ");
				ProcessAddress.add(addr);
			}
			bufferedReader.close();
			return ProcessAddress;
		}
}
