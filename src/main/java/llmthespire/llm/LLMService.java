package llmthespire.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import llmthespire.LLMConfig;
import llmthespire.game.GameState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for communicating with Language Models
 */
public class LLMService {
    private static final Logger logger = LogManager.getLogger(LLMService.class.getName());
    
    // Singleton instance
    private static volatile LLMService instance;
    
    // Thread pool for async operations
    private final ExecutorService executor;
    
    // Conversation logger
    private ConversationLogger conversationLogger;
    
    // Cache to minimize API calls
    private final long CACHE_TIMEOUT_MS = 500;
    private String lastGameState = "";
    private String lastResponse = "";
    private long lastRequestTime = 0;
    
    /**
     * Private constructor for singleton
     */
    private LLMService() {
        // Create a thread pool with daemon threads to avoid blocking game shutdown
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setDaemon(true);
            return thread;
        });
        
        conversationLogger = new ConversationLogger();
    }
    
    /**
     * Get the singleton instance
     */
    public static LLMService getInstance() {
        if (instance == null) {
            synchronized (LLMService.class) {
                if (instance == null) {
                    instance = new LLMService();
                }
            }
        }
        return instance;
    }
    
    /**
     * Ask the LLM for the next action given the current game state
     * @param gameState The current game state
     * @return A CompletableFuture that will resolve to the action to take
     */
    public CompletableFuture<String> requestAction(GameState gameState) {
        LLMConfig config = LLMConfig.getInstance();
        
        // 獲取當前活躍的API配置
        LLMConfig.ApiConfig activeConfig = config.getActiveApiConfig();
        
        logger.info("LLM request initiated with API type: " + activeConfig.getApiType() + 
                    ", model: " + activeConfig.getModel() + 
                    ", name: " + config.getActiveApiName());
        
        // Check if LLM is enabled - always true due to the overridden method in LLMConfig
        if (!config.isEnabled()) {
            logger.warn("LLM is disabled, no action will be taken");
            return CompletableFuture.completedFuture("");
        }
        
        // Convert game state to JSON
        String gameStateJson = gameState.toString();
        
        // 移除缓存检查，始终发送新请求
        // 记录请求状态
        lastGameState = gameStateJson;
        lastRequestTime = System.currentTimeMillis();
        
        // Async request to the LLM
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Preparing to send request to " + activeConfig.getApiType() + " API");
                
                // Build user prompt from game state with formatting instructions
                String userPrompt = buildPrompt(gameState);
                
                logger.debug("User prompt length: " + userPrompt.length() + " characters");
                
                // Call appropriate API based on config
                String response = "";
                logger.info("Calling " + activeConfig.getApiType() + " API...");
                try {
                    switch (activeConfig.getApiType()) {
                        case OPENAI:
                            response = callOpenAI(activeConfig, config.getSystemPrompt(), userPrompt);
                            break;
                        case ANTHROPIC:
                            response = callAnthropic(activeConfig, config.getSystemPrompt(), userPrompt);
                            break;
                        case DEEPSEEK:
                            response = callDeepSeek(activeConfig, config.getSystemPrompt(), userPrompt);
                            break;
                        default:
                            logger.info("Using default OpenAI API");
                            response = callOpenAI(activeConfig, config.getSystemPrompt(), userPrompt);
                            break;
                    }
                    logger.info("Successfully received response from " + activeConfig.getApiType() + " API");
                } catch (Exception e) {
                    logger.error("API call failed: " + e.getMessage(), e);
                    throw new RuntimeException("Failed to get response from " + activeConfig.getApiType() + ": " + e.getMessage(), e);
                }
                
                // Clean up response and update cache
                logger.debug("Raw response: " + response);
                String cleanedResponse = cleanResponse(response);
                logger.debug("Cleaned response: " + cleanedResponse);
                String actionOnly = extractActionFromResponse(cleanedResponse);
                logger.info("Extracted action: " + actionOnly);
                lastResponse = actionOnly;
                
                return cleanedResponse;
            } catch (Exception e) {
                logger.error("Error requesting action from LLM: " + e.getMessage(), e);
                return "ERROR: " + e.getMessage();
            }
        }, executor);
    }
    
    /**
     * Clean and validate the LLM response
     */
    private String cleanResponse(String response) {
        if (response == null || response.isEmpty()) {
            return "";
        }
        
        // Remove any extra whitespace at the beginning and end
        String cleaned = response.trim();
        
        // Handle markdown code block formatting while preserving internal structure
        if (cleaned.startsWith("```") && cleaned.endsWith("```")) {
            cleaned = cleaned.substring(3);
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }
        
        // Remove any language identifier after opening backticks
        int newlineIndex = cleaned.indexOf('\n');
        if (newlineIndex > 0 && !cleaned.substring(0, newlineIndex).contains(":")) {
            cleaned = cleaned.substring(newlineIndex + 1).trim();
        }
        
        return cleaned;
    }
    
    /**
     * Extract just the action from the formatted response
     */
    private String extractActionFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return "";
        }
        
        // Look for the ACTION: line
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("ACTION:")) {
                // Extract just the action command
                String action = line.substring("ACTION:".length()).trim();
                logger.info("Extracted action from response: " + action);
                return action;
            }
        }
        
        // If we can't find a properly formatted action line, try to use the whole response
        logger.warn("Could not find ACTION: line in response, using full response");
        return response;
    }
    
    /**
     * Call the OpenAI API
     */
    private String callOpenAI(LLMConfig.ApiConfig config, String systemPrompt, String userPrompt) throws IOException {
        logger.info("Preparing OpenAI API call to endpoint: " + config.getApiEndpoint());
        
        // 检查API密钥是否已设置
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new IOException("API key is not set or empty. Please set a valid API key in the configuration.");
        }
        
        URL url = new URL(config.getApiEndpoint());
        HttpURLConnection connection = null;
        
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000); // 30秒连接超时
            connection.setReadTimeout(60000);    // 60秒读取超时
            
            // Prepare request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", config.getModel());
            requestBody.addProperty("max_tokens", config.getMaxTokens());
            requestBody.addProperty("temperature", config.getTemperature());
            
            JsonArray messages = new JsonArray();
            
            // System prompt
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);
            
            // User prompt with game state
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", userPrompt);
            messages.add(userMessage);
            
            requestBody.add("messages", messages);
            
            String requestBodyString = requestBody.toString();
            logger.debug("OpenAI request payload: " + requestBodyString);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBodyString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Log the conversation
            conversationLogger.logRequest("OpenAI", systemPrompt, userPrompt);
            
            // Process response
            int responseCode = connection.getResponseCode();
            logger.info("OpenAI API response code: " + responseCode);
            
            if (responseCode == 200) {
                // 成功响应
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    String responseString = response.toString();
                    logger.debug("OpenAI raw response: " + responseString);
                    
                    // Parse JSON and extract content
                    JsonObject jsonResponse = new Gson().fromJson(responseString, JsonObject.class);
                    JsonArray choices = jsonResponse.getAsJsonArray("choices");
                    if (choices != null && choices.size() > 0) {
                        JsonObject choice = choices.get(0).getAsJsonObject();
                        JsonObject message = choice.getAsJsonObject("message");
                        String content = message.get("content").getAsString();
                        return content;
                    } else {
                        logger.error("OpenAI response did not contain any choices");
                        throw new IOException("OpenAI response did not contain any choices");
                    }
                }
            } else {
                // 错误响应，尝试读取错误信息
                String errorBody = "";
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    errorBody = response.toString();
                } catch (Exception e) {
                    logger.error("Error reading error stream", e);
                }
                
                logger.error("OpenAI API error (code " + responseCode + "): " + errorBody);
                throw new IOException("OpenAI API error code: " + responseCode + ", details: " + errorBody);
            }
        } catch (IOException e) {
            logger.error("OpenAI API call failed: " + e.getMessage(), e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Call the Anthropic API
     */
    private String callAnthropic(LLMConfig.ApiConfig config, String systemPrompt, String userPrompt) throws IOException {
        URL url = new URL(config.getApiEndpoint());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("x-api-key", config.getApiKey());
        connection.setRequestProperty("anthropic-version", "2023-06-01");
        connection.setDoOutput(true);
        
        // Prepare request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());
        requestBody.addProperty("temperature", config.getTemperature());
        requestBody.addProperty("system", systemPrompt);
        
        JsonArray messages = new JsonArray();
        
        // User message
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);
        
        requestBody.add("messages", messages);
        
        // Send request
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // Log the conversation
        conversationLogger.logRequest("Anthropic", systemPrompt, userPrompt);
        
        // Process response
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8.name())) {
                String responseBody = scanner.useDelimiter("\\A").next();
                
                // Parse JSON response
                Gson gson = new Gson();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonObject content = jsonResponse.getAsJsonObject("content");
                
                if (content != null) {
                    String text = content.get("text").getAsString();
                    
                    // Log the conversation
                    conversationLogger.logResponse(text);
                    
                    return text;
                }
            }
        } else {
            // Handle error
            String errorBody = "";
            try (Scanner scanner = new Scanner(connection.getErrorStream(), StandardCharsets.UTF_8.name())) {
                errorBody = scanner.useDelimiter("\\A").next();
            }
            
            // Log error
            conversationLogger.logError("Anthropic API Error: " + responseCode + "\n" + errorBody);
            
            throw new IOException("Anthropic API error: " + responseCode + " - " + errorBody);
        }
        
        return "";
    }
    
    /**
     * Call the DeepSeek API
     */
    private String callDeepSeek(LLMConfig.ApiConfig config, String systemPrompt, String userPrompt) throws IOException {
        logger.info("Preparing DeepSeek API call to endpoint: " + config.getApiEndpoint());
        
        // Check if API key is set
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new IOException("API key is not set or empty. Please set a valid DeepSeek API key in the configuration.");
        }
        
        URL url = new URL(config.getApiEndpoint());
        HttpURLConnection connection = null;
        
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000); // 30-second connection timeout
            connection.setReadTimeout(60000);    // 60-second read timeout
            
            // Prepare request body (similar to OpenAI format)
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", config.getModel());
            requestBody.addProperty("max_tokens", config.getMaxTokens());
            requestBody.addProperty("temperature", config.getTemperature());
            
            JsonArray messages = new JsonArray();
            
            // System prompt
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);
            
            // User prompt with game state
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", userPrompt);
            messages.add(userMessage);
            
            requestBody.add("messages", messages);
            
            String requestBodyString = requestBody.toString();
            logger.debug("DeepSeek request payload: " + requestBodyString);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBodyString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Log the conversation
            conversationLogger.logRequest("DeepSeek", systemPrompt, userPrompt);
            
            // Process response
            int responseCode = connection.getResponseCode();
            logger.info("DeepSeek API response code: " + responseCode);
            
            if (responseCode == 200) {
                // Success response
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    String responseString = response.toString();
                    logger.debug("DeepSeek raw response: " + responseString);
                    
                    // Parse JSON and extract content (same format as OpenAI)
                    JsonObject jsonResponse = new Gson().fromJson(responseString, JsonObject.class);
                    JsonArray choices = jsonResponse.getAsJsonArray("choices");
                    if (choices != null && choices.size() > 0) {
                        JsonObject choice = choices.get(0).getAsJsonObject();
                        JsonObject message = choice.getAsJsonObject("message");
                        String content = message.get("content").getAsString();
                        
                        // Log the conversation response
                        conversationLogger.logResponse(content);
                        
                        return content;
                    } else {
                        logger.error("DeepSeek response did not contain any choices");
                        throw new IOException("DeepSeek response did not contain any choices");
                    }
                }
            } else {
                // Error response
                String errorBody = "";
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    errorBody = response.toString();
                } catch (Exception e) {
                    logger.error("Error reading error stream", e);
                }
                
                logger.error("DeepSeek API error (code " + responseCode + "): " + errorBody);
                throw new IOException("DeepSeek API error code: " + responseCode + ", details: " + errorBody);
            }
        } catch (IOException e) {
            logger.error("DeepSeek API call failed: " + e.getMessage(), e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Call the Google API
     */
    private String callGoogle(LLMConfig.ApiConfig config, String systemPrompt, String userPrompt) throws IOException {
        URL url = new URL(config.getApiEndpoint() + "/" + config.getModel() + ":generateContent?key=" + config.getApiKey());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        
        // Prepare request body
        JsonObject requestBody = new JsonObject();
        
        JsonArray contents = new JsonArray();
        
        // Add system prompt as a part
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("role", "system");
        systemPart.addProperty("parts", systemPrompt);
        contents.add(systemPart);
        
        // Add user prompt
        JsonObject userPart = new JsonObject();
        userPart.addProperty("role", "user");
        userPart.addProperty("parts", userPrompt);
        contents.add(userPart);
        
        requestBody.add("contents", contents);
        
        // Add generation config
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", config.getTemperature());
        generationConfig.addProperty("maxOutputTokens", config.getMaxTokens());
        requestBody.add("generationConfig", generationConfig);
        
        // Send request
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // Log the conversation
        conversationLogger.logRequest("Google", systemPrompt, userPrompt);
        
        // Process response
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8.name())) {
                String responseBody = scanner.useDelimiter("\\A").next();
                
                // Parse JSON response (format depends on the Google API)
                Gson gson = new Gson();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                
                // Extract the text from the response (adjust this based on the actual response format)
                JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
                if (candidates != null && candidates.size() > 0) {
                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                    JsonObject content = candidate.getAsJsonObject("content");
                    JsonArray parts = content.getAsJsonArray("parts");
                    
                    if (parts != null && parts.size() > 0) {
                        String text = parts.get(0).getAsJsonObject().get("text").getAsString();
                        
                        // Log the conversation
                        conversationLogger.logResponse(text);
                        
                        return text;
                    }
                }
            }
        } else {
            // Handle error
            String errorBody = "";
            try (Scanner scanner = new Scanner(connection.getErrorStream(), StandardCharsets.UTF_8.name())) {
                errorBody = scanner.useDelimiter("\\A").next();
            }
            
            // Log error
            conversationLogger.logError("Google API Error: " + responseCode + "\n" + errorBody);
            
            throw new IOException("Google API error: " + responseCode + " - " + errorBody);
        }
        
        return "";
    }
    
    /**
     * Call a custom API (using OpenAI-compatible format)
     */
    private String callCustomAPI(LLMConfig.ApiConfig config, String systemPrompt, String userPrompt) throws IOException {
        // Fall back to OpenAI format for custom endpoints
        return callOpenAI(config, systemPrompt, userPrompt);
    }
    
    /**
     * Shut down the executor service
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Class to handle logging conversations to file
     */
    private class ConversationLogger {
        private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        private final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        private String sessionLogFile;
        private LLMConfig config;
        
        private ConversationLogger() {
            config = LLMConfig.getInstance();
            initializeLogFile();
        }
        
        private void initializeLogFile() {
            try {
                if (config.isSaveConversations()) {
                    // Create log directory if it doesn't exist
                    File logDir = new File(config.getConversationDirectory());
                    if (!logDir.exists()) {
                        logDir.mkdirs();
                    }
                    
                    // Create session log file
                    String timestamp = DATE_FORMAT.format(new Date());
                    sessionLogFile = config.getConversationDirectory() + File.separator + 
                                     "llm_conversation_" + timestamp + ".log";
                    
                    // Get active API config
                    LLMConfig.ApiConfig activeConfig = config.getActiveApiConfig();
                    
                    // Write header
                    try (PrintWriter writer = new PrintWriter(new FileWriter(sessionLogFile, true))) {
                        writer.println("=== LLM Conversation Log ===");
                        writer.println("Session started: " + TIMESTAMP_FORMAT.format(new Date()));
                        writer.println("API Configuration: " + config.getActiveApiName());
                        writer.println("LLM: " + activeConfig.getApiType().getDisplayName() + " - " + activeConfig.getModel());
                        writer.println("Temperature: " + activeConfig.getTemperature());
                        writer.println("Max Tokens: " + activeConfig.getMaxTokens());
                        writer.println("=========================\n");
                    }
                    
                    logger.info("Conversation logging enabled, writing to: " + sessionLogFile);
                }
            } catch (Exception e) {
                logger.error("Failed to initialize conversation log: " + e.getMessage(), e);
            }
        }
        
        public void logRequest(String apiType, String systemPrompt, String userPrompt) {
            if (!config.isSaveConversations() || sessionLogFile == null) {
                return;
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(sessionLogFile, true))) {
                writer.println("--- Request: " + TIMESTAMP_FORMAT.format(new Date()) + " ---");
                writer.println("[API: " + apiType + " / " + config.getActiveApiName() + "]");
                writer.println("\n[SYSTEM PROMPT]");
                writer.println(systemPrompt);
                writer.println("\n[USER PROMPT]");
                writer.println(userPrompt);
                writer.println();
            } catch (IOException e) {
                logger.error("Failed to log conversation request: " + e.getMessage(), e);
            }
        }
        
        public void logResponse(String response) {
            if (!config.isSaveConversations() || sessionLogFile == null) {
                return;
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(sessionLogFile, true))) {
                writer.println("[RESPONSE]");
                writer.println(response);
                writer.println("\n----------------------------\n");
            } catch (IOException e) {
                logger.error("Failed to log conversation response: " + e.getMessage(), e);
            }
        }
        
        public void logError(String error) {
            if (!config.isSaveConversations() || sessionLogFile == null) {
                return;
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(sessionLogFile, true))) {
                writer.println("[ERROR]");
                writer.println(error);
                writer.println("\n----------------------------\n");
            } catch (IOException e) {
                logger.error("Failed to log conversation error: " + e.getMessage(), e);
            }
        }
    }
    
    private String buildPrompt(GameState gameState) {
        StringBuilder prompt = new StringBuilder();
        
        // 添加系统提示
        prompt.append("You are an AI playing Slay the Spire. Your goal is to make optimal decisions.\n\n");
        prompt.append("IMPORTANT: You MUST format your response in exactly this way:\n");
        prompt.append("REASON: [Your reasoning for the action]\n");
        prompt.append("ACTION: [The exact one action to take from the available actions]\n\n");
        
        prompt.append("Valid action formats:\n");
        prompt.append("- PLAY_CARD [index] [target_index] or PLAY_CARD index target_index (for cards requiring targets)\n");
        prompt.append("- PLAY_CARD [index] or PLAY_CARD index (for cards not requiring targets)\n");
        prompt.append("- USE_POTION [index] [target_index] or USE_POTION index target_index (for targeted potions)\n");
        prompt.append("- END_TURN\n");
        prompt.append("- CHOOSE_OPTION [index] or CHOOSE_OPTION index (for event options)\n");
        prompt.append("And other actions as shown in 'Available Actions' below.\n\n");
        
        prompt.append("Do not include any other text in your response. Only return one action at a time.\n\n");
        
        // 添加游戏状态信息
        prompt.append("Current Game State:\n");
        prompt.append("Stage: ").append(gameState.stage.getDisplayName()).append("\n");
        
        // 添加玩家信息
        AbstractPlayer player = AbstractDungeon.player;
        if (player != null) {
            prompt.append("\nPlayer Info:\n");
            prompt.append("HP: ").append(player.currentHealth).append("/").append(player.maxHealth).append("\n");
            prompt.append("Energy: ").append(EnergyPanel.totalCount).append("\n");
            prompt.append("Hand Size: ").append(player.hand.size()).append("\n");
            
            // 添加手牌信息
            if (!player.hand.isEmpty()) {
                prompt.append("\nHand:\n");
                for (int i = 0; i < player.hand.size(); i++) {
                    AbstractCard card = player.hand.group.get(i);
                    prompt.append(i).append(": ").append(card.name)
                          .append(" (Cost: ").append(card.costForTurn).append(", Type: ").append(card.type);
                    
                    // 添加卡牌需要目标的信息
                    if (card.target == AbstractCard.CardTarget.ENEMY || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
                        prompt.append(", Requires Target");
                    }
                    
                    prompt.append(")\n");
                }
            }
        }
        
        // 添加怪物信息
        if (AbstractDungeon.getMonsters() != null && !AbstractDungeon.getMonsters().monsters.isEmpty()) {
            prompt.append("\nMonsters:\n");
            for (int i = 0; i < AbstractDungeon.getMonsters().monsters.size(); i++) {
                AbstractMonster monster = AbstractDungeon.getMonsters().monsters.get(i);
                prompt.append(i).append(": ").append(monster.name)
                      .append(" (HP: ").append(monster.currentHealth).append("/").append(monster.maxHealth)
                      .append(", Intent: ").append(monster.intent.name()).append(")\n");
            }
        }
        
        // 添加可用动作
        prompt.append("\nAvailable Actions:\n");
        for (String action : gameState.availableActions) {
            prompt.append("- ").append(action).append("\n");
        }
        
        // 添加重要说明
        prompt.append("\nImportant Notes:\n");
        prompt.append("1. For cards that require a target (marked with 'Requires Target'), you MUST specify the target index in the format: PLAY_CARD [card_index] [target_index]\n");
        prompt.append("2. Target index should be the index of the monster from the Monsters list (0-based)\n");
        prompt.append("3. For cards that don't require a target, you can use: PLAY_CARD [card_index]\n");
        prompt.append("4. Always check if a card requires a target before playing it\n");
        prompt.append("5. Remember to format your response with REASON: and ACTION: lines\n");
        
        // 添加决策请求
        prompt.append("\nBased on the current game state, what action should be taken?");
        
        return prompt.toString();
    }
} 