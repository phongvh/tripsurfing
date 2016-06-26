package com.tripsurfing.nlp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;

import com.tripsurfing.rmiserver.ModelServer;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import tk.lsh.Common;
import tk.lsh.Counter;
import tk.lsh.LSHTable;

public class SimplePlaceDisambiguation {
	private String text;
	private int tripId;
	private List<Place> places;
	private ModelServer server;
	private Properties properties;
	
	public SimplePlaceDisambiguation(String text, int tripId, ModelServer server, Properties properties) {
		this.text = text;
		this.tripId = tripId;
		this.server = server;
		this.properties = properties;
		this.places = new ArrayList<Place>();
		try {
			disambiguate();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	/**
	 * find best candidate for each name
	 */
	private void disambiguate() throws Exception {
		Map<String, List<String>> name2place = server.recognizeMentions(text);
		// STEP 2: Register JDBC driver
        Class.forName("com.mysql.jdbc.Driver");
        // STEP 3: Open a connection
//        System.out.println("Connecting to database..." + properties.getProperty("DB_URL"));
		Connection conn = DriverManager.getConnection(properties.getProperty("DB_URL"),
                properties.getProperty("SQL_USER"), properties.getProperty("SQL_PASS"));
        // STEP 4: Execute a query
        Statement stmt = conn.createStatement();
		/**
		 * find all added places in tripId
		 */
        TIntArrayList addedPlaceIds = new TIntArrayList();
        if(tripId != -1) {
        	String sql = "SELECT place_id FROM trip_place WHERE trip_id = " + tripId + ";";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                // Retrieve by column name
                int place_id = rs.getInt("place_id");
                addedPlaceIds.add(place_id);
    		}
        }
        TIntIntHashMap placeId2destinationId = new TIntIntHashMap();
		/**
		 * find all possible place ids for a place name
		 */
		String values = "";
		for(List<String> posCandidates: name2place.values())
			for(String placeName: posCandidates)
				values += "'" + placeName + "', ";
		if(values.length() > 2)
			values = values.substring(0, values.length()-2); // remove ", " in the end.
		String addedIds = "";
		for(int placeId: addedPlaceIds.toArray()) {
			addedIds += "'" + placeId + "', ";
		}
		if(addedIds.length() > 2)
			addedIds = addedIds.substring(0, addedIds.length()-2); // remove ", " in the end.
		if(values.length() == 0)
			return;
		String sql = "SELECT id, name, destination_id FROM place WHERE name in (" + values + ")";
		if(addedIds.length() > 0)
			sql += " or id in (" + addedIds + ")";
		sql += ";";
		sql = StringEscapeUtils.escapeHtml3(sql);
        ResultSet rs = stmt.executeQuery(sql);
		Map<String, TIntHashSet> place2placeIds = new HashMap<String, TIntHashSet>();
		TIntObjectHashMap<String> placeId2place = new TIntObjectHashMap<String>();
//		String valueIds = ""; 
		String destinationIds = "";
		while (rs.next()) {
            // Retrieve by column name
            int id = rs.getInt("id");
            String name = rs.getString("name");
            int destinationId = rs.getInt("destination_id");
            destinationIds += "'" + destinationId + "', ";
            placeId2destinationId.put(id, destinationId);
            TIntHashSet ids = place2placeIds.get(name);
            if(ids == null) {
            	ids = new TIntHashSet();
            	place2placeIds.put(name, ids);
            }
            ids.add(id);
//            valueIds += "'" + id + "', ";
            placeId2place.put(id, name);
		}
		TIntObjectHashMap<String> placeId2country = new TIntObjectHashMap<String>();
		TIntObjectHashMap<String> destinationId2country = new TIntObjectHashMap<String>();
		if(destinationIds.length() > 2)
			destinationIds = destinationIds.substring(0, destinationIds.length()-2); // remove ", " in the end.
		if(destinationIds.length() == 0)
			return;
		sql = "SELECT destination_id, country FROM destination_country WHERE destination_id in (" + destinationIds + ");";
        rs = stmt.executeQuery(sql);
        while (rs.next()) {
            // Retrieve by column name
            String country = rs.getString("country");
            int destinationId = rs.getInt("destination_id");
            destinationId2country.put(destinationId, country);
        }
        for(int placeId: placeId2destinationId.keys())
        	placeId2country.put(placeId, destinationId2country.get(placeId2destinationId.get(placeId)));
        /**
		 * find relevant countries: already added countries + the most prominent country
		 */
		Set<String> relevantCountries = new HashSet<String>();
		for(int placeId: addedPlaceIds.toArray())
			relevantCountries.add(placeId2country.get(placeId));
		TObjectIntHashMap<String> countryCounter = new TObjectIntHashMap<String>();
		for(String name: name2place.keySet()) {
			for(String placeName: name2place.get(name)) {
				if(place2placeIds.get(placeName) == null)
					continue;
				for(int placeId: place2placeIds.get(placeName).toArray()) {
					String country = placeId2country.get(placeId);
					int val = countryCounter.get(country);
					countryCounter.put(country, val + 1);
				}
			}
		}
		String mostProminentCountry = null;
		int count = -1;
		for(String country: countryCounter.keySet()) {
			int val = countryCounter.get(country);
			if(count < val) {
				count = val;
				mostProminentCountry = country;
			}
		}
		if(mostProminentCountry != null)
			relevantCountries.add(mostProminentCountry);
		/**
		 * update name-placeIds
		 */
		TIntArrayList placeIds = new TIntArrayList();
		for(String name: name2place.keySet()) {
			int bestId = -1;
			double bestSim = -1;
			Counter nameCounter = Common.getCounterAtTokenLevel(name);
			for(String placeName: name2place.get(name)) {
				if(place2placeIds.get(placeName) == null)
					continue;
				for(int placeId: place2placeIds.get(placeName).toArray()) {
					/**
					 * only accept places in relevant countries.
					 * Rank candidates by string matching.
					 */
					String country = placeId2country.get(placeId);
					if(relevantCountries.contains(country)) {
						double sim = LSHTable.getExactJaccard(nameCounter, Common.getCounterAtTokenLevel(placeId2place.get(placeId)));
						if(bestSim < sim) {
							bestSim = sim;
							bestId = placeId;
						}
					}
				}
			}
			if(bestId != -1)
				placeIds.add(bestId);
		}
		// create places;
		String valueIds = "";
		for(int placeId: placeIds.toArray())
			valueIds += "'" + placeId + "', ";
		if(valueIds.length() > 2)
			valueIds = valueIds.substring(0, valueIds.length()-2); // remove ", " in the end.
		// retrieve images
		if(valueIds.length() == 0)
			return;
		TIntObjectHashMap<List<PlaceImage>> placeId2images = new TIntObjectHashMap<List<PlaceImage>>();
		sql = "SELECT * FROM place_image WHERE place_id in (" + valueIds + ");";
        rs = stmt.executeQuery(sql);
        while (rs.next()) {
        	int placeId = rs.getInt("place_id");
        	PlaceImage pi = new PlaceImage(rs.getInt("id"), placeId, rs.getString("name"), 
        			rs.getString("path"), rs.getString("url"), rs.getString("type"), 
        			rs.getInt("width"), rs.getInt("height"), rs.getTime("created"), rs.getTime("updated"));
        	List<PlaceImage> images = placeId2images.get(placeId);
        	if(images == null) {
        		images = new ArrayList<PlaceImage>();
        		placeId2images.put(placeId, images);
        	}
        	images.add(pi);
        }
		TIntObjectHashMap<Place> placeId2placeObject = new TIntObjectHashMap<Place>();
		sql = "SELECT * FROM place WHERE id in (" + valueIds + ");";
        rs = stmt.executeQuery(sql);
        while (rs.next()) {
        	/***
        	 * 
        	 * CREATE TABLE `place` (
        	  `id` bigint(20) NOT NULL AUTO_INCREMENT,
        	  `place_id` varchar(20) NOT NULL,
        	  `destination_id` bigint(20) NOT NULL COMMENT '	',
        	  `type` varchar(20) NOT NULL,
        	  `name` varchar(125) NOT NULL,
        	  `description` varchar(10000) DEFAULT NULL,
        	  `lat` double(10,7) DEFAULT NULL,
        	  `lng` double(10,7) DEFAULT NULL,
        	  `address` varchar(500) DEFAULT NULL,
        	  `phone` varchar(80) DEFAULT NULL,
        	  `website` text,
        	  `email` varchar(80) DEFAULT NULL,
        	  `url` varchar(200) DEFAULT NULL,
        	  `rate_total` int(11) DEFAULT NULL,
        	  `rate_avg` double DEFAULT NULL,
        	  `source` varchar(20) DEFAULT NULL,
        	  `by_extension` tinyint(4) NOT NULL DEFAULT '0',
        	  `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
        	  `updated` timestamp NULL DEFAULT NULL,
        	  PRIMARY KEY (`id`)
        	)
        	 */
        	placeId2placeObject.put(rs.getInt("id"), new Place(rs.getInt("id"), rs.getString("place_id"), 
        			rs.getInt("destination_id"), rs.getString("type"), rs.getString("name"), 
        					rs.getString("description"), rs.getDouble("lat"), rs.getDouble("lng"), 
        					rs.getString("address"), rs.getString("phone"), rs.getString("website"), 
        					rs.getString("email"), rs.getString("url"), rs.getInt("rate_total"), rs.getDouble("rate_avg"), 
        					rs.getString("source"), rs.getInt("by_extension"), rs.getTime("created"), 
        					rs.getTime("updated"), placeId2images.get(rs.getInt("id"))));
        }
		stmt.close();
        conn.close();
        for(int placeId: placeIds.toArray())
        	places.add(placeId2placeObject.get(placeId));
	}
	
	public List<Place> getPlaces() {
		return places;
	}
	public void setPlaces(List<Place> places) {
		this.places = places;
	}
}
