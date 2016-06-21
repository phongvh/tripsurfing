package com.tripsurfing.rmiserver;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;

import com.tripsurfing.nlp.Place;

public class SearchResult implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8376556639939776433L;
	private String title;
	private String url;
	private List<Place> places;
	
	public SearchResult(String title, String url, List<Place> places) {
		this.title = StringEscapeUtils.escapeHtml3(title);
		this.url = StringEscapeUtils.escapeHtml3(url);
		this.setPlaces(places);
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

	public List<Place> getPlaces() {
		return places;
	}

	public void setPlaces(List<Place> places) {
		this.places = places;
	}
	
}
