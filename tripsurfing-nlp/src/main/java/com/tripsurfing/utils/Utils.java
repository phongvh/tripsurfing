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
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Created by ntran on 17.02.16.
 */
public class Utils {

    private static HashMap<String, String> destinationType;

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

    public static void main(String args[]) throws Exception {
//    	getAllLinks("http://www.tripadvisor.com/Tourism-g293924-Hanoi-Vacations.html", "g293924-Hanoi");
        collectPlaceLinks("./html_sources", "./places_links");
    }

}
