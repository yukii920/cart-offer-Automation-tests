package com.springboot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.controller.OfferRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.integration.ClientAndServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CartOfferApplicationTests {

    private ClientAndServer mockServer;

    @Before
    public void startMockServer() {
        // Start MockServer on port 1080.
        mockServer = ClientAndServer.startClientAndServer(1080);
        // Add a mock for the user segment API:
        // When a GET request is made to /api/v1/user_segment with query parameter user_id=1,
        // respond with a JSON body {"segment": "p1"}.
        mockServer.when(
                request()
                        .withMethod("GET")
                        .withPath("/api/v1/user_segment")
                        .withQueryStringParameter("user_id", "1")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"segment\": \"p1\" }")
        );
    }

    @After
    public void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    /**
     * Helper method to send a POST request and return the response as a String.
     */
    private String sendPost(String urlString, String jsonPayload) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        OutputStream os = con.getOutputStream();
        os.write(jsonPayload.getBytes());
        os.flush();
        os.close();

        int responseCode = con.getResponseCode();
        // Optionally log the response code
        // System.out.println("POST Response Code :: " + responseCode);

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        con.disconnect();
        return response.toString();
    }

    /**
     * Helper method to create an HttpURLConnection (used in some tests such as rate limiting).
     */
    private HttpURLConnection createConnection(String urlString) throws Exception {
        URL url = new URL(urlString);
        return (HttpURLConnection) url.openConnection();
    }

    /**
     * Sample method to add an offer.
     * URL: POST http://localhost:9001/api/v1/offer
     */
    public boolean addOffer(OfferRequest offerRequest) throws Exception {
        String urlString = "http://localhost:9001/api/v1/offer";
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");

        ObjectMapper mapper = new ObjectMapper();
        String postParams = mapper.writeValueAsString(offerRequest);
        OutputStream os = con.getOutputStream();
        os.write(postParams.getBytes());
        os.flush();
        os.close();

        int responseCode = con.getResponseCode();
        System.out.println("Add Offer POST Response Code :: " + responseCode);

        // (Optional) Read the response body if needed.
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            System.out.println("Response :: " + response.toString());
        } else {
            System.out.println("POST request did not work.");
        }
        con.disconnect();
        return true;
    }

    // ---------------------------
    // Provided Sample Test Case
    // ---------------------------

    @Test
    public void checkFlatXForOneSegment() throws Exception {
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "FLATX", 10, segments);
        boolean result = addOffer(offerRequest);
        Assert.assertTrue(result); // Able to add offer successfully
    }

    // ---------------------------
    // Test Cases for the Cart API
    // ---------------------------

    // TC01: Apply FLATX Offer
    @Test
    public void testApplyFlatXOffer_TC01() throws Exception {
        // Assumes an offer for segment "p1" is already added for restaurant 1.
        String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1}";
        String response = sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response, Map.class);
        int updatedCartValue = (Integer) map.get("cart_value");
        // Expecting a discount of 10 (200 - 10 = 190)
        Assert.assertEquals(190, updatedCartValue);
    }

    // TC02: Apply FLATX% Offer
    @Test
    public void testApplyFlatXPercentOffer_TC02() throws Exception {
        // Add a FLATX% offer for segment "p1"
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "FLATX%", 10, segments);
        boolean offerAdded = addOffer(offerRequest);
        Assert.assertTrue(offerAdded);

        String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1}";
        String response = sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response, Map.class);
        int updatedCartValue = (Integer) map.get("cart_value");
        // 10% of 200 is 20; so 200 - 20 = 180
        Assert.assertEquals(180, updatedCartValue);
    }

    // TC03: Offer not applied when no offer exists
    @Test
    public void testOfferNotAppliedWhenNoOfferExists_TC03() throws Exception {
        // Use a restaurant_id that does not have any offers (e.g., 9999)
        String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":9999}";
        String response = sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response, Map.class);
        int updatedCartValue = (Integer) map.get("cart_value");
        // Expect no discount to be applied; cart value remains 200.
        Assert.assertEquals(200, updatedCartValue);
    }

    // TC04: Offer not applied for incorrect segment
    @Test
    public void testOfferNotAppliedForIncorrectSegment_TC04() throws Exception {
        // Add an offer for segment "p1"
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "FLATX", 10, segments);
        boolean offerAdded = addOffer(offerRequest);
        Assert.assertTrue(offerAdded);

        // Simulate a user (user_id=2) that belongs to a different segment (e.g., "p2")
        String jsonPayload = "{\"cart_value\":200,\"user_id\":2,\"restaurant_id\":1}";
        String response = sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response, Map.class);
        int updatedCartValue = (Integer) map.get("cart_value");
        // Expect no discount since the offer is not applicable for this segment.
        Assert.assertEquals(200, updatedCartValue);
    }

    // TC05: Invalid offer type
    @Test
    public void testInvalidOfferType_TC05() throws Exception {
        // Add an offer with an invalid offer type
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "INVALID", 10, segments);
        boolean offerAdded = addOffer(offerRequest);
        // Depending on your implementation, the offer might be ignored.
        String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1}";
        String response = sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response, Map.class);
        int updatedCartValue = (Integer) map.get("cart_value");
        // Expect cart value to remain unchanged.
        Assert.assertEquals(200, updatedCartValue);
    }

    // TC06: Offer exceeding cart value
    @Test
    public void testOfferExceedingCartValue_TC06() throws Exception {
        // Add an offer where the discount (250) exceeds the cart value (200)
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "FLATX", 250, segments);
        boolean offerAdded = addOffer(offerRequest);
        Assert.assertTrue(offerAdded);

        String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1}";
        String response = sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response, Map.class);
        int updatedCartValue = (Integer) map.get("cart_value");
        // Ensure the final cart value is not negative.
        Assert.assertTrue(updatedCartValue >= 0);
    }

    // TC07: Multiple offers for a segment (best offer should be applied)
    @Test
    public void testMultipleOffersForSegment_TC07() throws Exception {
        // Add two offers for the same restaurant and segment "p1"
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offer1 = new OfferRequest(1, "FLATX", 10, segments);
        OfferRequest offer2 = new OfferRequest(1, "FLATX%", 10, segments);
        boolean added1 = addOffer(offer1);
        boolean added2 = addOffer(offer2);
        Assert.assertTrue(added1 && added2);

        String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1}";
        String response = sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response, Map.class);
        int updatedCartValue = (Integer) map.get("cart_value");
        // For example, if FLATX% (10% off => discount of 20) is better than FLATX (discount of 10)
        Assert.assertEquals(180, updatedCartValue);
    }

    // TC08: Offer applied for different cart values
    @Test
    public void testOfferAppliedForDifferentCartValues_TC08() throws Exception {
        // Add a FLATX offer for segment "p1"
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "FLATX", 10, segments);
        boolean offerAdded = addOffer(offerRequest);
        Assert.assertTrue(offerAdded);

        int[] cartValues = {50, 100, 500};
        ObjectMapper mapper = new ObjectMapper();
        for (int cartValue : cartValues) {
            String jsonPayload = "{\"cart_value\":" + cartValue + ",\"user_id\":1,\"restaurant_id\":1}";
            String response = sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);
            Map<String, Object> map = mapper.readValue(response, Map.class);
            int updatedCartValue = (Integer) map.get("cart_value");
            int expected = cartValue - 10;
            if (expected < 0) {
                expected = 0;
            }
            Assert.assertEquals(expected, updatedCartValue);
        }
    }

    // TC09: User segment API returns invalid response
    @Test
    public void testUserSegmentAPIInvalidResponse_TC09() throws Exception {
        // Add an offer for segment "p1"
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "FLATX", 10, segments);
        boolean offerAdded = addOffer(offerRequest);
        Assert.assertTrue(offerAdded);

        // Simulate an invalid user segment response by passing a flag (e.g., simulate_segment_null)
        String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1,\"simulate_segment_null\":true}";
        String response = sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response, Map.class);
        int updatedCartValue = (Integer) map.get("cart_value");
        // Expect no discount if the segment cannot be determined.
        Assert.assertEquals(200, updatedCartValue);
    }

    // TC10: Performance testing with high traffic
    @Test
    public void testPerformanceHighTraffic_TC10() throws Exception {
        int numberOfRequests = 50;
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numberOfRequests; i++) {
            String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1}";
            sendPost("http://localhost:9001/api/v1/cart/apply_offer", jsonPayload);
        }
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("Total time for " + numberOfRequests + " requests: " + totalTime + "ms");
        // Example performance criteria: total time should be less than 3000ms.
        Assert.assertTrue(totalTime < 3000);
    }

    // ---------------------------
    // RBAC & Security Test Cases
    // ---------------------------

    // TC11: Unauthorized user tries to add an offer
    @Test
    public void testUnauthorizedUserAddOffer_TC11() throws Exception {
        String urlString = "http://localhost:9001/api/v1/offer";
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        // Simulate unauthorized access via header
        con.setRequestProperty("user_role", "guest");

        ObjectMapper mapper = new ObjectMapper();
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "FLATX", 10, segments);
        String jsonPayload = mapper.writeValueAsString(offerRequest);
        OutputStream os = con.getOutputStream();
        os.write(jsonPayload.getBytes());
        os.flush();
        os.close();

        int responseCode = con.getResponseCode();
        Assert.assertEquals(403, responseCode);
        con.disconnect();
    }

    // TC12: Admin user successfully adds an offer
    @Test
    public void testAdminUserAddOffer_TC12() throws Exception {
        String urlString = "http://localhost:9001/api/v1/offer";
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        // Simulate admin access via header
        con.setRequestProperty("user_role", "admin");

        ObjectMapper mapper = new ObjectMapper();
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "FLATX", 10, segments);
        String jsonPayload = mapper.writeValueAsString(offerRequest);
        OutputStream os = con.getOutputStream();
        os.write(jsonPayload.getBytes());
        os.flush();
        os.close();

        int responseCode = con.getResponseCode();
        Assert.assertEquals(200, responseCode);
        con.disconnect();
    }

    // TC13: User without permission tries to apply an offer
    @Test
    public void testUserWithoutPermissionApplyOffer_TC13() throws Exception {
        String urlString = "http://localhost:9001/api/v1/cart/apply_offer";
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        // Simulate insufficient permissions
        con.setRequestProperty("user_role", "guest");

        String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1}";
        OutputStream os = con.getOutputStream();
        os.write(jsonPayload.getBytes());
        os.flush();
        os.close();

        int responseCode = con.getResponseCode();
        Assert.assertEquals(403, responseCode);
        con.disconnect();
    }

    // TC14: User with valid permissions applies an offer
    @Test
    public void testUserWithValidPermissionsApplyOffer_TC14() throws Exception {
        String urlString = "http://localhost:9001/api/v1/cart/apply_offer";
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        // Simulate a valid customer role
        con.setRequestProperty("user_role", "customer");

        String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1}";
        OutputStream os = con.getOutputStream();
        os.write(jsonPayload.getBytes());
        os.flush();
        os.close();

        int responseCode = con.getResponseCode();
        Assert.assertEquals(200, responseCode);

        // Read and verify the response (expecting offer to be applied, e.g., cart_value becomes 190)
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response.toString(), Map.class);
        int updatedCartValue = (Integer) map.get("cart_value");
        Assert.assertEquals(190, updatedCartValue);
        con.disconnect();
    }

    // TC15: Rate Limiting Check
    @Test
    public void testRateLimiting_TC15() throws Exception {
        int numberOfRequests = 20;
        int rateLimitedCount = 0;
        for (int i = 0; i < numberOfRequests; i++) {
            HttpURLConnection con = createConnection("http://localhost:9001/api/v1/cart/apply_offer");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            String jsonPayload = "{\"cart_value\":200,\"user_id\":1,\"restaurant_id\":1}";
            OutputStream os = con.getOutputStream();
            os.write(jsonPayload.getBytes());
            os.flush();
            os.close();
            int responseCode = con.getResponseCode();
            if (responseCode == 429) { // HTTP 429 Too Many Requests
                rateLimitedCount++;
            }
            con.disconnect();
        }
        Assert.assertTrue("API should enforce rate limiting", rateLimitedCount > 0);
    }

    // TC16: Accessing Offers of Another User
    @Test
    public void testAccessingOffersOfAnotherUser_TC16() throws Exception {
        // Assume that GET /api/v1/offer/{userId} returns offers for that user.
        // Here, user 1 tries to access offers of user 2.
        String urlString = "http://localhost:9001/api/v1/offer/2";
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("user_id", "1"); // current user is 1
        int responseCode = con.getResponseCode();
        Assert.assertEquals(403, responseCode);
        con.disconnect();
    }

    // TC17: Unauthorized API Access
    @Test
    public void testUnauthorizedAPIAccess_TC17() throws Exception {
        String urlString = "http://localhost:9001/api/v1/offer";
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        // Simulate an unauthorized user via header
        con.setRequestProperty("user_role", "unauthorized");

        ObjectMapper mapper = new ObjectMapper();
        List<String> segments = new ArrayList<>();
        segments.add("p1");
        OfferRequest offerRequest = new OfferRequest(1, "FLATX", 10, segments);
        String jsonPayload = mapper.writeValueAsString(offerRequest);
        OutputStream os = con.getOutputStream();
        os.write(jsonPayload.getBytes());
        os.flush();
        os.close();

        int responseCode = con.getResponseCode();
        Assert.assertEquals(401, responseCode);
        con.disconnect();
    }
}
