package com.tripsurfing.nlp;

import java.io.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import com.tripsurfing.rmiserver.ModelServer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;

import com.google.gson.Gson;

/***
 * This recognizes mentions by dictionaries with longest matching. There are
 * some cases which might make mistakes such as: Berlin, Germany; need more
 * thoughts here... At the moment, process sentence by sentence independently.
 *
 * @author datnb
 */
public class DictionaryBasedNER {
    //	private int LIMIT_LENGTH = 6;
//	private Set<String> dictionary;
    private String configFile = "./src/main/resources/vivut.properties"; // default
    private ModelServer server;

    public DictionaryBasedNER() {
        String host = "localhost";
        try {
            Registry registry = LocateRegistry.getRegistry(host, 52478);
            server = (ModelServer) registry.lookup("TkServer_" + host);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public DictionaryBasedNER(String configFile) {
        this.configFile = configFile;
    }

//	public DictionarybasedNER(int LIMIT_LENGTH) {
//		this.LIMIT_LENGTH = LIMIT_LENGTH;
//		loadDictionary();
//	}
//
//	private void loadDictionary() {
//		dictionary = new HashSet<String>();
//		Properties properties = new Properties();
//		try {
//			properties.load(new FileInputStream(configFile));
//			File folder = new File(properties.getProperty("SOURCE_LINKS"));
//			for (File file : folder.listFiles()) {
//				for (String line : Utils.readFileByLine(file.getPath())) {
//					// http://www.tripadvisor.com/Restaurant_Review-g2528749-d4104336-Reviews-Waroeng_Santa_Fe-Abang_Bali.html
//					String str[] = line.split("-");
//					String name = str[str.length - 2].replaceAll("_", " ");
//					dictionary.add(name.toLowerCase());
//					String dstName = str[str.length - 1].split("\\.")[0].replaceAll("_", " ");
//					dictionary.add(dstName.toLowerCase());
//				}
//			}
//		} catch (Exception ioe) {
//			ioe.printStackTrace();
//		}
//		System.out.println("Loaded: " + dictionary.size() + " names.");
//	}
//
//	public List<String> recognizeMentions(String sentence) {
//		if(dictionary == null) {
//			loadDictionary();
//		}
//		Properties properties = new Properties();
//		try {
//			properties.load(new FileInputStream(configFile));
//		} catch (Exception e) {
//			// TODO: handle exception
//		}
//		// Initialize the tagger
//        MaxentTagger tagger = new MaxentTagger(properties.getProperty("POS_TAGGER"));
//        // The tagged string
//        String tagged = tagger.tagString(sentence);
//		// TODO: implement tokenizer or call Stanford tokenizer
//		List<String> res = new ArrayList<String>();
//		String[] tokens = tagged.split(" ");
//		for (int i = 0; i < tokens.length; ) {
//			int nextPos = i + 1;
//			String[] info = tokens[i].split("_");
//			if(info.length > 1 && info[1].equalsIgnoreCase("NNP")) {
//				for (int j = LIMIT_LENGTH; j >= 0; j--) {
//					String s = info[0];
//					for (int t = 1; t < j && i + t < tokens.length; t++)
//						s += " " + tokens[i + t].split("_")[0];
//					if (dictionary.contains(s.toLowerCase())) {
//						res.add(s);
//						nextPos = i + j;
//						break;
//					}
//				}
//			}
//			i = nextPos;
//		}
//		return res;
//	}

    private void updateAllQuotes(int tripId) throws Exception {
        // STEP 2: Register JDBC driver
        Class.forName("com.mysql.jdbc.Driver");
        // STEP 3: Open a connection
        System.out.println("Connecting to database...");
        Properties properties = new Properties();
        properties.load(new FileInputStream(configFile));
        Connection conn = DriverManager.getConnection(properties.getProperty("DB_URL"),
                properties.getProperty("SQL_USER"), properties.getProperty("SQL_PASS"));
        // STEP 4: Execute a query
        Statement stmt = conn.createStatement();
        String sql = "SELECT id, content FROM trip_quote WHERE trip_id=" + tripId + ";";
        ResultSet rs = stmt.executeQuery(sql);
        // STEP 5: Extract data from result set
        while (rs.next()) {
            // Retrieve by column name
            int id = rs.getInt("id");
            String quote = rs.getString("content");
            List<String> names = server.recognizeMentions(quote);
            if (names.size() > 0) {
                Gson gson = new Gson();
                String res = gson.toJson(names);
                // update
                String cmd = "UPDATE trip_quote SET extracted_places=? WHERE id=?;";
//		        System.out.println(id + " " + quote);
                PreparedStatement updateStatement = conn.prepareStatement(cmd);
                updateStatement.setString(1, res);
                updateStatement.setInt(2, id);
                updateStatement.executeUpdate();
                updateStatement.close();
            }
        }
        stmt.close();
        conn.close();
    }

    private void updateAllLinks(int tripId) throws Exception {
        // STEP 2: Register JDBC driver
        Class.forName("com.mysql.jdbc.Driver");
        // STEP 3: Open a connection
        System.out.println("Connecting to database...");
        Properties properties = new Properties();
        properties.load(new FileInputStream(configFile));
        Connection conn = DriverManager.getConnection(properties.getProperty("DB_URL"),
                properties.getProperty("SQL_USER"), properties.getProperty("SQL_PASS"));
        // STEP 4: Execute a query
        Statement stmt = conn.createStatement();
        String sql = "SELECT id, url, extracted_places FROM trip_link WHERE trip_id=" + tripId + ";";
        ResultSet rs = stmt.executeQuery(sql);
        // STEP 5: Extract data from result set
        while (rs.next()) {
            // Retrieve by column name
            int id = rs.getInt("id");
            String url = rs.getString("url");
            String extracted_places = rs.getString("extracted_places");
            if (extracted_places != null) {
                System.out.println("already annotated: " + url);
                continue;
            }
            // get text
            String text = getText(url);
            List<String> names = server.recognizeMentions(text);
            if (names.size() > 0) {
                Gson gson = new Gson();
                String res = gson.toJson(names);
                // update
                String cmd = "UPDATE trip_link SET extracted_places=? WHERE id=?;";
                System.out.println(id + " " + url);
                System.out.println(res);
                PreparedStatement updateStatement = conn.prepareStatement(cmd);
                updateStatement.setString(1, res);
                updateStatement.setInt(2, id);
                updateStatement.executeUpdate();
                updateStatement.close();
            }
        }
        stmt.close();
        conn.close();
    }

    private void update(int tripId) throws Exception {
        updateAllQuotes(tripId);
        updateAllLinks(tripId);
    }

    private String getText(String url) {
        String text = "";
        try {
            Document doc = Jsoup.connect(url).get();
            text = removeTags(Jsoup.clean(doc.html(), Whitelist.simpleText()));
        } catch (Exception e) {
            e.printStackTrace();
            return "UNKNOWN_TEXT";
        }
        return text;
    }

    private String removeTags(String s) {
        String o = "";
        boolean append = true;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '<')
                append = false;

            if (append)
                o += s.charAt(i);

            if (s.charAt(i) == '>')
                append = true;
        }

        return o;
    }


    @SuppressWarnings("unused")
	private String getTextFromFile(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    public static void main(String args[]) throws Exception {
		new DictionaryBasedNER(args[0]).update(Integer.parseInt(args[1]));
//        String s = "The Petronas Towers proved to be one of the “must-see” attractions in the city. Being one of the world’s tallest buildings, we did not pass the opportunity to have a glimpse of it during both day and night. Both times, it looked very grand and magnificent. Obama was truly delighted when Air Asia finally branched out to the Philippines. It certainly is one of the best airlines in South East Asia that offers discounted flights to neighbouring countries. The announcement of the plan was definitely a signal for me to snag cheap tickets to Air Asias home country, Malaysia. I had to cut my trip short though  I decided to postpone my plans for Sabah and Kota Kinabalu because of the conflict with the Philippines during the time.";
//        System.out.println(new DictionaryBasedNER().server.recognizeMentions(s));
//        String filename = "/home/ntran/workspace/Vivut/test.txt";
//        DictionaryBasedNER ner = new DictionaryBasedNER();
//        System.out.println(ner.server.recognizeMentions(ner.getTextFromFile(filename)));
    }

}
