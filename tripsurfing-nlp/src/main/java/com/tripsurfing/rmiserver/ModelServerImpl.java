package com.tripsurfing.rmiserver;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tripsurfing.utils.Utils;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.crf.CRFCliqueTree;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import tk.lsh.Common;
import tk.lsh.LSHTable;

public class ModelServerImpl implements ModelServer {

    private static final Logger logger = LoggerFactory.getLogger(ModelServerImpl.class);
    /**
     * fast and furious
     */
    private boolean FAST_SETTING = true;
    private int LOWER_BOUND_TOKEN_LENGTH = 1;

    private Properties properties;
    private Set<String> dictionary;
    private MaxentTagger tagger;
    private int LIMIT_LENGTH = 6;
    private CRFClassifier<CoreLabel> classifier;
    private LSHTable lsh;
    private Map<String, List<String[]>> googleResults;
    private int CACHE_SIZE = 100;
    private String[] keys = new String[CACHE_SIZE];
    private int oldestKey = -1;
    private static String google = "http://www.google.com/search?as_q=";
	private static String charset = "UTF-8";
	@SuppressWarnings("unused")
	private static String userAgent = "Googlebot";
	private static int K = 5;
	/**
	 * noisy names
	 */
	private String[] noise = new String[]{"menu", "man", "street", "ocean", "sea", 
			"food", "road", "tour", "city", "hotel", "house", "bridge", "restaurant",
		    "menus", "men", "streets", "oceans", "seas", "foods", "roads", "tours", 
		    "cities", "hotels", "houses", "bridges", "restaurants", "review", "reviews"};
	private Set<String> noisyNames;
	private boolean isNoisyName(String name) {
		if(noisyNames == null) {
			noisyNames = new HashSet<String>();
			for(String s: noise)
				noisyNames.add(s);
		}
		return noisyNames.contains(name);
	}

    public ModelServerImpl() {
        try {
            properties = new Properties();
            properties.load(this.getClass().getClassLoader().getResourceAsStream("vivut.properties"));
            googleResults = new HashMap<String, List<String[]>>();
        } catch (Exception e) {
            //
        }
    }

    public ModelServerImpl(String configFile) {
        try {
            properties = new Properties();
            properties.load(new FileInputStream(configFile));
            googleResults = new HashMap<String, List<String[]>>();
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
    
    private void loadDictionary() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(this.getClass().
                getClassLoader().getResourceAsStream("tripsurfing.dict")));

        dictionary = new HashSet<String>();
        lsh = new LSHTable(2, 8, 100, 999999999, 0.6);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0)
                    continue;
                String placeName = line.toLowerCase();
                dictionary.add(placeName);
                lsh.put(Common.getCounterAtTokenLevel(placeName));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("Loaded: {} names.", dictionary.size());
    }

    public List<int[]> getNames(String text) {
        if (classifier == null) {
            classifier = CRFClassifier.getClassifierNoExceptions(properties.getProperty("NER_TAGGER"));
        }
        List<int[]> names = new ArrayList<int[]>();
        List<List<CoreLabel>> out = classifier.classify(text);
        int tokenIndex = 0;
        for (List<CoreLabel> cl : out) {
            CRFCliqueTree<String> cliqueTree = classifier.getCliqueTree(cl);
            int i = 0;
            while (i < cliqueTree.length()) {
                CoreLabel word = cl.get(i);
                if (word.get(AnswerAnnotation.class).equalsIgnoreCase("O")) {
                    i++;
                    continue;
                }
                int j = i;
                while (j < cl.size()
                        && cl.get(j).get(AnswerAnnotation.class).equalsIgnoreCase(word.get(AnswerAnnotation.class)))
                    j++;
                names.add(new int[]{i + tokenIndex, j + tokenIndex}); // from i-th to (j-1)th
                i = j;
            }
            tokenIndex += cliqueTree.length();
        }
        return names;
    }
    
    /**
     * get tags via Stanford tool
     * @param sentence
     * @return
     */
    private String[] getTags(String sentence) {
    	if (tagger == null) {
            // Initialize the tagger
            tagger = new MaxentTagger(properties.getProperty("POS_TAGGER"));
        }
        // The tagged string
        String tagged = tagger.tagString(sentence);
        return tagged.split(" ");
    }
    
    /**
     * get tags via fast settings
     */
    private String[] getFastTags(String sentence) {
    	String[] tokens = sentence.split(" ");
    	boolean[] styles = Utils.getStyles(tokens);
    	String[] pos = new String[styles.length];
    	for(int i = 0; i < pos.length; i ++) {
    		pos[i] = tokens[i] + "_";
    		pos[i] += styles[i] ? "NNP" : "OTHER";
    	}
    	return pos;
    }


    public synchronized Map<String, List<String>> recognizeMentions(String sentence) throws RemoteException {
        if (dictionary == null) {
            loadDictionary();
        }
        // TODO: implement tokenizer or call Stanford tokenizer
        Map<String, List<String>> res = new HashMap<String, List<String>>();
        String[] tokens = FAST_SETTING ? getFastTags(sentence) : getTags(sentence);
        List<int[]> names = new ArrayList<int[]>();
        if(!FAST_SETTING)
        	names = getNames(sentence);
        boolean[] isNames = new boolean[tokens.length];
        int nameIndex = 0;
        for (int i = 0; i < tokens.length; ) {
            if (nameIndex < names.size() && names.get(nameIndex)[1] == i) {
                boolean added = true, overlap = false;
                for (int t = names.get(nameIndex)[0]; t < names.get(nameIndex)[1]; t++) {
                    if (!isNames[t])
                        added = false;
                    else
                        overlap = true;
                }
                if (!added) {
                    if (overlap)
                        res.remove(res.size() - 1);
                    String s = "";
                    for (int t = names.get(nameIndex)[0]; t < names.get(nameIndex)[1]; t++)
                        s += " " + tokens[t].split("_")[0];
                    String canName = s.substring(1);
//                    System.out.println("canName: " + canName); 
//                    if (dictionary.contains(canName.toLowerCase()))
//                    	res.add(canName);
                   if(!isNoisyName(canName.toLowerCase())) {
                	   List<String> candidates = lsh.deduplicate(Common.getCounterAtTokenLevel(canName.toLowerCase()));
                       if(candidates.size() > 0)
                    	   res.put(canName, candidates);
                   }
                }
            }
            while (nameIndex < names.size() &&
                    names.get(nameIndex)[1] < i) {
                nameIndex++;
            }
            int nextPos = i + 1;
            String[] info = tokens[i].split("_");
            if (info.length > 1 && info[1].equalsIgnoreCase("NNP")) {
                for (int j = LIMIT_LENGTH; j >= 0; j--) {
                    String s = info[0];
                    for (int t = LOWER_BOUND_TOKEN_LENGTH + 1; t < j && i + t < tokens.length; t++)
                        s += " " + tokens[i + t].split("_")[0];
                    if (!isNoisyName(s.toLowerCase())  
                    		&& (dictionary.contains(s.toLowerCase()) 
                    				|| lsh.deduplicate(Common.getCounterAtTokenLevel(s.toLowerCase())).size() > 0)) {
                    	List<String> candidates = new ArrayList<String>();
                    	candidates.add(s);
                        res.put(s, candidates);
                        nextPos = i + j;
                        for (int t = 0; t < j && i + t < tokens.length; t++)
                            isNames[i + t] = true;
                        break;
                    }
                }
            }
            i = nextPos;
        }
        return res;
    }
    
    
    public List<String[]> getGoogleResults(String query) throws RemoteException {
    	long beginTime = System.currentTimeMillis();
    	List<String[]> results = googleResults.get(query);
    	if(results != null) {
    		System.out.println("get results in: " + (System.currentTimeMillis() - beginTime) + " ms.");
    		return results;
    	}
    	results = new ArrayList<String[]>();
    	try {
    		Document doc = Jsoup.connect(google + URLEncoder.encode(query, charset) + "&lr=lang_en")
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36")
                    .get();
//    		Document doc = Jsoup
//    				.connect(
//    						google + URLEncoder.encode(query, charset)
//    								+ "&lr=lang_en").userAgent(userAgent).get();
    		Set<String> snipSet = new HashSet<String>();
    		String snippet = "";
			for(Element ele: doc.select("b")) {
//				System.out.println(ele.html());
				snipSet.add(ele.text().toLowerCase());
			}
			for(String s: snipSet)
				snippet += s + "::";
    		for (Element element : doc.select("h3.r")) {
    			String url = element.html();
    			if (!url.startsWith("<a href="))
    				continue;
//    			System.out.println(element.html());
    			url = URLDecoder.decode(url.substring(url.indexOf("href=\"") + 6, url.indexOf("\"", url.indexOf("href=\"") + 6)), "UTF-8");
//    			System.out.println(url);
//    			String snippet = Jsoup.parse(element.select("span").toString())
//    					.text();
    			String title = element.text();
    			String[] info = new String[]{title, url, snippet};
    			results.add(info);
    			if(results.size() == K)
    				break;
    		}
    		googleResults.put(query, results);
			oldestKey = (oldestKey + 1) % CACHE_SIZE;
			keys[oldestKey] = query;
		} catch (Exception e) {
			logger.info(e.getMessage());
		}
    	return results;
    }
    
}
