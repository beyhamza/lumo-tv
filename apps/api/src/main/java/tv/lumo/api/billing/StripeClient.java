package tv.lumo.api.billing;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.shared.config.LumoProperties;
import tv.lumo.api.shared.error.ApiException;

/**
 * The only class in this application that talks to Stripe.
 *
 * <p>Three calls, form-encoded, over Stripe's REST API — no SDK. The surface used
 * here is small and stable, and a dependency that pulls its own HTTP client and
 * JSON stack into a service that already has both would cost more than the forty
 * lines it saves.
 *
 * <h2>What this class does not do</h2>
 *
 * <p><b>It never grants anything.</b> A checkout session is a redirect target; the
 * entitlement moves when the provider says a payment succeeded, and only then
 * (ADR 0003). Nothing here writes {@code plan} or {@code status}.
 *
 * <h2>Logging</h2>
 *
 * <p>The secret key and the returned session URLs are never logged. A session URL
 * is a bearer credential for somebody's payment page, which is why the contract
 * types it {@code format: password}.
 */
@Component
public class StripeClient {

    private static final Logger log = LoggerFactory.getLogger(StripeClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final LumoProperties.Billing billing;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public StripeClient(LumoProperties properties, ObjectMapper objectMapper) {
        this.billing = properties.billing();
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.http = RestClient.builder()
                .baseUrl(this.billing.apiBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Creates a customer for an account that has never been billed.
     *
     * <p>The email is sent because an invoice has to reach someone. Nothing else
     * about the account is: not the display name, not the sources, not what they
     * watch. A payment provider needs to know who is paying, and that is all.
     */
    public String createCustomer(String email, String accountReference) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", email);
        // Lets a webhook, when one exists, find the account a customer belongs to
        // without keeping a mapping of its own.
        form.add("metadata[lumo_user_id]", accountReference);
        return post("/v1/customers", form).path("id").asString("");
    }

    /** @return the URL to redirect the user to. Sensitive: never logged. */
    public String createCheckoutSession(String customerRef, String locale,
                                        String successUrl, String cancelUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("customer", customerRef);
        form.add("line_items[0][price]", billing.priceId());
        form.add("line_items[0][quantity]", "1");
        form.add("success_url", successUrl);
        form.add("cancel_url", cancelUrl);
        form.add("locale", locale);
        if (billing.trialDays() > 0) {
            form.add("subscription_data[trial_period_days]", String.valueOf(billing.trialDays()));
        }
        return post("/v1/checkout/sessions", form).path("url").asString("");
    }

    /** @return the URL to redirect the user to. Sensitive: never logged. */
    public String createPortalSession(String customerRef, String locale, String returnUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", customerRef);
        form.add("return_url", returnUrl);
        form.add("locale", locale);
        return post("/v1/billing_portal/sessions", form).path("url").asString("");
    }

    // ---- transport ----------------------------------------------------------

    /**
     * One request, and one failure mode.
     *
     * <p>Everything that goes wrong on the provider's side is a 502 carrying
     * {@code INTERNAL_ERROR}: the caller did nothing wrong, and there is nothing a
     * client can do differently. The provider's own message is logged, never
     * returned — it is written for whoever holds the account, not for the user.
     */
    private JsonNode post(String path, MultiValueMap<String, String> form) {
        try {
            String body = http.post()
                    .uri(path)
                    .header("Authorization", "Bearer " + billing.secretKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.error("Stripe refused {}: HTTP {}", path, e.getStatusCode().value());
            throw providerUnavailable();
        } catch (Exception e) {
            log.error("Stripe call to {} failed", path, e);
            throw providerUnavailable();
        }
    }

    private static ApiException providerUnavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.INTERNAL_ERROR,
                "The payment provider did not answer");
    }
}
