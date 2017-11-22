package onl.identitas.krakbot;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SampleTasks {

    private static final Logger LOG = LogManager.getLogger();

    @Value("${krakbot.userid}")
    private String userId;
    @Value("${krakbot.apiKey}")
    private String apiKey;
    @Value("${krakbot.apiSecret}")
    private String apiSecret;

    private Mac macObject;

    @Value("${krakbot.uriTemplate}")
    private String uriTemplateStr;
    private UriTemplate uriTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @PostConstruct
    public void init() throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {

        SecretKeySpec signingKey = new SecretKeySpec(apiSecret.getBytes("UTF-8"), "HmacSHA256");

        macObject = Mac.getInstance("HmacSHA256");
        macObject.init(signingKey);

        uriTemplate = new UriTemplate(uriTemplateStr);
    }

    @Scheduled(fixedRate = 30000)
    public void obtainBalance() throws UnsupportedEncodingException, JsonProcessingException {

        long nonce = Instant.now().toEpochMilli();

        LOG.info("nonce = {}", nonce);

        String msg = nonce + userId + apiKey;

        LOG.info("msg = {}", msg);

        byte[] rawMac = macObject.doFinal(msg.getBytes("UTF-8"));

        String strMac = DatatypeConverter.printHexBinary(rawMac);

        LOG.info("strMac = {}", strMac);

        HashMap<String, Object> body = new HashMap<String, Object>(3);
        body.put("signature", strMac);
        body.put("nonce", nonce);
        body.put("key", apiKey);

        String bodyStr = new ObjectMapper().writeValueAsString(body);

        LOG.info(bodyStr);

        try {
            RequestEntity<Map<String, Object>> post = RequestEntity.post(uriTemplate.expand("balance"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, "krakbot")
                    .body(body);

            LOG.info(post);

            ResponseEntity<String> response = restTemplate.exchange(post, String.class);

            LOG.info(response);

        } catch (HttpStatusCodeException e) {
            LOG.error(e::getResponseBodyAsString);
        }
    }

}
