package com.tripsurfing.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import gnu.trove.map.hash.TObjectDoubleHashMap;

/**
 * Created by ntran on 17.02.16.
 */
public class Utils {

    private static HashMap<String, String> destinationType;
    private static Map<String, TObjectDoubleHashMap<String>> word2relatedWord;
    
    private static void update(String key, String val, double weight) {
    	TObjectDoubleHashMap<String> mp = word2relatedWord.get(key);
    	if(mp == null) {
    		mp = new TObjectDoubleHashMap<String>();
    		word2relatedWord.put(key, mp);
    	}
    	mp.put(val, weight);
    }
    
    public static TObjectDoubleHashMap<String> getRelatedWords(String word) {
    	if(word2relatedWord == null) {
    		word2relatedWord = new HashMap<String, TObjectDoubleHashMap<String>>();
    		//ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            InputStream is = Utils.class.getClassLoader().getResourceAsStream("wordvec_simwords");
            try {
                for (String line : Utils.readFileByLine(is)) {
                    String[] str = line.split("\t");
                    for(int i = 1; i < str.length; i += 2) {
                    	update(str[0], str[i], Double.parseDouble(str[i+1]));
                    	update(str[i], str[0], Double.parseDouble(str[i+1]));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
    	}
    	return word2relatedWord.get(word);
    }

    public static String getDestinationType(String tripadvisorId) {
        if (destinationType == null) {
            destinationType = new HashMap<String, String>();

            //ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            InputStream is = Utils.class.getClassLoader().getResourceAsStream("destination_type.txt");
            try {
                for (String line : Utils.readFileByLine(is)) {
                    String[] token = line.split("\\s+");
                    int t = Integer.parseInt(token[1]);
                    String type = (t == 1) ? "COUNTRY" : (t == 2) ? "REGION" : "CITY";

                    destinationType.put(token[0], type);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return destinationType.containsKey(tripadvisorId) ? destinationType.get(tripadvisorId) : "CITY";
    }

    public static List<String> readFileByLine(InputStream is) throws IOException {
        List<String> content = new ArrayList<String>();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            content.add(line);
        }
        reader.close();
        return content;
    }

    /**
     * all relevant links in a page.
     *
     * @param sourceLink
     * @param filter
     * @return
     * @throws IOException
     */
    private static Set<String> getLinks(String sourceLink, String filter) throws Exception {
        Set<String> res = new HashSet<String>();
        String code = filter.split("-")[0];
        Document document = Jsoup.connect(sourceLink).get();
        Elements links = document.select("a[href]");
        for (Element element : links) {
            String link = element.attr("abs:href");
            int index = link.indexOf("#");
            if (index != -1)
                link = link.substring(0, index);
            if (link.startsWith("http://www.tripadvisor.com/Restaurant_Review-" + code)
                    || link.startsWith("http://www.tripadvisor.com/Hotel_Review-" + code)
                    || link.startsWith("http://www.tripadvisor.com/Attraction_Review-" + code)) {
                res.add(link);
            }
        }
        return res;
    }


    private static boolean satisfiedConstrain(String link, String filter) {
        String[] prefix = new String[]{"http://www.tripadvisor.com/Restaurants-",
                "http://www.tripadvisor.com/Hotels-",
                "http://www.tripadvisor.com/Attractions-"
        };
        for (String s : prefix) {
            if (link.startsWith(s)) {
                String info = link.substring(s.length());
                if (info.startsWith(filter))
                    return true;
                String[] str = info.split("-");
                if (str.length > 1) {
                    if (str[0].equalsIgnoreCase(filter.split("-")[0])
                            && str[1].startsWith("oa")
                            && str[2].startsWith(filter.split("-")[1]))
                        return true;
                }
                break;
            }
        }

        return false;
    }

    private static Set<String> getListHotelRestaurantAttractionLinks(String sourceLink, String filter) throws Exception {
        Set<String> res = new HashSet<String>();
        Document document = Jsoup.connect(sourceLink).get();
        Elements links = document.select("a[href]");
        for (Element element : links) {
            String link = element.attr("abs:href");
//    		if(link.startsWith("http://www.tripadvisor.com/Restaurants-" + filter)
//    				|| link.startsWith("http://www.tripadvisor.com/Hotels-" + filter)
//    				|| link.startsWith("http://www.tripadvisor.com/Attractions-" + filter))
//    				|| link.startsWith("/Restaurants-" + filter)
//    				|| link.startsWith("/Hotels-" + filter)
//    				|| link.startsWith("/Attractions-" + filter))
            if (satisfiedConstrain(link, filter)) {
                res.add(link);
            }
//    		System.out.println(element.attr("abs:href"));
        }
        return res;
    }

    /**
     * crawl all relevant links connected to a page
     *
     * @param sourceLink
     * @param filter
     * @return
     * @throws IOException
     */
    public static Set<String> getAllLinks(String sourceLink, String filter) {
        long start = System.currentTimeMillis();
        Set<String> res = new HashSet<String>();
        Set<String> checkList = new HashSet<String>();
        Queue<String> queue = new LinkedBlockingQueue<String>();
        queue.add(sourceLink);
        res.add(sourceLink);
        checkList.add(sourceLink);
        int counter = 0;
        while (!queue.isEmpty()) {
            if (++counter % 10 == 0)
                System.out.println(counter + "/" + res.size() + "/" + queue.size() + "-" + (System.currentTimeMillis() - start));
            String link = queue.poll();
            try {
//    			Set<String> links = getLinks(link, filter);
                res.addAll(getLinks(link, filter));
                for (String nextLink : getListHotelRestaurantAttractionLinks(link, filter)) {
                    if (!checkList.contains(nextLink)) {
                        checkList.add(nextLink);
                        queue.add(nextLink);
//        				System.out.println("add " + nextLink);
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }
        System.out.println("Found: " + res.size() + " in: " + (System.currentTimeMillis() - start) + " ms.");
        return res;
    }

    public static void collectPlaceLinks(String srcFolder, String dstFolder) throws IOException {
        File folder = new File(srcFolder);
        for (File file : folder.listFiles()) {
            System.out.println(file.getPath());
            for (String cityInfo : readFileByLine(file.getPath())) {
                String city = cityInfo.substring("http://www.tripadvisor.com/Tourism-".length());
                String str[] = city.split("-");
                city = str[0] + "-" + str[1];
                System.out.println("collecting places in: " + city);
                long start = System.currentTimeMillis();
                PrintWriter writer = new PrintWriter(dstFolder + "/" + str[1], "UTF-8");
                for (String link : getAllLinks(cityInfo, city))
                    writer.write(link + "\n");
                writer.close();
                System.out.println("Done in: " + (System.currentTimeMillis() - start) + " ms");
            }
        }
    }

    public static List<String> readFileByLine(String filename) throws IOException {
        InputStream is = new FileInputStream(new File(filename));
        return readFileByLine(is);
    }
    
	/**
	 * This is to check each word in the given sentence is written in a special style comparing to others or not.
	 * 
	 * @param tokens
	 * @return boolean array b[] where b[i] = true if the i-th word in the given sentence is written in a special style. 
	 */
	public static boolean[] getStyles(String[] tokens) {
		boolean b[] = new boolean[tokens.length];
		int[] style = new int[tokens.length]; // 0 - not character word, 1 - lowercase, 2 - first character in uppercase, 3 - UPPER
		for(int i = 0; i < tokens.length; i ++) {
			String token = tokens[i];
			if(isWord(token)) {
				if(isUppercase(token))
					style[i] = 3;
				else if(isFirstCharacterUppercase(token))
					style[i] = 2;
				else
					style[i] = 1;
			}
			else {
				style[i] = 0;
			}
		}
		for(int i = 0; i < tokens.length; i ++) {
			/**
			 * look backward
			 */
			int j = i;
			while(j >= 0 && (style[j] == style[i] || style[j] == 0))
				j--;
			if(j >= 0 && style[j] != 0 && style[j] < style[i]) {
				b[i] = true;
				continue;
			}
			/**
			 * look forward
			 */
			j = i;
			while(j < tokens.length && (style[j] == style[i] || style[j] == 0))
				j ++;
			if(j < tokens.length && style[j] != 0 && style[j] < style[i]) {
				b[i] = true;
			}
		}
		return b;
	}
	
	/**
	 * @param word
	 * @return true if word is a character word (e.g. hello ---- not ab1cd)
	 */
	public static boolean isWord(String word) {
		if(word == null || word.length() < 1)
			return false;
		for(int i = 0; i < word.length(); i ++) {
			char ch = word.charAt(i);
			if(!('A' <= ch && ch <= 'Z') && !('a' <= ch && ch <= 'z'))
				return false;
		}
		return true;
	}
	
	/**
	 * 
	 * @param s
	 * @return true if the first character from 'A' to 'Z'
	 */
	public static boolean isFirstCharacterUppercase(String s) {
		if(s == null || s.length() < 1)
			return false;
		char ch = s.charAt(0);
		return 'A' <= ch && ch <= 'Z';
	}
	
	/**
	 * 
	 * @param word
	 * @return true if all characters of words is in uppercase form.
	 */
	public static boolean isUppercase(String word) {
		if(word == null || word.length() < 1)
			return false;
		for(int i = 0; i < word.length(); i ++) {
			char ch = word.charAt(i);
			if(!('A' <= ch && ch <= 'Z'))
				return false;
		}
		return true;
	}

    public static void main(String args[]) throws Exception {
//    	getAllLinks("http://www.tripadvisor.com/Tourism-g293924-Hanoi-Vacations.html", "g293924-Hanoi");
//        collectPlaceLinks("./html_sources", "./places_links");
    	TObjectDoubleHashMap<String> mp = getRelatedWords("family");
    	if(mp != null)
    		for(String key: mp.keySet())
    			System.out.println(key + " " + mp.get(key));
    		
    }

}
