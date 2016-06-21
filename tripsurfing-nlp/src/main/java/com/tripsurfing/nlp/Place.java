package com.tripsurfing.nlp;

import java.sql.Time;

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
 * 
 * @author datnb
 *
 */
public class Place {
	private int id;
	private String place_id;
	private int destination_id;
	private String type;
	private String name;
	private String description;
	private double lat;
	private double lng;
	private String address;
	private String phone;
	private String website;
	private String email;
	private String url;
	private int rate_total;
	private double rate_avg;
	private String source;
	private int by_extension;
	private Time created;
	private Time updated;
	
	public Place(int id, String place_id, int destination_id, String type, String name, 
			String description, double lat, double lng, String address, String phone,
			String website, String email, String url, int rate_total, double rate_avg,
			String source, int by_extension, Time created, Time updated) {
		this.setId(id);
		this.setPlace_id(place_id);
		this.setDestination_id(destination_id);
		this.setType(type);
		this.setName(name);
		this.setDescription(description);
		this.setLat(lat);
		this.setLng(lng);
		this.setAddress(address);
		this.setPhone(phone);
		this.setWebsite(website);
		this.setEmail(email);
		this.setUrl(url);
		this.setRate_total(rate_total);
		this.setRate_avg(rate_avg);
		this.setSource(source);
		this.setBy_extension(by_extension);
		this.setCreated(created);
		this.setUpdated(updated);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getPlace_id() {
		return place_id;
	}

	public void setPlace_id(String place_id) {
		this.place_id = place_id;
	}

	public int getDestination_id() {
		return destination_id;
	}

	public void setDestination_id(int destination_id) {
		this.destination_id = destination_id;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getLat() {
		return lat;
	}

	public void setLat(double lat) {
		this.lat = lat;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public double getLng() {
		return lng;
	}

	public void setLng(double lng) {
		this.lng = lng;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getWebsite() {
		return website;
	}

	public void setWebsite(String website) {
		this.website = website;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public int getRate_total() {
		return rate_total;
	}

	public void setRate_total(int rate_total) {
		this.rate_total = rate_total;
	}

	public double getRate_avg() {
		return rate_avg;
	}

	public void setRate_avg(double rate_avg) {
		this.rate_avg = rate_avg;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public int getBy_extension() {
		return by_extension;
	}

	public void setBy_extension(int by_extension) {
		this.by_extension = by_extension;
	}

	public Time getCreated() {
		return created;
	}

	public void setCreated(Time created) {
		this.created = created;
	}

	public Time getUpdated() {
		return updated;
	}

	public void setUpdated(Time updated) {
		this.updated = updated;
	}
}
