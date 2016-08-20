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
import com.tripsurfing.utils.Utils;

import gnu.trove.map.hash.TObjectDoubleHashMap;


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
    
    public DictionaryBasedNER(String configFile) {
        this.configFile = configFile;
        String host = "localhost";
        try {
        	properties = new Properties();
            properties.load(new FileInputStream(this.configFile));
            Registry registry = LocateRegistry.getRegistry(host, Integer.parseInt(properties.getProperty("RMI_PORT")));
            server = (ModelServer) registry.lookup("TkServer_" + host);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }


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
            List<Place> places = new SimplePlaceDisambiguation(quote, tripId, null, server, properties).getPlaces();
            List<Integer> placeIds = new ArrayList<Integer>();
            for(Place place: places) {
            	placeIds.add(place.getId());
            }
//            if (places.size() > 0) {
                Gson gson = new Gson();
                String res = gson.toJson(placeIds);
                // update
                String cmd = "UPDATE trip_quote SET extracted_places=? WHERE id=?;";
//		        System.out.println(id + " " + quote);
                PreparedStatement updateStatement = conn.prepareStatement(cmd);
                updateStatement.setString(1, res);
                updateStatement.setInt(2, id);
                updateStatement.executeUpdate();
                updateStatement.close();
//            }
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
            List<Place> places = new SimplePlaceDisambiguation(text, tripId, null, server, properties).getPlaces();
            List<Integer> placeIds = new ArrayList<Integer>();
            for(Place place: places) {
            	placeIds.add(place.getId());
            }
//            if (places.size() > 0) {
                Gson gson = new Gson();
                String res = gson.toJson(placeIds);
                // update
                String cmd = "UPDATE trip_link SET extracted_places=? WHERE id=?;";
//                System.out.println(id + " " + url);
//                System.out.println(res);
                PreparedStatement updateStatement = conn.prepareStatement(cmd);
                updateStatement.setString(1, res);
                updateStatement.setInt(2, id);
                updateStatement.executeUpdate();
                updateStatement.close();
//            }
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
//            e.printStackTrace();
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
     * best places in Hanoi::history, family::Vietnam
     * @param link
     * @param timeout
     * @return
     * @throws Exception
     */
    public List<SearchResult> summarize(String queryFull, int tripId, int timeout) throws Exception {
    	// best places in Hanoi::history, family::Vietnam
    	String info[] = queryFull.split("::");
    	if(info.length != 3)
    		return new ArrayList<SearchResult>();
//    	String query = info[0];
//    	if(!activeCategoryFilter)
//    		query += " " + info[1];
    	boolean activeCategoryFilter = !info[1].isEmpty();
    	List<String[]> searchResults = server.getGoogleResults(queryFull);
    	List<SearchResult> results = new ArrayList<SearchResult>();
    	if(searchResults.size() < 1)
    		return results;
    	List<Thread> threads = new ArrayList<Thread>();
    	List<QThread> qThreads = new ArrayList<QThread>();
    	for(String[] searchResult: searchResults) {
    		QThread qt = new QThread(searchResult, tripId, info[2], server, properties);
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
		if(activeCategoryFilter) {
			List<SearchResult> filteredResults = new ArrayList<SearchResult>();
			for(SearchResult rs: results) {
				List<Place> places = rs.getPlaces();
				List<Place> filteredPlaces = new ArrayList<Place>();
				for(Place place: places) {
					// check if related to a topic
					String description = place.getDescription();
					if(description == null)
						continue;
					boolean b = false;
					for(String cate: info[1].split(",")) {
						String category = cate.trim();
						if(description.indexOf(category) != -1) {
							b = true;
							break;
						}
						TObjectDoubleHashMap<String> relatedWords = Utils.getRelatedWords(category);
						if(relatedWords != null) {
							for(String relWord: relatedWords.keySet()) {
								if(description.indexOf(relWord) != -1) {
									b = true;
									break;
								}
							}
						}
						if(b) break;
					}
					if(b)
						filteredPlaces.add(place);
				}
				if(!filteredPlaces.isEmpty()) {
					rs.setPlaces(filteredPlaces);
					filteredResults.add(rs);
				}
			}
			results = filteredResults;
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
		private String targetCountry;
    	private List<Place> places;
    	private ModelServer server;
    	private Properties properties;
		
		public QThread(String[] searchResult, int tripId, String targetCountry, ModelServer server, Properties properties) {
			this.searchResult = searchResult;
			this.tripId = tripId;
			this.targetCountry = targetCountry;
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
	            places = new SimplePlaceDisambiguation(text, tripId, targetCountry, server, properties).getPlaces();
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
            System.out.println("update trip-" + id);
            update(id, true);
        }
        stmt.close();
        conn.close();
    }
    
    public static void main(String args[]) throws Exception {
//    	System.out.println(new Gson().toJson(new DictionaryBasedNER("./src/main/resources/vivut.properties")
//    			.summarize("Phu Quoc", 74, 1000)));
//    	new DictionaryBasedNER("./src/main/resources/vivut.properties").fullUpdate();
    	if(args.length == 1) {
    		new DictionaryBasedNER(args[0]).fullUpdate();
    	}
    	else if(args.length == 2) {
    		new DictionaryBasedNER(args[0]).update(Integer.parseInt(args[1]), false);
    	}
    	else if(args.length > 3) {
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
