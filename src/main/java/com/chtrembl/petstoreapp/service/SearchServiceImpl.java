package com.chtrembl.petstoreapp.service;

import com.chtrembl.petstoreapp.model.ContainerEnvironment;
import com.chtrembl.petstoreapp.model.SearchResponse;
import com.chtrembl.petstoreapp.model.Value;
import com.chtrembl.petstoreapp.model.WebPages;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import javax.annotation.PostConstruct;

@Service
public class SearchServiceImpl implements SearchService {
	private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);

	final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
			false);

	private final ContainerEnvironment containerEnvironment;

	private WebClient bingSearchWebClient = null;

	public SearchServiceImpl(ContainerEnvironment containerEnvironment) {
		this.containerEnvironment = containerEnvironment;
	}

	@PostConstruct
	public void initialize() {
		this.bingSearchWebClient = WebClient.builder().baseUrl(this.containerEnvironment.getBingSearchURL())
				.build();
	}

	@Override
	public WebPages bingSearch(String query) {
		Exception e;

		try {
			String response = this.bingSearchWebClient.get().uri("v7.0/search?q=" + query)
					.accept(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
					.header("Ocp-Apim-Subscription-Key", this.containerEnvironment.getBingSearchSubscriptionKey())
					.header("Cache-Control", "no-cache").retrieve()
                    .bodyToMono(String.class)
                    .block();

			SearchResponse searchResponse = this.objectMapper.readValue(response, SearchResponse.class);
			return searchResponse.getWebPages();

		} catch (WebClientException | IllegalArgumentException | JsonProcessingException wce) {
			e = wce;
			logger.error("Error", e);
		}
		WebPages webpages = new WebPages();
		webpages.value = new Value[1];
		Value value = new Value();
		value.setName(e.getMessage());
		webpages.value[0] = value;

		return webpages;
	}
}
