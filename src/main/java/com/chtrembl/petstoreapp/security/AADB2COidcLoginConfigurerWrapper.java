package com.chtrembl.petstoreapp.security;

import com.azure.spring.cloud.autoconfigure.aadb2c.AadB2cOidcLoginConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Ebables Azure B2C if and only when the required Azure properties are present
 *
 */
@Component
@ConditionalOnProperty({
		"spring.cloud.azure.active-directory.b2c.base-uri",
		"spring.cloud.azure.active-directory.b2c.credential.client-id",
		"spring.cloud.azure.active-directory.b2c.credential.client-secret",
		"spring.cloud.azure.active-directory.b2c.logout-success-url"
})
public class AADB2COidcLoginConfigurerWrapper {

	@Autowired(required = false)
	private AadB2cOidcLoginConfigurer configurer = null;

	public AadB2cOidcLoginConfigurer getConfigurer() {
		return configurer;
	}

	public void setConfigurer(AadB2cOidcLoginConfigurer configurer) {
		this.configurer = configurer;
	}
}
