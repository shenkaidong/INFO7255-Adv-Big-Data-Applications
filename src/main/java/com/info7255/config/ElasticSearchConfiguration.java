package com.info7255.config;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class ElasticSearchConfiguration {
    private org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RestHighLevelClient ecClient;

    //Rest Client为推荐使用，TransportClient将在未来版本中废弃。
    @Bean
    public RestHighLevelClient ecClient() {
        // HttpClient用户身份验证 CredentialsProvider
    	final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    	/*credentialsProvider.setCredentials(AuthScope.ANY,
    	        new UsernamePasswordCredentials("elastic", "UW888O4ON2ZAxzuJZFIY7XML"));*/
//                new UsernamePasswordCredentials("elastic", "123456"));

//    	RestClientBuilder builder = RestClient.builder(new HttpHost("b32c035a1b6f4bada420903f5849d9bc.us-east-1.aws.found.io", 9243,"https"))
//        RestClientBuilder builder = RestClient.builder(new HttpHost("192.168.84.129", 9300,"https"))
        RestClientBuilder builder = RestClient.builder(new HttpHost("localhost", 9200,"http"))
    	        .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
    	            @Override
    	            public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
    	                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    	            }
    	        });

    	
    	RestHighLevelClient client = new RestHighLevelClient(builder);
        return client;
    }
/*
    @EventListener(ApplicationReadyEvent.class)
    public void initForIndexJoin() {
        try {

            String jsonString = "{\n" +
                    "  \"mappings\": {\n" +
                    "    \"_doc\": {\n" +
                    "      \"properties\": {\n" +
                    "        \"join_field\": { \n" +
                    "          \"type\": \"join\",\n" +
                    "          \"relations\": {\n" +
                    "            \"plan\": [\"membercostshare\"],\n" +
                    "            \"planservice\": \"service\"\n" +
                    "          }\n" +
                    "        },\n" +
                    "        \"creationDate\": {\n" +
                    "          \"type\":   \"date\",\n" +
                    "          \"format\": \"MM-dd-yyyy\"\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            String mapping=new String ( Files.readAllBytes( Paths.get("./src/main/resources/mappingTest.json")));
            Map<String, String> params = Collections.singletonMap("pretty", "true");
            HttpEntity entity = new NStringEntity(mapping, ContentType.APPLICATION_JSON);
            ecClient.getLowLevelClient().performRequest("PUT", "/plan", params, entity);
            logger.info("Init index_join field success.");
        } catch (Exception e) {
            logger.error("Init index_join field failed(acceptable if field already exist), error:"+e);
        }
    }

    //@EventListener(ApplicationReadyEvent.class)
    public void initForIndexAll() {
        try {

             String jsonString = "{\n" +
                    "  \"mappings\": {\n" +
                    "    \"plan\": {\n" +
                    "      \"properties\": {\n" +
                    "        \"creationDate\": {\n" +
                    "          \"type\":   \"date\",\n" +
                    "          \"format\": \"MM-dd-yyyy\"\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            String mapping=new String ( Files.readAllBytes( Paths.get("./src/main/resources/mapping.json")));
            Map<String, String> params = Collections.singletonMap("pretty", "true");
            HttpEntity entity = new NStringEntity(mapping, ContentType.APPLICATION_JSON);
            ecClient.getLowLevelClient().performRequest("PUT", "/insurance", params, entity);
            logger.info("Init index_all field success.");
        } catch (Exception e) {
            logger.error("Init index_all date field failed(acceptable if field already exist), error:"+e);
        }
    }
*/
}
