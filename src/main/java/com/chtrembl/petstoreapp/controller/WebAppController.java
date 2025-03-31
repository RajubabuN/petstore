package com.chtrembl.petstoreapp.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;

import com.chtrembl.petstoreapp.model.ContainerEnvironment;
import com.chtrembl.petstoreapp.model.Order;
import com.chtrembl.petstoreapp.model.Pet;
import com.chtrembl.petstoreapp.model.User;
import com.chtrembl.petstoreapp.model.WebPages;
import com.chtrembl.petstoreapp.service.PetStoreService;
import com.chtrembl.petstoreapp.service.SearchService;
import com.microsoft.applicationinsights.telemetry.PageViewTelemetry;
import com.nimbusds.jose.shaded.json.JSONArray;

/**
 * Web Controller for all of the model/presentation construction and various
 * endpoints
 */
@Controller
public class WebAppController {
	private static Logger logger = LoggerFactory.getLogger(WebAppController.class);

	@Autowired
	private ContainerEnvironment containerEnvironment;

	@Autowired
	private PetStoreService petStoreService;

	@Autowired
	private SearchService searchService;

	@Autowired
	private User sessionUser;

	@Autowired
	private CacheManager currentUsersCacheManager;

	@ModelAttribute
	public void setModel(HttpServletRequest request, Model model, OAuth2AuthenticationToken token) {

		CaffeineCache caffeineCache = (CaffeineCache) currentUsersCacheManager
				.getCache(ContainerEnvironment.CURRENT_USERS_HUB);
		com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();

		// this is used for n tier correlated Telemetry. Keep the same one for anonymous
		// sessions that get authenticateds
		if (sessionUser.getSessionId() == null) {
			String sessionId = RequestContextHolder.currentRequestAttributes().getSessionId();
			sessionUser.setSessionId(sessionId);
			// put session in TTL cache so its there after initial login
			caffeineCache.put(sessionUser.getSessionId(), sessionUser.getName());
			containerEnvironment.sendCurrentUsers();
		}

		// put session in TTL cache to refresh TTL
		caffeineCache.put(sessionUser.getSessionId(), sessionUser.getName());

		if (token != null) {
			final OAuth2User user = token.getPrincipal();

			try {
				sessionUser.setEmail((String) ((JSONArray) user.getAttribute("emails")).get(0));
			} catch (Exception e) {
				logger.warn(String.format("PetStoreApp  %s logged in, however cannot get email associated: %s",
						sessionUser.getName(), e.getMessage()));
			}

			// this should really be done in the authentication/pre auth flow....
			sessionUser.setName((String) user.getAttributes().get("name"));

			if (!sessionUser.isInitialTelemetryRecorded()) {
				sessionUser.getTelemetryClient().trackEvent(
						String.format("PetStoreApp %s logged in, container host: %s", sessionUser.getName(),
								containerEnvironment.getContainerHostName()),
						sessionUser.getCustomEventProperties(), null);

				sessionUser.setInitialTelemetryRecorded(true);
			}
			model.addAttribute("claims", user.getAttributes());
			model.addAttribute("user", sessionUser.getName());
			model.addAttribute("grant_type", user.getAuthorities());
		}

		model.addAttribute("userName", sessionUser.getName());
		model.addAttribute("containerEnvironment", containerEnvironment);

		model.addAttribute("sessionId", sessionUser.getSessionId());

		model.addAttribute("appVersion", containerEnvironment.getAppVersion());

		model.addAttribute("cartSize", sessionUser.getCartCount());

		model.addAttribute("currentUsersOnSite", nativeCache.asMap().keySet().size());
		model.addAttribute("signalRNegotiationURL", containerEnvironment.getSignalRNegotiationURL());

		MDC.put("session_Id", sessionUser.getSessionId());
	}

	@GetMapping(value = "/login")
	public String login(Model model, HttpServletRequest request) throws URISyntaxException {
		logger.info("PetStoreApp /login requested, routing to login view...");

		PageViewTelemetry pageViewTelemetry = new PageViewTelemetry();
		pageViewTelemetry.setUrl(new URI(request.getRequestURL().toString()));
		pageViewTelemetry.setName("login");
		sessionUser.getTelemetryClient().trackPageView(pageViewTelemetry);
		return "login";
	}

	// multiple endpoints to generate some Telemetry and allowing for
	// differentiation
	@GetMapping(value = { "/dogbreeds", "/catbreeds", "/fishbreeds" })
	public String breeds(Model model, OAuth2AuthenticationToken token, HttpServletRequest request,
			@RequestParam(name = "category") String category) throws URISyntaxException {

		// quick validation, should really be done in validators, check for cross side
		// scripting etc....
		if (!"Dog".equals(category) && !"Cat".equals(category) && !"Fish".equals(category)) {
			return "home";
		}
		logger.info(String.format("PetStoreApp /breeds requested for %s, routing to breeds view...", category));

		model.addAttribute("pets", petStoreService.getPets(category));
		return "breeds";
	}

	@GetMapping(value = "/breeddetails")
	public String breedeetails(Model model, OAuth2AuthenticationToken token, HttpServletRequest request,
			@RequestParam(name = "category") String category, @RequestParam(name = "id") int id)
			throws URISyntaxException {

		// quick validation, should really be done in validators, check for cross side
		// scripting etc....
		if (!"Dog".equals(category) && !"Cat".equals(category) && !"Fish".equals(category)) {
			return "home";
		}

		if (null == sessionUser.getPets()) {
			petStoreService.getPets(category);
		}

		Pet pet = null;

		try {
			pet = sessionUser.getPets().get(id - 1);
		} catch (Exception npe) {
			sessionUser.getTelemetryClient().trackException(npe);
			pet = new Pet();
		}

		logger.info(String.format("PetStoreApp /breeddetails requested for %s, routing to dogbreeddetails view...",
				pet.getName()));

		model.addAttribute("pet", pet);

		return "breeddetails";
	}

	@GetMapping(value = "/products")
	public String products(Model model, OAuth2AuthenticationToken token, HttpServletRequest request,
			@RequestParam(name = "category") String category, @RequestParam(name = "id") int id) throws Exception {

		// quick validation, should really be done in validators, check for cross side
		// scripting etc....
		if (!"Toy".equals(category) && !"Food".equals(category)) {
			return "home";
		}
		logger.info(String.format("PetStoreApp /products requested for %s, routing to products view...", category));

		// for stateless container(s), this container may not have products loaded, this
		// is a temp fix until we implement caching, specifically a distributed/redis
		// cache
		Collection<Pet> pets = petStoreService.getPets(category);

		Pet pet = new Pet();

		if (pets != null) {
			pet = sessionUser.getPets().get(id - 1);
		}

		model.addAttribute("products",
				petStoreService.getProducts(pet.getCategory().getName() + " " + category, pet.getTags()));
		return "products";
	}

	@GetMapping(value = "/cart")
	public String cart(Model model, OAuth2AuthenticationToken token, HttpServletRequest request) {
		Order order = petStoreService.retrieveOrder(sessionUser.getSessionId());
		model.addAttribute("order", order);
		int cartSize = 0;
		if (order != null && order.getProducts() != null && !order.isComplete()) {
			cartSize = order.getProducts().size();
		}
		sessionUser.setCartCount(cartSize);
		model.addAttribute("cartSize", sessionUser.getCartCount());
		if (token != null) {
			model.addAttribute("userLoggedIn", true);
			model.addAttribute("email", sessionUser.getEmail());
		}
		return "cart";
	}

	@PostMapping(value = "/updatecart")
	public String updatecart(Model model, OAuth2AuthenticationToken token, HttpServletRequest request,
			@RequestParam Map<String, String> params) {
		int cartCount = 1;

		String operator = params.get("operator");
		if (StringUtils.isNotEmpty(operator)) {
			if ("minus".equals(operator)) {
				cartCount = -1;
			}
		}

		petStoreService.updateOrder(Long.valueOf(params.get("productId")), cartCount, false);
		return "redirect:cart";
	}

	@PostMapping(value = "/completecart")
	public String updatecart(Model model, OAuth2AuthenticationToken token, HttpServletRequest request) {
		if (token != null) {
			petStoreService.updateOrder(0, 0, true);
		}
		return "redirect:cart";
	}

	@GetMapping(value = "/claims")
	public String claims(Model model, OAuth2AuthenticationToken token, HttpServletRequest request)
			throws URISyntaxException {
		logger.info(String.format("PetStoreApp /claims requested for %s, routing to claims view...",
				sessionUser.getName()));
		return "claims";
	}

	@GetMapping(value = "/slowness")
	public String generateappinsightsslowness(Model model, OAuth2AuthenticationToken token, HttpServletRequest request)
			throws URISyntaxException, InterruptedException {
		logger.info("PetStoreApp simulating slowness, routing to home view...");

		PageViewTelemetry pageViewTelemetry = new PageViewTelemetry();
		pageViewTelemetry.setUrl(new URI(request.getRequestURL().toString()));
		pageViewTelemetry.setName("slow operation");

		// 30s delay
		Thread.sleep(30000);

		sessionUser.getTelemetryClient().trackPageView(pageViewTelemetry);

		return "slowness";
	}

	@GetMapping(value = "/exception")
	public String exception(Model model, OAuth2AuthenticationToken token, HttpServletRequest request)
			throws URISyntaxException, InterruptedException {

		NullPointerException npe = new NullPointerException();

		logger.info("PetStoreApp simulating NullPointerException, routing to home view..." + npe.getStackTrace());

		sessionUser.getTelemetryClient().trackException(npe);

		return "exception";
	}

	@GetMapping(value = "/*")
	public String landing(Model model, OAuth2AuthenticationToken token, HttpServletRequest request)
			throws URISyntaxException {
		logger.info(String.format("PetStoreApp %s requested and %s is being routed to home view session %s",
				request.getRequestURI(), sessionUser.getName(), sessionUser.getSessionId()));
		PageViewTelemetry pageViewTelemetry = new PageViewTelemetry();
		pageViewTelemetry.setUrl(new URI(request.getRequestURL().toString()));
		pageViewTelemetry.setName("landing");
		sessionUser.getTelemetryClient().trackPageView(pageViewTelemetry);
		return "home";
	}

	@GetMapping(value = "/bingSearch")
	public String bingSearch(Model model) throws URISyntaxException {
		logger.info(String.format("PetStoreApp /bingsearch requested for %s, routing to bingSearch view...",
				sessionUser.getName()));
		String companies[] = { "Chewy", "PetCo", "PetSmart", "Walmart" };
		List<String> companiesList = Arrays.asList(companies);
		List<WebPages> webpages = new ArrayList<>();
		companiesList.forEach(company -> webpages.add(searchService.bingSearch(company)));
		model.addAttribute("companies", companiesList);
		model.addAttribute("webpages", webpages);

		return "bingSearch";
	}
}
