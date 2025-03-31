package com.chtrembl.petstoreapp.service;

import com.chtrembl.petstoreapp.model.WebPages;
import org.springframework.stereotype.Service;

public interface SearchService {
	WebPages bingSearch(String query);
}
