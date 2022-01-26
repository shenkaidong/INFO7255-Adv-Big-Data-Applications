package com.info7255.controller;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import io.jsonwebtoken.SignatureAlgorithm;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.everit.json.schema.Schema;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.info7255.beans.EtagManager;
import com.info7255.beans.JSONValidator;
import com.info7255.beans.JedisBean;

@RestController
public class HomeController {

	@Autowired
	private JSONValidator validator;
	@Autowired
	private JedisBean jedisBean;

	@Autowired
	private EtagManager etagManager;
	@Autowired
	private RestHighLevelClient restHighLevelClient;
	//队列
	private static final Queue queue =new ConcurrentLinkedQueue();

	private String key = "ssdkF$HUy2A#D%kd";
	private String algorithm = "AES";

	Map<String, Object> m = new HashMap<String, Object>();

	@RequestMapping("/")
	public String home() {
		return "Welcome!";
	}

	@GetMapping(value = "/plan/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> read(@PathVariable(name = "id", required = true) String id,
			@RequestHeader HttpHeaders requestHeaders) {
		m.clear();
		if (!authorize(requestHeaders)) {
			m.put("message", "Authorization failed");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.UNAUTHORIZED);
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		JSONObject jsonString = jedisBean.read(id);
		if (jsonString != null) {
			String etag = etagManager.getETag(jsonString);
			if (!etagManager.verifyETag(jsonString, requestHeaders.getIfNoneMatch())) {
				headers.setETag(etag);
				return new ResponseEntity<Map<String, Object>>(jsonString.toMap(), headers, HttpStatus.OK);
			} else {
				headers.setETag(etag);
				return new ResponseEntity<Map<String, Object>>(m, headers, HttpStatus.NOT_MODIFIED);
			}
		} else {
			m.put("message", "Read unsuccessful. Invalid Id.");
			return new ResponseEntity<>(m, headers, HttpStatus.NOT_FOUND);
		}

	}

	@PostMapping(value = "/plan", produces = MediaType.APPLICATION_JSON_VALUE, consumes = "application/json")
	public ResponseEntity<Map<String, Object>> insert(@RequestBody(required = true) String body,
			@RequestHeader HttpHeaders requestHeaders) throws IOException {
		m.clear();
		if (!authorize(requestHeaders)) {
			m.put("message", "Authorization failed");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.UNAUTHORIZED);
		}

		Schema schema = validator.getSchema();
		if (schema == null) {
			m.put("message", "No Schema found");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.NOT_FOUND);
		}
		
		JSONObject jsonObject = validator.getJsonObjectFromString(body);

		if (validator.validate(jsonObject)) {
			String uuid = jedisBean.insert(jsonObject);
			String etag=etagManager.getETag(jsonObject);
			conversionESJson(jsonObject);//内含队列
			pullES();//添加到ES
			HttpHeaders responseHeader=new HttpHeaders();
			responseHeader.setETag(etag);
			m.put("message", "Added successfully");
			m.put("id", uuid);
			return new ResponseEntity<Map<String, Object>>(m,responseHeader, HttpStatus.CREATED);
		} else {
			m.put("message", "Validation failed");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.BAD_REQUEST);
		}
	}

	@DeleteMapping(value = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> delete(@RequestBody(required = true) String body,
			@RequestHeader HttpHeaders requestHeaders) {
		m.clear();
		if (!authorize(requestHeaders)) {
			m.put("message", "Authorization failed");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.UNAUTHORIZED);
		}
		if (jedisBean.delete(body)) {
			m.put("message", "Deleted successfully");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.ACCEPTED);
		} else {
			m.put("message", "Delete failed");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.BAD_REQUEST);
		}
	}

	@RequestMapping(method= RequestMethod.PATCH,value = "/plan/{planID}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = "application/json")
	public ResponseEntity<Map<String, Object>> update(@PathVariable(name = "planID", required = true) String planID,@RequestBody(required = true) String body,
			@RequestHeader HttpHeaders requestHeaders) throws IOException {
		m.clear();
		if (!authorize(requestHeaders)) {
			m.put("message", "Authorization failed");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.UNAUTHORIZED);
		}
		Schema schema = validator.getSchema();
		if (schema == null) {	
			m.put("message", "No schema found!");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.NOT_FOUND);
		}
		
//		System.out.println("landmark 1");
		JSONObject jsonObject = validator.getJsonObjectFromString(body);

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);
		
		JSONObject planJSON=jedisBean.read(planID);
		if(planJSON!=null)
		{
//			System.out.println("landmark 2");
			System.out.println(planJSON);
			String etag = etagManager.getETag(planJSON);
			System.out.println("etag");
			System.out.println(etag);
			if (etagManager.verifyETag(planJSON, requestHeaders.getIfMatch())) {
				String newETag=jedisBean.patch(jsonObject,planID);
				conversionESJson(jsonObject);//内含队列
				pullES();//添加到ES
//				System.out.println("landmark 3");
				if (newETag==null) {
//					System.out.println("landmark 4");
					m.put("message", "Update failed");
					return new ResponseEntity<Map<String, Object>>(m, HttpStatus.BAD_REQUEST);
				}
				//String newETag=etagManager.getETag(jedisBean.read(planID));
//				System.out.println("landmark 5");
				responseHeaders.setETag(newETag);
				m.put("message", "Updated successfully");
				return new ResponseEntity<Map<String, Object>>(m, responseHeaders, HttpStatus.ACCEPTED);
			} else {
//				System.out.println("landmark 6");
				if(requestHeaders.getIfMatch().isEmpty()) {
//					System.out.println("landmark 7");
					m.put("message","If-Match ETag required");
					return new ResponseEntity<Map<String, Object>>(m, responseHeaders, HttpStatus.PRECONDITION_REQUIRED);
				}
				else {
//					System.out.println("landmark 8");
					responseHeaders.setETag(etag);
					return new ResponseEntity<Map<String, Object>>(m, responseHeaders, HttpStatus.PRECONDITION_FAILED);
			
				}
			}
		}
		else {

		m.put("message", "Invalid Plan Id");
		return new ResponseEntity<Map<String, Object>>(m, HttpStatus.BAD_REQUEST);
		}
	}
	@RequestMapping(method= RequestMethod.PUT,value = "/plan/{planID}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = "application/json")
	public ResponseEntity<Map<String, Object>> update1(@PathVariable(name = "planID", required = true) String planID,@RequestBody(required = true) String body,
			@RequestHeader HttpHeaders requestHeaders) throws IOException {
		m.clear();
		if (!authorize(requestHeaders)) {
			m.put("message", "Authorization failed");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.UNAUTHORIZED);
		}

		Schema schema = validator.getSchema();
		if (schema == null) {	
			m.put("message", "No schema found!");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.NOT_FOUND);
		}
		
		
		JSONObject jsonObject = validator.getJsonObjectFromString(body);

		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(MediaType.APPLICATION_JSON);
		
		JSONObject planJSON=jedisBean.read(planID);
		if(planJSON!=null)
		{
			String etag = etagManager.getETag(planJSON);
			if (etagManager.verifyETag(planJSON, requestHeaders.getIfMatch())) {
			
				if (!jedisBean.replace(jsonObject)) {
					m.put("message", "Update failed");
					return new ResponseEntity<Map<String, Object>>(m, HttpStatus.BAD_REQUEST);
				}
				conversionESJson(jsonObject);//内含队列
				pullES();//添加到ES
				String newETag=etagManager.getETag(jedisBean.read(planID));
				responseHeaders.setETag(newETag);
				m.put("message", "Update successfully!");
				return new ResponseEntity<Map<String, Object>>(m, responseHeaders, HttpStatus.ACCEPTED);
///				return new ResponseEntity<Map<String, Object>>(m, responseHeaders, HttpStatus.NO_CONTENT);
			} else {
				if(requestHeaders.getIfMatch().isEmpty()) {
					m.put("message","If-Match ETag required");
					return new ResponseEntity<Map<String, Object>>(m, responseHeaders, HttpStatus.PRECONDITION_REQUIRED);
				}
				else {
					responseHeaders.setETag(etag);
					return new ResponseEntity<Map<String, Object>>(m, responseHeaders, HttpStatus.PRECONDITION_FAILED);
			
				}
			}
		}
		else {

		m.put("message", "Invalid Plan Id");
		return new ResponseEntity<Map<String, Object>>(m, HttpStatus.BAD_REQUEST);
		}
	}
	// 获取token
	@GetMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> createToken() {
		m.clear();
		// 新建Json格式的jsonToken
		JSONObject jsonToken = new JSONObject();
//		jsonToken.put("Issuer", "HuilinCui");
		jsonToken.put("Issuer", "KaidongShen");

		TimeZone tz = TimeZone.getTimeZone("UTC");
		// yyyy-MM-dd'T'HH:mm'Z'
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
		df.setTimeZone(tz);

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(Calendar.MINUTE, 60);
		Date date = calendar.getTime();

		jsonToken.put("expiry", df.format(date));
		String token = jsonToken.toString();
		System.out.println(token);

		SecretKey spec = loadKey();

		try {
			Cipher c = Cipher.getInstance(algorithm);
			c.init(Cipher.ENCRYPT_MODE, spec);
			byte[] encrBytes = c.doFinal(token.getBytes());
			String encoded = Base64.getEncoder().encodeToString(encrBytes);
			m.put("token", encoded);
			m.put("expiry", df.format(date));
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.ACCEPTED);

		} catch (Exception e) {
			e.printStackTrace();
			m.put("message", "Token creation failed. Please try again.");
			return new ResponseEntity<Map<String, Object>>(m, HttpStatus.UNAUTHORIZED);
		}
		

	}

	// Oauth2.0
	public boolean authorize(HttpHeaders headers) {

		if (headers.getFirst("Authorization") == null)
			return false;

		String token = headers.getFirst("Authorization").substring(7);
		byte[] decrToken = Base64.getDecoder().decode(token);
		SecretKey spec = loadKey();
		try {
			Cipher c = Cipher.getInstance(algorithm);
			c.init(Cipher.DECRYPT_MODE, spec);
			String tokenString = new String(c.doFinal(decrToken));
			JSONObject jsonToken = new JSONObject(tokenString);
			
			String ttldateAsString = jsonToken.get("expiry").toString();
			Date currentDate = Calendar.getInstance().getTime();

			TimeZone tz = TimeZone.getTimeZone("UTC");
			DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
			formatter.setTimeZone(tz);

			Date ttlDate = formatter.parse(ttldateAsString);
			currentDate = formatter.parse(formatter.format(currentDate));

			if (currentDate.after(ttlDate)) {
				return false;
			}

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	private SecretKey loadKey() {
		return new SecretKeySpec(key.getBytes(), algorithm);
	}

	@PostMapping("/test")
	public void test(@RequestBody String body) throws IOException {
		JSONObject jsonObject = new JSONObject(body);
		conversionESJson(jsonObject);
		pullES();
	}


	/**
	 * 解析为ES父子文档格式
	 */
	private JSONObject conversionESJson(JSONObject jsonObject){
		JSONObject json;
		String parentId = (String) jsonObject.get("objectId");
		JSONObject planCostShares = jsonObject.getJSONObject("planCostShares");
		JSONArray linkedPlanServices = jsonObject.getJSONArray("linkedPlanServices");
		Iterator<Object> iterator = linkedPlanServices.iterator();
		while (iterator.hasNext()){
			JSONObject linkedPlanService = (JSONObject) iterator.next();
			String objectId = (String)linkedPlanService.get("objectId");

			JSONObject linkedService = linkedPlanService.getJSONObject("linkedService");
			JSONObject planserviceCostShares = linkedPlanService.getJSONObject("planserviceCostShares");
			json = addParentNode(linkedService, objectId, "linkedService");
			queue.offer(json);
			json = addParentNode(planserviceCostShares, objectId, "planserviceCostShares");
			queue.offer(json);
			linkedPlanService.remove("linkedService");
			linkedPlanService.remove("planserviceCostShares");
			json = addParentNode(linkedPlanService, parentId, "linkedPlanServices");
			queue.offer(json);
		}
		json = addParentNode(planCostShares, parentId, "planCostShares");
		queue.offer(json);
		jsonObject.remove("planCostShares");
		jsonObject.remove("linkedPlanServices");
		jsonObject.put("relation","plan");
		queue.offer(jsonObject);
		return json;
	}

	/**
	 * 提交到ES
	 */
	private void pullES() throws IOException {
		while (!queue.isEmpty()){
			JSONObject poll = (JSONObject) queue.poll();//弹出
			Object objectId = poll.get("objectId");
			JSONObject relation;
			Object parent=null;
			try {
				relation = poll.getJSONObject("relation");
				parent = relation.get("parent");
			}catch (JSONException e){
				System.err.println("父类!");
			}
			System.out.println(objectId+"    "+parent);
			IndexRequest indexRequest = new IndexRequest("plan","_doc", (String) objectId);
			if (parent!=null){
				indexRequest.routing((String) parent);
			}
			indexRequest.source(poll.toMap());
			restHighLevelClient.index(indexRequest);
		}
	}
	/**
	 * 添加父关系
	 */
	private JSONObject addParentNode(JSONObject jsonObject,String parentId,String name){
		JSONObject relation = new JSONObject();
		relation.put("name",name);
		relation.put("parent",parentId);
		jsonObject.put("relation",relation);
		return jsonObject;
	}
}
