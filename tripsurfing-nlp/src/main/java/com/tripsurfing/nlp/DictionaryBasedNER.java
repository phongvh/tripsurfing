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
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.gson.Gson;
import com.tripsurfing.rmiserver.ModelServer;

import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;


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
    private int LIMIT_LENGTH = 600;
    
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
        this();
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
            // check quote null
            if (quote == null || quote.length() == 0) {
                continue;
            }
            Map<String, List<String>> names = server.recognizeMentions(quote);
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
            if (url == null || url.length() == 0) {
                continue;
            }
            String extracted_places = rs.getString("extracted_places");
            if (extracted_places != null) {
                System.out.println("already annotated: " + url);
                continue;
            }
            // get text
            String text = getText(url);
            if (text == null || text.length() == 0) {
                continue;
            }
            Map<String, List<String>> names = server.recognizeMentions(text);
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
    
    /**
     * 
     * @param link
     * @param timeout
     * @return
     * @throws Exception
     */
    public List<SearchResult> summarize(String query, int timeout) throws Exception {
    	List<String[]> searchResults = server.getGoogleResults(query);
    	List<SearchResult> results = new ArrayList<SearchResult>();
    	if(searchResults.size() < 1)
    		return results;
    	List<Thread> threads = new ArrayList<Thread>();
    	List<QThread> qThreads = new ArrayList<DictionaryBasedNER.QThread>();
    	for(String[] searchResult: searchResults) {
    		QThread qt = new QThread(searchResult);
    		qThreads.add(qt);
    		threads.add(new Thread(qt));
    	}
		for(Thread thread: threads)
			thread.start();
		for(Thread thread: threads)
			thread.join(timeout);
		Set<String> snippets = new HashSet<String>();
		for(String s: searchResults.get(0)[2].split("::"))
			snippets.add(s);
		for(int i = 0; i < threads.size(); i ++) {
			QThread qThread = qThreads.get(i);
			Thread thread = threads.get(i);
			if (!thread.isAlive()) {
				String[] info = qThread.getInfo();
				if(info != null)
					results.add(new SearchResult(info[0], info[1], summarize(info[2], snippets)));
			}
			else {
				thread.interrupt();
			} 
		}
    	return results;
    }
    
    private List<String> getSentences(String text) throws Exception {
//		text.replaceAll("  ", ". ").replaceAll("\t", ". ");
		/**
		 * simple algorithm here...
		 */
		BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
		List<String> sentences = new ArrayList<String>();
		iterator.setText(text);
		int start = iterator.first();
		for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
			String sentence = text.substring(start, end);
			// sentenceWeight.put(sentence, computeWeight(sentence, query,
			// entityWeight));
			for (String s : sentence.split("\n")) {
				s = StringEscapeUtils.unescapeHtml3(s).trim();
				int x = s.indexOf("\t");
				int y = s.indexOf("  ");
				int index;
				if(x == -1 || y == -1)
					index = x + y + 1;
				else
					index = Math.min(x, y);
				if(index >= 0) {
					if(s.indexOf("\t", index + 1) != -1 || s.indexOf("  ", index + 1) != -1) {
						continue;
					}
				}
				if(s.length() > 2 && s.length() < 320) {
					sentences.add(s.replaceAll("  ", ". ").replaceAll("\t", ". "));
				}
			}
		}
		return sentences;
	}
    
    private Map<String, Set<String>> init(List<String> sentences, TObjectDoubleHashMap<String> entityWeight) {
		Map<String, Set<String>> sentence2relEntities = new HashMap<String, Set<String>>();
		for (String sentence : sentences) {
			Set<String> entities = new HashSet<String>();
			for (String entity : entityWeight.keySet()) {
				if (sentence.indexOf(entity + " ") != -1 || sentence.indexOf(" " + entity) != -1)
					entities.add(entity);
			}
			sentence2relEntities.put(sentence, entities);
		}
		return sentence2relEntities;
	}
    
    private double computeOverLap(String src, String dst, TObjectDoubleHashMap<String> entityWeight, Map<String, Set<String>> sentence2relEntities) {
	    double d = 0, d1 = 0, d2 = 0;
	    for(String entity: entityWeight.keySet()) {
//	      if(src.indexOf(entity) != -1) {
	      if(sentence2relEntities.get(src).contains(entity)) {
	        double val = entityWeight.get(entity); 
	        d1 += val;
//	        if(dst.indexOf(entity) != -1) {
	        if(sentence2relEntities.get(dst).contains(entity)) {
	          d += val;
	          d2 += val;
	        }
	      }
	      else {
//	        if(dst.indexOf(entity) != -1)
	        if(sentence2relEntities.get(dst).contains(entity))
	          d2 += entityWeight.get(entity);
	      }
	    }
//	    return d / Math.min(d1, d2);
	    return d / (d1 + d2 - d);
	  }
    
    private String summarize(String text, Set<String> snippets) throws Exception {
		TObjectDoubleHashMap<String> entityWeight = new TObjectDoubleHashMap<String>();
//		TObjectDoubleHashMap<String> entityDocumentCounter = new TObjectDoubleHashMap<String>();
		for(String strTok: snippets) {
			strTok = strTok.toLowerCase();
			if(strTok.length() < 2) 
				continue; // noise
			char ch = strTok.charAt(0);
			if(!(('a' <= ch && ch <= 'z') || ('A' <= ch && ch <= 'Z')))
				continue;
			entityWeight.put(strTok, 2);
		}
		List<String> sentences = getSentences(text);
		Map<String, List<String>> names = server.recognizeMentions(text);
//		Set<String> checkList = new HashSet<String>();
		for (String nameText : names.keySet()) {
			int index = nameText.indexOf("  ");
			if (index != -1)
				nameText = nameText.substring(0, index);
			index = nameText.indexOf("\t");
			if (index != -1)
				nameText = nameText.substring(0, index);
			if (entityWeight.containsKey(nameText))
				entityWeight.put(nameText, entityWeight.get(nameText) + 1);
			else
				entityWeight.put(nameText, 1);
		}
	
//		String[] sortNames = new String[entityWeight.keySet().size()];
//		int vt = 0;
//		for (String s : entityWeight.keySet())
//			sortNames[vt++] = s;
//		for(String s: sortNames) {
////			System.out.println(s + " " + entityWeight.get(s) + " " + entityDocumentCounter.get(s));
//			double d = entityDocumentCounter.get(s);
//			if(d < 1) d = 1;
//			entityWeight.put(s, entityWeight.get(s) * Math.exp(d));
//		}
//		for (int i = 0; i < sortNames.length; i++) {
//			for (int j = i + 1; j < sortNames.length; j++) {
//				if (entityWeight.get(sortNames[i]) < entityWeight.get(sortNames[j])) {
//					String tmp = sortNames[i];
//					sortNames[i] = sortNames[j];
//					sortNames[j] = tmp;
//				}
//			}
//		}
//		int limit = Math.min(5, sortNames.length);
////		TObjectDoubleHashMap<String> entityCounter = new TObjectDoubleHashMap<String>();
////		for(int i = 0; i < limit; i ++)
////			entityCounter.put(sortNames[i], entityWeight.get(sortNames[i]));
////		entityWeight = entityCounter;

		/**
		 * compute overlap between each pair of sentence
		 */
		Map<String, Set<String>> sentence2relEntities = init(sentences, entityWeight);
		TObjectIntHashMap<String> entity2idfWeight = new TObjectIntHashMap<String>();
		for(String sentence: sentence2relEntities.keySet()) {
			for(String entity: sentence2relEntities.get(sentence)) {
				if(entity2idfWeight.containsKey(entity))
					entity2idfWeight.put(entity, entity2idfWeight.get(entity) + 1);
				else
					entity2idfWeight.put(entity, 1);
			}
		}
		for(String entity: entity2idfWeight.keySet()) {
			entityWeight.put(entity, Math.log(entityWeight.get(entity)) * Math.log((double)sentence2relEntities.size() / entity2idfWeight.get(entity)));
		}
		double[][] overlap = new double[sentences.size()][sentences.size()];
		for (int i = 0; i < sentences.size(); i++) {
			for (int j = i + 1; j < sentences.size(); j++) {
				double o = computeOverLap(sentences.get(i), sentences.get(j), entityWeight, sentence2relEntities);
				if (!Double.isNaN(o)) {
					overlap[i][j] = o;
					overlap[j][i] = o;
				}
			}
		}

		double weight[] = new double[sentences.size()];
		String[] sentenceArr = new String[sentences.size()];
		for (int i = 0; i < sentences.size(); i++) {
			sentenceArr[i] = sentences.get(i);
			// compute the weight for this sentence
			double w = 0;
			for(String entity: sentence2relEntities.get(sentenceArr[i]))
				w += entityWeight.get(entity); // weight inside this sentence.
			for (int j = 0; j < sentences.size(); j++) {
				if (i != j) {
					w += overlap[i][j];
				}
			}
			weight[i] = w;
		}

		// sort sentences by weights
		int index[] = new int[sentences.size()];
		for (int i = 0; i < sentences.size(); i++)
			index[i] = i;
		for (int i = 0; i < index.length; i++) {
			for (int j = i + 1; j < index.length; j++) {
				if (weight[index[i]] < weight[index[j]]) {
					int tmp = index[i];
					index[i] = index[j];
					index[j] = tmp;
				}
			}
		}

		String res = "";
		for (int i = 0; i < sentences.size(); i++) {
			if(weight[index[i]] > 0)
//				res += sentenceArr[index[i]] + "\t" + weight[index[i]] + "\n";
				res += sentenceArr[index[i]] + "\n";
			if(res.length() > LIMIT_LENGTH)
				break;
		}
//		if(res.equalsIgnoreCase("")) {
//			for(int i = 0; i < limit; i ++)
//				res += sortNames[i] + ", ";
//		}
		return res;
	}

    public static void main(String args[]) throws Exception {
//    	System.out.println(new Gson().toJson(new DictionaryBasedNER().summarize("honeymoon in Thailand", 2000)));
    	if(args.length < 2)
    		return;
    	else if(args.length == 2) {
    		new DictionaryBasedNER(args[0]).update(Integer.parseInt(args[1]));
    	}
    	else {
    		System.out.println(new Gson().toJson(new DictionaryBasedNER(args[0]).summarize(args[1], Integer.parseInt(args[2]))));
    	}
		
//        String s = "The Petronas Towers proved to be one of the “must-see” attractions in the city. Being one of the world’s tallest buildings, we did not pass the opportunity to have a glimpse of it during both day and night. Both times, it looked very grand and magnificent. Obama was truly delighted when Air Asia finally branched out to the Philippines. It certainly is one of the best airlines in South East Asia that offers discounted flights to neighbouring countries. The announcement of the plan was definitely a signal for me to snag cheap tickets to Air Asias home country, Malaysia. I had to cut my trip short though  I decided to postpone my plans for Sabah and Kota Kinabalu because of the conflict with the Philippines during the time.";
//        System.out.println(new DictionaryBasedNER().server.recognizeMentions(s));
//        String filename = "/home/ntran/workspace/Vivut/test.txt";
//        DictionaryBasedNER ner = new DictionaryBasedNER();
//        System.out.println(ner.server.recognizeMentions(ner.getTextFromFile(filename)));
    }
    
    
    public class QThread implements Runnable {
		private String[] searchResult; // title, url, important keywords
		private String[] info; // title, url, text
		
		public QThread(String[] searchResult) {
			this.searchResult = searchResult;
		}

		

		public String[] getInfo() {
			return info;
		}


		public void run() {
			// TODO Auto-generated method stub
			try {
	            Document doc = Jsoup.connect(searchResult[1])
	                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36")
	                    .get(); // Jsoup.connect(url).get();
	            String text = doc.text();
	            info = new String[]{searchResult[0], searchResult[1], text};
	        } catch (Exception e) {
	        	
	        }
		}
	}

    class SearchResult {
    	private String title;
    	private String url;
    	private String summary;
    	
    	public SearchResult(String title, String url, String summary) {
    		this.title = StringEscapeUtils.escapeHtml3(title);
    		this.url = StringEscapeUtils.escapeHtml3(url);
    		this.summary = StringEscapeUtils.escapeHtml3(summary);
    	}
    	
		public String getTitle() {
			return title;
		}
		public void setTitle(String title) {
			this.title = title;
		}
		public String getUrl() {
			return url;
		}
		public void setUrl(String url) {
			this.url = url;
		}
		public String getSummary() {
			return summary;
		}
		public void setSummary(String summary) {
			this.summary = summary;
		}
    	
    	
    }
}
