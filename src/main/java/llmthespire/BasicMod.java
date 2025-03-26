package llmthespire;

import basemod.BaseMod;
import basemod.interfaces.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.*;
import llmthespire.ui.LLMConfigPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpireInitializer
public class BasicMod implements
        EditCardsSubscriber,
        EditRelicsSubscriber,
        EditStringsSubscriber,
        EditKeywordsSubscriber,
        PostInitializeSubscriber {

    public static final String MOD_ID = "llmthespire";
    public static final String modName = "LLM-Powered Autoplay";
    private static final String BADGE_IMAGE = "llmthespire/images/badge.png";
    
    private static final String MODNAME = "LLM-Powered Autoplay";
    private static final String AUTHOR = "Your Name";
    private static final String DESCRIPTION = "Uses LLM to play the game automatically.";
    
    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(BasicMod.class.getName());
    
    // This is for the in-game mod settings panel
    private static final String SETTINGS_PANEL_TXT = "Use this mod to let an LLM (ChatGPT, Claude, etc) play the game for you using your API key.";
    
    // Thread-safe logging throttling system
    private static final long LOG_THROTTLE_MS = 5000; // Only log once per 5 seconds for similar messages
    private static final Map<String, Long> lastLogTimes = new ConcurrentHashMap<>();
    
    // Static SafeLogger instance for general mod logging
    private static final SafeLogger safeLogger = new SafeLogger(BasicMod.class.getName());
    
    // This makes it so we can type BasicMod.ID instead of a String literal
    public static final String ID = "llmthespire";
    
    /**
     * Get a safe logger for the mod
     */
    public static SafeLogger getLogger() {
        return safeLogger;
    }
    
    /**
     * Safe logger to prevent log4j locking issues 
     */
    public static class SafeLogger {
        private final String name;
        
        public SafeLogger(String name) {
            this.name = name;
        }
        
        public void info(String message) {
            logWithThrottle(Level.INFO, message);
        }
        
        public void debug(String message) {
            logWithThrottle(Level.DEBUG, message);
        }
        
        public void warn(String message) {
            logWithThrottle(Level.WARN, message);
        }
        
        public void error(String message) {
            logWithThrottle(Level.ERROR, message);
        }
        
        public void error(String message, Throwable t) {
            logWithThrottle(Level.ERROR, message + ": " + t.getMessage());
        }
        
        private void logWithThrottle(Level level, String message) {
            // Create a simple hash of the log level + message to identify similar messages
            String logKey = level.name() + ":" + message.hashCode();
            long now = System.currentTimeMillis();
            
            Long lastTime = lastLogTimes.get(logKey);
            if (lastTime == null || now - lastTime > LOG_THROTTLE_MS) {
                lastLogTimes.put(logKey, now);
                LogManager.getLogger(name).log(level, message);
            }
        }
    }

    // Configure log4j to reduce contention
    static {
        try {
            // Reduce logging level for some known noisy loggers to ERROR
            Configurator.setLevel("com.megacrit.cardcrawl.helpers.steamInput.SteamInputDetect", Level.ERROR);
            
            // Set the root logger to DEBUG to show debug messages
            Configurator.setRootLevel(Level.DEBUG);
            
            // Log the configuration
            logger.info("Configured log4j with DEBUG level logging");
        } catch (Exception e) {
            System.err.println("Error configuring log4j: " + e.getMessage());
        }
    }

    /**
     * Helper methods for constructing paths
     */
    public static String makeID(String id) {
        return MOD_ID + ":" + id;
    }

    public static String makeResourcePath(String resource) {
        return MOD_ID + "/images/" + resource;
    }

    public static String makeImagePath(String resourcePath) {
        return makeResourcePath(resourcePath);
    }

    public static String makePowerPath(String resourcePath) {
        return makeResourcePath("powers/" + resourcePath);
    }
    
    /**
     * Resource path helpers for accessing assets
     */
    public static String makeRelicPath(String resourcePath) {
        return MOD_ID + "/images/relics/" + resourcePath;
    }

    public static String makeRelicOutlinePath(String resourcePath) {
        return MOD_ID + "/images/relics/outline/" + resourcePath;
    }
    
    public static String makeCardPath(String resourcePath) {
        return MOD_ID + "/images/cards/" + resourcePath;
    }
    
    public static String makeUIPath(String resourcePath) {
        return MOD_ID + "/images/ui/" + resourcePath;
    }
    
    public static String makeCharPath(String resourcePath) {
        return MOD_ID + "/images/char/" + resourcePath;
    }
    
    public static String makeLocalizationPath(String resourcePath) {
        return MOD_ID + "/localization/" + resourcePath;
    }
    
    /**
     * Subscribe to BaseMod hooks
     */
    public static void initialize() {
        logger.info("Initialize");
        logger.info("Subscribing to BaseMod hooks");
        
        BaseMod.subscribe(new BasicMod());
        
        logger.info("Done subscribing");
    }
    
    @Override
    public void receiveEditRelics() {
        logger.info("Adding relics");
    }

    @Override
    public void receiveEditCards() {
        logger.info("Adding cards");
    }

    @Override
    public void receivePostInitialize() {
        // Create and register the config panel
        LLMConfigPanel llmConfigPanel = new LLMConfigPanel();
        
        // Initialize our components
        LLMConfig.getInstance();
        LLMAutoplayController.getInstance();
        
        // Register the mod badge
        Texture badgeTexture = new Texture(BADGE_IMAGE);
        BaseMod.registerModBadge(
                badgeTexture, 
                modName, 
                AUTHOR, 
                "Uses AI to analyze game state and make optimal plays.", 
                llmConfigPanel
        );
        
        logger.info("Initialized LLM-Powered Autoplay mod");
    }
    
    @Override
    public void receiveEditStrings() {
        logger.info("Loading localization strings based on language setting: " + Settings.language);
        
        // Load English as the default
        loadLocalizationStrings("eng");
        
        // Try to load the language based on the current setting
        if (Settings.language != Settings.GameLanguage.ENG) {
            try {
                String langCode = getLangCodeFromGameLanguage(Settings.language);
                loadLocalizationStrings(langCode);
            } catch (Exception e) {
                logger.error("Failed to load localization for " + Settings.language, e);
            }
        }
    }
    
    /**
     * Convert game language to language code
     */
    private String getLangCodeFromGameLanguage(Settings.GameLanguage language) {
        switch (language) {
            case ZHS:
                return "zhs";
            case ZHT:
                return "zht";
            case JPN:
                return "jpn";
            case KOR:
                return "kor";
            case FRA:
                return "fra";
            case DEU:
                return "deu";
            case ITA:
                return "ita";
            case RUS:
                return "rus";
            default:
                return "eng";
        }
    }
    
    /**
     * Load localization strings for the specified language
     */
    private void loadLocalizationStrings(String langCode) {
        try {
            // UI Text
            loadJsonStrings(UIStrings.class, makeLocalizationPath(langCode + "/ui.json"));
            
            // Card Text
            loadJsonStrings(CardStrings.class, makeLocalizationPath(langCode + "/cards.json"));
            
            // Power Text
            loadJsonStrings(PowerStrings.class, makeLocalizationPath(langCode + "/powers.json"));
            
            // Relic Text
            loadJsonStrings(RelicStrings.class, makeLocalizationPath(langCode + "/relics.json"));
            
            // Event Text
            loadJsonStrings(EventStrings.class, makeLocalizationPath(langCode + "/events.json"));
            
            // Potion Text
            loadJsonStrings(PotionStrings.class, makeLocalizationPath(langCode + "/potions.json"));
            
            // Monster Text
            loadJsonStrings(MonsterStrings.class, makeLocalizationPath(langCode + "/monsters.json"));
            
            logger.info("Done loading localization strings for: " + langCode);
        } catch (Exception e) {
            logger.error("Error loading localization strings for " + langCode, e);
        }
    }
    
    /**
     * Helper method to load JSON strings for a given type and path
     */
    private <T> void loadJsonStrings(Class<T> stringType, String path) {
        try {
            InputStream in = BasicMod.class.getClassLoader().getResourceAsStream(path);
            if (in == null) {
                logger.warn("No localization found at: " + path);
                return;
            }
            
            Gson gson = new Gson();
            T strings = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), stringType);
            BaseMod.loadCustomStringsFile(stringType, path);
            
        } catch (Exception e) {
            logger.error("Error loading strings from " + path, e);
        }
    }
    
    @Override
    public void receiveEditKeywords() {
        try {
            // Use the language detected by the game
            String langCode = getLangCodeFromGameLanguage(Settings.language);
            
            // If not English, load English first as a backup
            if (!langCode.equals("eng")) {
                loadKeywords("eng");
            }
            
            // Then load the language-specific keywords
            loadKeywords(langCode);
            
        } catch (Exception e) {
            logger.error("Error loading keywords", e);
        }
    }
    
    /**
     * Helper method to load keywords for a given language
     */
    private void loadKeywords(String langCode) {
        try {
            String path = makeLocalizationPath(langCode + "/keywords.json");
            InputStream in = BasicMod.class.getClassLoader().getResourceAsStream(path);
            if (in == null) {
                logger.warn("No keyword localization found at: " + path);
                return;
            }
            
            Gson gson = new Gson();
            KeywordWithProper[] keywords = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), KeywordWithProper[].class);
            
            for (KeywordWithProper keyword : keywords) {
                BaseMod.addKeyword(MOD_ID, keyword.PROPER_NAME, keyword.NAMES, keyword.DESCRIPTION);
            }
            
            logger.info("Done loading keywords for language: " + langCode);
        } catch (Exception e) {
            logger.error("Error loading keywords for " + langCode, e);
        }
    }
    
    /**
     * Class to help parse keywords from JSON
     */
    private static class KeywordWithProper {
        public String PROPER_NAME;
        public String[] NAMES;
        public String DESCRIPTION;
    }
}
