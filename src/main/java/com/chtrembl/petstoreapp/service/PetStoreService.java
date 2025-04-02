package com.chtrembl.petstoreapp.service;

import java.util.Collection;
import java.util.List;

import com.chtrembl.petstoreapp.model.Order;
import com.chtrembl.petstoreapp.model.Pet;
import com.chtrembl.petstoreapp.model.Product;
import com.chtrembl.petstoreapp.model.Tag;

public interface PetStoreService {
	Collection<Pet> getPets(String category);

	Collection<Product> getProducts(String category, List<Tag> tags);

	void updateOrder(long productId, int quantity, boolean completeOrder);

	Order retrieveOrder(String orderId);
}
