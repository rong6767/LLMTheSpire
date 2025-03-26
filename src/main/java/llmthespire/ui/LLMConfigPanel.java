package llmthespire.ui;

import basemod.ModLabel;
import basemod.ModPanel;
import basemod.ModSlider;
import basemod.ModToggleButton;
import basemod.ModButton;
import basemod.IUIElement;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import llmthespire.LLMConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI panel for configuring LLM settings
 */
public class LLMConfigPanel extends ModPanel {
    private static final Logger logger = LogManager.getLogger(LLMConfigPanel.class.getName());
    
    // UI layout constants
    private static final float TITLE_X = 800.0f * Settings.scale;
    private static final float START_Y = 700.0f * Settings.scale;
    private static final float SPACING = 40.0f * Settings.scale;
    private static final float LABEL_X = 600.0f * Settings.scale;
    private static final float VALUE_X = 800.0f * Settings.scale;
    private static final float TEXTAREA_WIDTH = 420.0f * Settings.scale;
    private static final float TEXTAREA_HEIGHT = 120.0f * Settings.scale;
    
    // UI elements
    private final ModLabel activeApiLabel;
    private final ModLabel configsLabel;
    private final ModLabel systemPromptValueLabel;
    private final ModLabel saveConversationsValueLabel;
    
    public LLMConfigPanel() {
        LLMConfig config = LLMConfig.getInstance();
        LLMConfig.ApiConfig activeConfig = config.getActiveApiConfig();
        
        float currentY = START_Y;
        
        // Title
        ModLabel titleLabel = new ModLabel("LLM Configuration (Read-only)", TITLE_X, currentY, Settings.CREAM_COLOR, FontHelper.buttonLabelFont, this, 
                (l) -> {});
        addUIElement(titleLabel);
        currentY -= SPACING * 1.5f;
        
        // Active API Configuration
        ModLabel activeApiHeaderLabel = new ModLabel("Active API Configuration:", TITLE_X, currentY, Settings.CREAM_COLOR, FontHelper.buttonLabelFont, this, 
                (l) -> {});
        addUIElement(activeApiHeaderLabel);
        currentY -= SPACING;
        
        // 當前活躍的API配置
        ModLabel activeApiNameLabel = new ModLabel("Name:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(activeApiNameLabel);
        
        activeApiLabel = new ModLabel(config.getActiveApiName(), VALUE_X, currentY, Settings.GREEN_TEXT_COLOR, FontHelper.charDescFont, this, 
                (l) -> {});
        addUIElement(activeApiLabel);
        currentY -= SPACING;
        
        // API Type
        ModLabel apiTypeLabel = new ModLabel("API Type:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(apiTypeLabel);
        
        ModLabel apiTypeValueLabel = new ModLabel(activeConfig.getApiType().getDisplayName(), VALUE_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this, 
                (l) -> {});
        addUIElement(apiTypeValueLabel);
        currentY -= SPACING;
        
        // API Endpoint
        ModLabel apiEndpointLabel = new ModLabel("API Endpoint:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(apiEndpointLabel);
        
        String displayEndpoint = activeConfig.getApiEndpoint();
        if (displayEndpoint.length() > 30) {
            displayEndpoint = displayEndpoint.substring(0, 27) + "...";
        }
        ModLabel apiEndpointValueLabel = new ModLabel(displayEndpoint, VALUE_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this, 
                (l) -> {});
        addUIElement(apiEndpointValueLabel);
        currentY -= SPACING;
        
        // API Key
        ModLabel apiKeyLabel = new ModLabel("API Key:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(apiKeyLabel);
        
        String displayKey = activeConfig.getApiKey().isEmpty() ? "[Not Set]" : "********";
        ModLabel apiKeyValueLabel = new ModLabel(displayKey, VALUE_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(apiKeyValueLabel);
        currentY -= SPACING;
        
        // Model Name
        ModLabel modelLabel = new ModLabel("Model:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(modelLabel);
        
        ModLabel modelNameValueLabel = new ModLabel(activeConfig.getModel(), VALUE_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(modelNameValueLabel);
        currentY -= SPACING;
        
        // Temperature
        ModLabel temperatureLabel = new ModLabel("Temperature:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(temperatureLabel);
        
        ModLabel temperatureValueLabel = new ModLabel(String.format("%.2f", activeConfig.getTemperature()), VALUE_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(temperatureValueLabel);
        currentY -= SPACING;
        
        // Max Tokens
        ModLabel maxTokensLabel = new ModLabel("Max Tokens:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(maxTokensLabel);
        
        ModLabel maxTokensValueLabel = new ModLabel(String.valueOf(activeConfig.getMaxTokens()), VALUE_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(maxTokensValueLabel);
        currentY -= SPACING;
        
        // Status
        ModLabel enabledLabel = new ModLabel("Enabled:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(enabledLabel);
        
        ModLabel enabledValueLabel = new ModLabel(activeConfig.isEnabled() ? "Yes" : "No", VALUE_X, currentY, activeConfig.isEnabled() ? Settings.GREEN_TEXT_COLOR : Settings.RED_TEXT_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(enabledValueLabel);
        currentY -= SPACING * 1.5f;
        
        // 全局設置
        ModLabel globalSettingsLabel = new ModLabel("Global Settings:", TITLE_X, currentY, Settings.CREAM_COLOR, FontHelper.buttonLabelFont, this, 
                (l) -> {});
        addUIElement(globalSettingsLabel);
        currentY -= SPACING;
        
        // Save Conversations
        ModLabel saveConversationsLabel = new ModLabel("Save Conversations:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(saveConversationsLabel);
        
        saveConversationsValueLabel = new ModLabel(config.isSaveConversations() ? "Yes" : "No", VALUE_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(saveConversationsValueLabel);
        currentY -= SPACING;
        
        // System Prompt
        ModLabel systemPromptLabel = new ModLabel("System Prompt:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(systemPromptLabel);
        currentY -= 20.0f * Settings.scale;
        
        systemPromptValueLabel = new ModLabel(formatMultiline(config.getSystemPrompt(), 40), LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(systemPromptValueLabel);
        currentY -= 80.0f * Settings.scale;
        
        // 所有API配置列表
        ModLabel availableConfigsLabel = new ModLabel("Available API Configurations:", TITLE_X, currentY, Settings.CREAM_COLOR, FontHelper.buttonLabelFont, this, 
                (l) -> {});
        addUIElement(availableConfigsLabel);
        currentY -= SPACING;
        
        // 構建配置列表
        StringBuilder configsList = new StringBuilder();
        Map<String, LLMConfig.ApiConfig> allConfigs = config.getApiConfigs();
        for (Map.Entry<String, LLMConfig.ApiConfig> entry : allConfigs.entrySet()) {
            LLMConfig.ApiConfig apiConfig = entry.getValue();
            String status = apiConfig.isEnabled() ? "Enabled" : "Disabled";
            configsList.append(entry.getKey()).append(" (").append(status).append("): ")
                      .append(apiConfig.getApiType().getDisplayName()).append(" - ")
                      .append(apiConfig.getModel()).append("\n");
        }
        
        configsLabel = new ModLabel(configsList.toString(), LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(configsLabel);
        
        // 配置列表後的間隔
        int configLines = allConfigs.size();
        currentY -= (SPACING * 0.8f) * (configLines + 1);
        
        // Instructions
        ModLabel instructionsLabel = new ModLabel("Configuration loaded from:", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(instructionsLabel);
        
        currentY -= 25.0f * Settings.scale;
        ModLabel instructionsLabel2 = new ModLabel(config.getConversationDirectory() + "llm_config.json", LABEL_X, currentY, Settings.CREAM_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(instructionsLabel2);
        
        currentY -= 40.0f * Settings.scale;
        ModLabel instructionsLabel3 = new ModLabel("To modify settings, edit this file directly.", LABEL_X, currentY, Settings.GOLD_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(instructionsLabel3);
        
        currentY -= 25.0f * Settings.scale;
        ModLabel instructionsLabel4 = new ModLabel("Changes will take effect after restarting the game.", LABEL_X, currentY, Settings.GOLD_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(instructionsLabel4);
        
        currentY -= 25.0f * Settings.scale;
        ModLabel instructionsLabel5 = new ModLabel("To switch APIs, set 'activeApiName' and ensure the API is enabled.", LABEL_X, currentY, Settings.GOLD_COLOR, FontHelper.charDescFont, this,
                (l) -> {});
        addUIElement(instructionsLabel5);
    }
    
    @Override
    public void update() {
        super.update();
        // No update needed, panel is read-only
    }
    
    /**
     * Format a long string as a multiline string for display
     */
    private String formatMultiline(String text, int charsPerLine) {
        if (text.length() <= charsPerLine) {
            return text;
        }
        
        StringBuilder result = new StringBuilder();
        int startIndex = 0;
        while (startIndex < text.length()) {
            int endIndex = Math.min(startIndex + charsPerLine, text.length());
            if (endIndex < text.length() && text.charAt(endIndex) != ' ') {
                // Try to break at a space
                int lastSpace = text.lastIndexOf(' ', endIndex);
                if (lastSpace > startIndex) {
                    endIndex = lastSpace;
                }
            }
            
            result.append(text.substring(startIndex, endIndex).trim());
            result.append("\n");
            startIndex = endIndex;
        }
        
        return result.toString();
    }
} 