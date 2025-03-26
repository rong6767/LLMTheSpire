package llmthespire;

import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.megacrit.cardcrawl.core.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.io.FileReader;

/**
 * Configuration for the LLM integration
 */
public class LLMConfig {
    private static final Logger logger = LogManager.getLogger(LLMConfig.class.getName());
    private static final String CONFIG_FILE = "llm_config.json";
    private static final String CONVERSATION_DIR = "llm_conversations";
    private static LLMConfig instance;
    private transient Gson gson;
    private transient String conversationDirectory;
    
    // 存儲多個API配置
    private Map<String, ApiConfig> apiConfigs;
    // 當前激活的API名稱
    private String activeApiName;
    
    // 其他配置
    private boolean saveConversations;
    private String systemPrompt;
    private Map<String, Object> extraParams;
    
    // Flag to prevent recursive loading
    private static boolean isLoading = false;
    
    /**
     * API providers supported by the system
     */
    public enum ApiType {
        OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions"),
        ANTHROPIC("Anthropic", "https://api.anthropic.com/v1/messages"),
        DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1/chat/completions"),
        AZURE_OPENAI("Azure OpenAI", "");
        
        private final String displayName;
        private final String defaultEndpoint;
        
        ApiType(String displayName, String defaultEndpoint) {
            this.displayName = displayName;
            this.defaultEndpoint = defaultEndpoint;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDefaultEndpoint() {
            return defaultEndpoint;
        }
    }
    
    /**
     * API配置類，用於存儲每個API的詳細配置
     */
    public static class ApiConfig {
        private String name;
        private boolean enabled;
        private ApiType apiType;
        private String apiKey;
        private String apiEndpoint;
        private String model;
        private float temperature;
        private int maxTokens;
        
        public ApiConfig() {
            // 默認值
            this.name = "";
            this.enabled = false;
            this.apiType = ApiType.OPENAI;
            this.apiKey = "";
            this.apiEndpoint = apiType.getDefaultEndpoint();
            this.model = "gpt-4o";
            this.temperature = 0.7f;
            this.maxTokens = 1024;
        }
        
        public ApiConfig(String name, ApiType apiType) {
            this();
            this.name = name;
            this.apiType = apiType;
            this.apiEndpoint = apiType.getDefaultEndpoint();
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public ApiType getApiType() {
            return apiType;
        }
        
        public void setApiType(ApiType apiType) {
            this.apiType = apiType;
            if (apiEndpoint == null || apiEndpoint.isEmpty() || 
                (this.apiEndpoint != null && this.apiEndpoint.equals(this.apiType.getDefaultEndpoint()))) {
                this.apiEndpoint = apiType.getDefaultEndpoint();
            }
        }
        
        public String getApiKey() {
            return apiKey;
        }
        
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
        
        public String getApiEndpoint() {
            return apiEndpoint;
        }
        
        public void setApiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
        }
        
        public String getModel() {
            return model;
        }
        
        public void setModel(String model) {
            this.model = model;
        }
        
        public float getTemperature() {
            return temperature;
        }
        
        public void setTemperature(float temperature) {
            this.temperature = temperature;
        }
        
        public int getMaxTokens() {
            return maxTokens;
        }
        
        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized LLMConfig getInstance() {
        if (instance == null) {
            instance = new LLMConfig();
        }
        return instance;
    }
    
    /**
     * Private constructor for singleton
     */
    private LLMConfig() {
        // Initialize transient field (can't be final anymore)
        gson = new GsonBuilder()
            .setPrettyPrinting()
            .enableComplexMapKeySerialization()
            .create();
        
        // Create conversation directory using preferences path
        String basePath = "";
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            basePath = System.getProperty("user.home") + "/AppData/Roaming/SlayTheSpire/";
        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            basePath = System.getProperty("user.home") + "/Library/Preferences/SlayTheSpire/";
        } else {
            basePath = System.getProperty("user.home") + "/.config/SlayTheSpire/";
        }
        
        // Initialize transient field (can't be final anymore)
        conversationDirectory = basePath + CONVERSATION_DIR;
        try {
            Files.createDirectories(Paths.get(conversationDirectory));
        } catch (IOException e) {
            logger.error("Failed to create conversation directory", e);
        }
        
        // 初始化API配置映射
        apiConfigs = new HashMap<>();
        
        // 設置默認值
        saveConversations = true;
        systemPrompt = "You are an AI assistant playing Slay the Spire. Your job is to analyze the game state and make strategic choices to win the game. Choose the best action based on the current game state.";
        extraParams = new HashMap<>();
        
        // 創建默認API配置
        createDefaultConfigs();
        
        // 加載配置
        loadConfig();
    }
    
    /**
     * 創建默認的API配置
     */
    private void createDefaultConfigs() {
        // 創建默認的OpenAI配置
        ApiConfig openaiConfig = new ApiConfig("OpenAI", ApiType.OPENAI);
        openaiConfig.setEnabled(true);
        openaiConfig.setModel("gpt-4o");
        apiConfigs.put(openaiConfig.getName(), openaiConfig);
        
        // 創建默認的Anthropic配置
        ApiConfig anthropicConfig = new ApiConfig("Anthropic", ApiType.ANTHROPIC);
        anthropicConfig.setModel("claude-3-opus-20240229");
        apiConfigs.put(anthropicConfig.getName(), anthropicConfig);
        
        // 創建默認的DeepSeek配置
        ApiConfig deepseekConfig = new ApiConfig("DeepSeek", ApiType.DEEPSEEK);
        deepseekConfig.setModel("deepseek-chat");
        apiConfigs.put(deepseekConfig.getName(), deepseekConfig);
        
        // 默認選擇第一個配置作為活躍的
        activeApiName = "OpenAI";
    }
    
    /**
     * Load the config from file
     */
    private void loadConfig() {
        // Prevent recursive loading
        if (isLoading) {
            logger.warn("Detected recursive config loading, aborting");
            return;
        }
        
        try {
            isLoading = true;
            Path configPath = Paths.get(conversationDirectory, CONFIG_FILE);
            if (Files.exists(configPath)) {
                String json = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
                
                try {
                    // Load the data into a map
                    Type mapType = new TypeToken<HashMap<String, Object>>(){}.getType();
                    Map<String, Object> data = gson.fromJson(json, mapType);
                    
                    if (data != null) {
                        // Parse the apiConfigs
                        if (data.containsKey("apiConfigs")) {
                            String apiConfigsJson = gson.toJson(data.get("apiConfigs"));
                            Type apiConfigsType = new TypeToken<Map<String, ApiConfig>>(){}.getType();
                            Map<String, ApiConfig> loadedApiConfigs = gson.fromJson(apiConfigsJson, apiConfigsType);
                            if (loadedApiConfigs != null) {
                                this.apiConfigs = loadedApiConfigs;
                            }
                        }
                        
                        // Parse other primitive properties
                        if (data.containsKey("activeApiName")) {
                            this.activeApiName = (String) data.get("activeApiName");
                        }
                        
                        if (data.containsKey("saveConversations")) {
                            Object value = data.get("saveConversations");
                            if (value instanceof Boolean) {
                                this.saveConversations = (Boolean) value;
                            }
                        }
                        
                        if (data.containsKey("systemPrompt")) {
                            this.systemPrompt = (String) data.get("systemPrompt");
                        }
                        
                        if (data.containsKey("extraParams")) {
                            String extraParamsJson = gson.toJson(data.get("extraParams"));
                            Type extraParamsType = new TypeToken<Map<String, Object>>(){}.getType();
                            Map<String, Object> loadedExtraParams = gson.fromJson(extraParamsJson, extraParamsType);
                            if (loadedExtraParams != null) {
                                this.extraParams = loadedExtraParams;
                            }
                        }
                        
                        // 確保活躍API存在
                        if (this.activeApiName == null || !this.apiConfigs.containsKey(this.activeApiName)) {
                            // 選擇第一個啟用的API
                            for (Map.Entry<String, ApiConfig> entry : this.apiConfigs.entrySet()) {
                                if (entry.getValue().isEnabled()) {
                                    this.activeApiName = entry.getKey();
                                    break;
                                }
                            }
                            // 如果沒有啟用的API，選擇第一個
                            if (this.activeApiName == null && !this.apiConfigs.isEmpty()) {
                                this.activeApiName = this.apiConfigs.keySet().iterator().next();
                            }
                        }
                        
                        logger.info("Loaded LLM config from " + configPath);
                    }
                } catch (Exception e) {
                    // If we cannot parse the new format, try with defaults
                    logger.error("Failed to load config: " + e.getMessage(), e);
                    
                    // Create new defaults since parsing failed
                    createDefaultConfigs();
                    saveConfig();
                }
            } else {
                saveConfig(); // Create default config
                logger.info("Created default config at " + configPath);
            }
        } catch (Exception e) {
            logger.error("Failed to load config", e);
        } finally {
            isLoading = false;
        }
    }
    
    /**
     * Save config to file
     */
    public void saveConfig() {
        Path configPath = Paths.get(conversationDirectory, CONFIG_FILE);
        try {
            // Create a new Gson instance for serialization
            Gson serializer = new GsonBuilder()
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .create();
            
            // Create a clean copy without transient/non-serializable fields
            Map<String, Object> configData = new HashMap<>();
            configData.put("apiConfigs", apiConfigs);
            configData.put("activeApiName", activeApiName);
            configData.put("saveConversations", saveConversations);
            configData.put("systemPrompt", systemPrompt);
            configData.put("extraParams", extraParams);
            
            // Serialize the clean copy
            String json = serializer.toJson(configData);
            Files.write(configPath, json.getBytes(StandardCharsets.UTF_8));
            logger.info("Saved LLM config to " + configPath);
        } catch (IOException e) {
            logger.error("Failed to save config", e);
        }
    }

    /**
     * 獲取當前活躍的API配置
     */
    public ApiConfig getActiveApiConfig() {
        if (activeApiName != null && apiConfigs.containsKey(activeApiName)) {
            return apiConfigs.get(activeApiName);
        }
        // 沒有活躍配置，嘗試返回第一個啟用的配置
        for (ApiConfig config : apiConfigs.values()) {
            if (config.isEnabled()) {
                activeApiName = config.getName();
                return config;
            }
        }
        // 沒有啟用的配置，返回第一個
        if (!apiConfigs.isEmpty()) {
            String firstKey = apiConfigs.keySet().iterator().next();
            activeApiName = firstKey;
            return apiConfigs.get(firstKey);
        }
        // 如果沒有任何配置，創建一個默認的
        createDefaultConfigs();
        return apiConfigs.get(activeApiName);
    }
    
    /**
     * 添加或更新API配置
     */
    public void addOrUpdateApiConfig(ApiConfig config) {
        if (config != null && config.getName() != null && !config.getName().isEmpty()) {
            apiConfigs.put(config.getName(), config);
            saveConfig();
        }
    }
    
    /**
     * 刪除API配置
     */
    public void removeApiConfig(String name) {
        if (apiConfigs.containsKey(name)) {
            apiConfigs.remove(name);
            // 如果刪除的是當前活躍的配置，重新選擇一個活躍配置
            if (name.equals(activeApiName)) {
                if (!apiConfigs.isEmpty()) {
                    activeApiName = apiConfigs.keySet().iterator().next();
                } else {
                    activeApiName = null;
                }
            }
            saveConfig();
        }
    }
    
    /**
     * 設置活躍的API配置
     */
    public void setActiveApiName(String name) {
        if (apiConfigs.containsKey(name)) {
            activeApiName = name;
            saveConfig();
        }
    }
    
    /**
     * 獲取所有API配置
     */
    public Map<String, ApiConfig> getApiConfigs() {
        return apiConfigs;
    }
    
    /**
     * 獲取當前活躍的API名稱
     */
    public String getActiveApiName() {
        return activeApiName;
    }
    
    // 以下是為了兼容現有代碼的方法，它們都是基於當前活躍的API配置
    
    public boolean isEnabled() {
        // 強制啟用，確保始終向API發送請求
        return true;
    }
    
    public ApiType getApiType() {
        return getActiveApiConfig().getApiType();
    }
    
    public String getApiKey() {
        return getActiveApiConfig().getApiKey();
    }
    
    public String getApiEndpoint() {
        return getActiveApiConfig().getApiEndpoint();
    }
    
    public String getModel() {
        return getActiveApiConfig().getModel();
    }
    
    public float getTemperature() {
        return getActiveApiConfig().getTemperature();
    }
    
    public int getMaxTokens() {
        return getActiveApiConfig().getMaxTokens();
    }
    
    // 全局設置
    
    public boolean isSaveConversations() {
        return saveConversations;
    }
    
    public void setSaveConversations(boolean saveConversations) {
        this.saveConversations = saveConversations;
        saveConfig();
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        saveConfig();
    }
    
    public Map<String, Object> getExtraParams() {
        return extraParams;
    }
    
    public void setExtraParams(Map<String, Object> extraParams) {
        this.extraParams = extraParams;
        saveConfig();
    }
    
    public String getConversationDirectory() {
        return conversationDirectory;
    }
} 