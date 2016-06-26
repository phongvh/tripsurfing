package com.tripsurfing.nlp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.gson.Gson;
import com.tripsurfing.rmiserver.ModelServer;
import com.tripsurfing.rmiserver.SearchResult;


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
//    private int LIMIT_LENGTH = 600;
    private Properties properties;
    
//    public DictionaryBasedNER() {
//        String host = "localhost";
//        try {
//            Registry registry = LocateRegistry.getRegistry(host, 52478);
//            server = (ModelServer) registry.lookup("TkServer_" + host);
//        } catch (Exception e) {
//            // TODO: handle exception
//        }
//    }

    public DictionaryBasedNER(String configFile) {
        this.configFile = configFile;
        String host = "localhost";
        try {
            Registry registry = LocateRegistry.getRegistry(host, 52478);
            server = (ModelServer) registry.lookup("TkServer_" + host);
            properties = new Properties();
            properties.load(new FileInputStream(this.configFile));
        } catch (Exception e) {
            // TODO: handle exception
        }
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

    private void updateAllQuotes(int tripId, boolean restart) throws Exception {
        // STEP 2: Register JDBC driver
        Class.forName("com.mysql.jdbc.Driver");
        // STEP 3: Open a connection
//        System.out.println("Connecting to database...");
        Connection conn = DriverManager.getConnection(properties.getProperty("DB_URL"),
                properties.getProperty("SQL_USER"), properties.getProperty("SQL_PASS"));
        // STEP 4: Execute a query
        Statement stmt = conn.createStatement();
        String sql = "SELECT id, content, extracted_places FROM trip_quote WHERE trip_id=" + tripId + ";";
        ResultSet rs = stmt.executeQuery(sql);
        // STEP 5: Extract data from result set
        while (rs.next()) {
            // Retrieve by column name
            int id = rs.getInt("id");
            String quote = rs.getString("content");
            // check quote null
            if (quote == null || quote.length() == 0) {
                continue;
            }
            String extracted_places = rs.getString("extracted_places");
            if (extracted_places != null && !restart) {
                System.out.println("already annotated: " + quote);
                continue;
            }
//            Map<String, List<String>> names = server.recognizeMentions(quote);
            List<Place> places = new SimplePlaceDisambiguation(quote, tripId, server, properties).getPlaces();
            if (places.size() > 0) {
                Gson gson = new Gson();
                String res = gson.toJson(places);
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

    private void updateAllLinks(int tripId, boolean restart) throws Exception {
        // STEP 2: Register JDBC driver
        Class.forName("com.mysql.jdbc.Driver");
        // STEP 3: Open a connection
//        System.out.println("Connecting to database...");
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
            if (url == null || url.length() == 0) {
                continue;
            }
            String extracted_places = rs.getString("extracted_places");
            if (extracted_places != null && !restart) {
                System.out.println("already annotated: " + url);
                continue;
            }
            // get text
            String text = getText(url);
            if (text == null || text.length() == 0) {
                continue;
            }
//            Map<String, List<String>> names = server.recognizeMentions(text);
            List<Place> places = new SimplePlaceDisambiguation(text, tripId, server, properties).getPlaces();
            if (places.size() > 0) {
                Gson gson = new Gson();
                String res = gson.toJson(places);
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

    private void update(int tripId, boolean restart) throws Exception {
        updateAllQuotes(tripId, restart);
        updateAllLinks(tripId, restart);
    }

    private String getText(String url) {
        String text = "";
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36")
                    .get(); // Jsoup.connect(url).get();
//            text = removeTags(Jsoup.clean(doc.html(), Whitelist.simpleText()));
            doc.select("a").remove();
            text = doc.text();
        } catch (Exception e) {
            e.printStackTrace();
            return "UNKNOWN_TEXT";
        }
        return text;
    }

    @SuppressWarnings("unused")
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

    /**
     * 
     * @param link
     * @param timeout
     * @return
     * @throws Exception
     */
    public List<SearchResult> summarize(String query, int tripId, int timeout) throws Exception {
    	List<String[]> searchResults = server.getGoogleResults(query);
    	List<SearchResult> results = new ArrayList<SearchResult>();
    	if(searchResults.size() < 1)
    		return results;
    	List<Thread> threads = new ArrayList<Thread>();
    	List<QThread> qThreads = new ArrayList<QThread>();
    	for(String[] searchResult: searchResults) {
    		QThread qt = new QThread(searchResult, tripId, server, properties);
    		qThreads.add(qt);
    		threads.add(new Thread(qt));
    	}
		for(Thread thread: threads) {
			thread.start();
		}
		for(Thread thread: threads) {
			thread.join(timeout);
		}
		// summary
		for(int i = 0; i < threads.size(); i ++) {
			QThread qThread = qThreads.get(i);
			Thread thread = threads.get(i);
			if (!thread.isAlive()) {
				results.add(new SearchResult(qThread.getTitle(), qThread.getUrl(), qThread.getPlaces()));
			}
			else {
				thread.interrupt();
			} 
		}
    	return results;
    }
    
    

    /**
     * 
     * @author datnb
     *
     */
    public class QThread implements Runnable {
		private String[] searchResult; // title, url, important keywords
//		private String text; // title, url, text
		private int tripId;
    	private List<Place> places;
    	private ModelServer server;
    	private Properties properties;
		
		public QThread(String[] searchResult, int tripId, ModelServer server, Properties properties) {
			this.searchResult = searchResult;
			this.tripId = tripId;
			this.server = server;
			this.properties = properties;
		}

		
		public String getTitle() {
			return searchResult[0];
		}
		
		public String getUrl() {
			return searchResult[1];
		}
		
		public List<Place> getPlaces() {
			return places;
		}


		public void run() {
			// TODO Auto-generated method stub
			try {
	            Document doc = Jsoup.connect(searchResult[1])
	                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36")
	                    .get(); // Jsoup.connect(url).get();
	            String text = doc.text();
	            places = new SimplePlaceDisambiguation(text, tripId, server, properties).getPlaces();
	        } catch (Exception e) {
	        	
	        }
		}
	}
//    
//    public class SThread implements Runnable {
//    	private String title;
//    	private String url;
//    	private String text;
//    	private int tripId;
//    	private List<Place> places;
//    	private ModelServer server;
//    	private Properties properties;
//		
//		public SThread(String title, String url, String text, int tripId, ModelServer server, Properties properties) {
//			this.title = title;
//			this.url = url;
//			this.text = text;
//			this.tripId = tripId;
//			this.server = server;
//			this.properties = properties;
//		}
//		
//		
//		public String getTitle() {
//			return title;
//		}
//		
//		public String getUrl() {
//			return url;
//		}
//		
//		public List<Place> getPlaces() {
//			return places;
//		}
//
//
//		public void run() {
//			// TODO Auto-generated method stub
//			try {
//	            places = new SimplePlaceDisambiguation(text, tripId, server, properties).getPlaces();
//	        } catch (Exception e) {
//	        	
//	        }
//		}
//
//
//	}
//
//
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
    
    private void fullUpdate() throws Exception {
    	Class.forName("com.mysql.jdbc.Driver");
        // STEP 3: Open a connection
//        System.out.println("Connecting to database...");
        Connection conn = DriverManager.getConnection(properties.getProperty("DB_URL"),
                properties.getProperty("SQL_USER"), properties.getProperty("SQL_PASS"));
        // STEP 4: Execute a query
        Statement stmt = conn.createStatement();
        String sql = "SELECT id FROM trip";
        ResultSet rs = stmt.executeQuery(sql);
        while (rs.next()) {
            // Retrieve by column name
            int id = rs.getInt("id");
            update(id, true);
        }
        stmt.close();
        conn.close();
    }
    
    public static void main(String args[]) throws Exception {
//    	System.out.println(new Gson().toJson(new DictionaryBasedNER("./src/main/resources/vivut.properties")
//    			.summarize("honeymoon in Vietnam", 74, 1000)));
    	if(args.length < 2) {
    		if(args[0].equalsIgnoreCase("annotate")) {
    			new DictionaryBasedNER(args[0]).fullUpdate();
    		}
    		return;
    	}
    	else if(args.length == 2) {
    		new DictionaryBasedNER(args[0]).update(Integer.parseInt(args[1]), false);
    	}
    	else {
    		System.out.println(new Gson().toJson(new DictionaryBasedNER(args[0]).summarize(args[1], 
    				Integer.parseInt(args[2]), Integer.parseInt(args[3]))));
    	}
		
//        String s = "The Petronas Towers proved to be one of the “must-see” attractions in the city. Being one of the world’s tallest buildings, we did not pass the opportunity to have a glimpse of it during both day and night. Both times, it looked very grand and magnificent. Obama was truly delighted when Air Asia finally branched out to the Philippines. It certainly is one of the best airlines in South East Asia that offers discounted flights to neighbouring countries. The announcement of the plan was definitely a signal for me to snag cheap tickets to Air Asias home country, Malaysia. I had to cut my trip short though  I decided to postpone my plans for Sabah and Kota Kinabalu because of the conflict with the Philippines during the time.";
//        System.out.println(new DictionaryBasedNER().server.recognizeMentions(s));
//        String filename = "/home/ntran/workspace/Vivut/test.txt";
//        DictionaryBasedNER ner = new DictionaryBasedNER();
//        System.out.println(ner.server.recognizeMentions(ner.getTextFromFile(filename)));
    }
    
}
