package com.tripsurfing.nlp;

import java.sql.Time;

public class PlaceImage {
	private int id;
	private int place_id;
	private String name;
	private String path;
	private String url;
	private String type;
	private int width;
	private int height;
	private Time created;
	private Time updated;
	
	public PlaceImage(int id, int place_id, String name, String path, String url, String type,
			int width, int height, Time created, Time updated) {
		this.id = id;
		this.place_id = place_id;
		this.name = name;
		this.path = path;
		this.url = url;
		this.type = type;
		this.width = width;
		this.height = height;
		this.created = created;
		this.updated = updated;
	}
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public int getPlace_id() {
		return place_id;
	}
	public void setPlace_id(int place_id) {
		this.place_id = place_id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public int getWidth() {
		return width;
	}
	public void setWidth(int width) {
		this.width = width;
	}
	public int getHeight() {
		return height;
	}
	public void setHeight(int height) {
		this.height = height;
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
