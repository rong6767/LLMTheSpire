package llmthespire;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import llmthespire.game.ActionExecutor;
import llmthespire.game.GameState;
import llmthespire.game.GameStageType;
import llmthespire.llm.LLMService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controls the LLM-powered autoplay functionality
 */
public class LLMAutoplayController {
    private static final Logger logger = LogManager.getLogger(LLMAutoplayController.class.getName());
    private static LLMAutoplayController instance;
    
    // State tracking
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private long lastProcessTime = 0;
    private static final long PROCESS_COOLDOWN_MS = 1000; // Increased cooldown to 1 second
    
    // Track game state to detect changes
    private String lastGamePhase = "";
    private int lastPlayerHealth = -1;
    private int lastEnemyCount = -1;
    private int lastHandSize = -1;
    private int lastTurnNumber = -1;
    private int lastEnergy = -1;
    
    // 用于状态变化检测的变量
    private AbstractRoom.RoomPhase lastProcessedPhase = null;
    private int lastProcessedPlayerHp = -1;
    private int lastProcessedMonsterCount = -1;
    private int lastProcessedHandSize = -1;
    private int lastProcessedTurn = -1;
    private int lastProcessedEnergy = -1;
    private long lastProcessedTime = 0;
    
    // Services
    private LLMService llmService;
    private ActionExecutor actionExecutor;
    private ConversationLogger conversationLogger;
    
    /**
     * Private constructor for singleton
     */
    private LLMAutoplayController() {
        logger.info("Initializing LLM Autoplay Controller");
        try {
            llmService = LLMService.getInstance();
            actionExecutor = new ActionExecutor();
            conversationLogger = new ConversationLogger();
            logger.info("LLM Autoplay Controller initialized successfully");
        } catch (Exception e) {
            logger.error("Error initializing LLM Autoplay Controller: " + e.getMessage(), e);
            // Initialize with fallbacks to avoid null pointer exceptions
            if (llmService == null) llmService = LLMService.getInstance();
            if (actionExecutor == null) actionExecutor = new ActionExecutor();
            if (conversationLogger == null) conversationLogger = new ConversationLogger();
        }
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized LLMAutoplayController getInstance() {
        if (instance == null) {
            try {
                instance = new LLMAutoplayController();
            } catch (Exception e) {
                logger.error("Failed to create LLMAutoplayController instance: " + e.getMessage(), e);
                // Create a basic instance to avoid null pointer exceptions
                instance = new LLMAutoplayController();
            }
        }
        return instance;
    }
    
    /**
     * Toggle the autoplay functionality
     * @return The new enabled state
     */
    public boolean toggleEnabled() {
        boolean newState = !enabled.get();
        enabled.set(newState);
        logger.info("LLM Autoplay " + (newState ? "enabled" : "disabled"));
        
        // Reset state tracking variables when enabling
        if (newState) {
            lastGamePhase = "";
            lastPlayerHealth = -1;
            lastEnemyCount = -1;
            lastHandSize = -1;
            lastTurnNumber = -1;
            lastEnergy = -1;
            
            if (!processing.get()) {
                update();
            }
            
            conversationLogger.startNewSession();
        }
        
        return newState;
    }
    
    /**
     * Check if autoplay is currently enabled
     */
    public boolean isEnabled() {
        return enabled.get();
    }
    
    /**
     * Check if autoplay is currently processing
     */
    public boolean isProcessing() {
        return processing.get();
    }
    
    /**
     * Update method to be called from game loop
     */
    public void update() {
        // 添加debug日志，显示当前状态
        if (enabled.get() && AbstractDungeon.player != null) {
            logger.debug("LLMAutoplay checking state: enabled=" + enabled.get() + 
                       ", processing=" + processing.get() + 
                       ", gamePhase=" + (AbstractDungeon.getCurrRoom() != null ? AbstractDungeon.getCurrRoom().phase : "null"));
        }
        
        // If not enabled or already processing, or in a screen where we shouldn't autoplay, skip
        if (!enabled.get() || processing.get() || !canProcessGameState()) {
            return;
        }
        
        // Avoid processing if game is waiting for player input but action manager has pending actions
        if (AbstractDungeon.actionManager != null && !AbstractDungeon.actionManager.isEmpty()) {
            return;
        }
        
        // In combat, only process during player turn when they can actually do something
        if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
            // Skip if it's not player's turn
            if (AbstractDungeon.actionManager.turnHasEnded) {
                return;
            }
        }
        
        // 无论状态是否变化，每隔一段时间都强制处理一次
        long now = System.currentTimeMillis();
        if (now - lastProcessTime >= PROCESS_COOLDOWN_MS * 2) {
            logger.info("Forcing state processing due to time elapsed");
            processGameState();
            return;
        }
        
        // Check if game state has changed
        if (!hasGameStateChanged()) {
            return;
        }
        
        // Avoid processing too frequently even if state changed
        if (now - lastProcessTime < PROCESS_COOLDOWN_MS) {
            return;
        }
        
        // Start processing
        processGameState();
    }
    
    /**
     * Check if the game state has changed enough to warrant processing
     * This helps avoid unnecessary API calls when the state hasn't changed much
     */
    private boolean hasGameStateChanged() {
        try {
            // Always process if this is the first time
            if (lastProcessedPhase == null) {
                logger.debug("First time processing, no previous state");
                updateLastProcessedState();
                return true;
            }
            
            // If we're in a different phase, definitely process
            AbstractRoom.RoomPhase currentPhase = AbstractDungeon.getCurrRoom().phase;
            if (lastProcessedPhase != currentPhase) {
                logger.debug("Room phase changed from " + lastProcessedPhase + " to " + currentPhase);
                updateLastProcessedState();
                return true;
            }
            
            // Special handling for combat
            if (currentPhase == AbstractRoom.RoomPhase.COMBAT) {
                int currentPlayerHp = AbstractDungeon.player.currentHealth;
                int currentMonsterCount = AbstractDungeon.getMonsters().monsters.size();
                int currentHandSize = AbstractDungeon.player.hand.size();
                int currentTurn = AbstractDungeon.actionManager.turn;
                int currentEnergy = EnergyPanel.totalCount;
                
                // 特殊处理第一回合
                if (currentTurn == 1 && lastProcessedTurn != currentTurn) {
                    logger.debug("First turn started, processing");
                    updateLastProcessedState();
                    return true;
                }
                
                // 检查游戏状态是否有明显变化
                boolean significant_change = false;
                
                // 检查HP变化
                if (Math.abs(lastProcessedPlayerHp - currentPlayerHp) > 0) {
                    logger.debug("Player HP changed from " + lastProcessedPlayerHp + " to " + currentPlayerHp);
                    significant_change = true;
                }
                
                // 检查怪物数量变化
                if (lastProcessedMonsterCount != currentMonsterCount) {
                    logger.debug("Monster count changed from " + lastProcessedMonsterCount + " to " + currentMonsterCount);
                    significant_change = true;
                }
                
                // 检查手牌变化
                if (lastProcessedHandSize != currentHandSize) {
                    logger.debug("Hand size changed from " + lastProcessedHandSize + " to " + currentHandSize);
                    significant_change = true;
                }
                
                // 检查回合变化
                if (lastProcessedTurn != currentTurn) {
                    logger.debug("Turn changed from " + lastProcessedTurn + " to " + currentTurn);
                    significant_change = true;
                }
                
                // 检查能量变化
                if (lastProcessedEnergy != currentEnergy) {
                    logger.debug("Energy changed from " + lastProcessedEnergy + " to " + currentEnergy);
                    significant_change = true;
                }
                
                // 如果有明显变化，则处理游戏状态
                if (significant_change) {
                    updateLastProcessedState();
                    return true;
                }
                
                // 特殊处理：即使没有明显变化，如果已经过了一段时间也应该处理
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastProcessedTime > 5000) {  // 5秒没有处理，强制重新处理
                    logger.debug("No change for 5 seconds, forcing reprocessing");
                    updateLastProcessedState();
                    return true;
                }
                
                return false;
            }
            
            // For non-combat phases, always process (could be optimized further)
            logger.debug("Non-combat phase, processing");
            updateLastProcessedState();
            return true;
        } catch (Exception e) {
            logger.error("Error in hasGameStateChanged: " + e.getMessage(), e);
            return true; // On error, better to process than not
        }
    }
    
    /**
     * Get the current game phase as a string
     */
    private String getCurrentGamePhase() {
        if (AbstractDungeon.getCurrRoom() == null) {
            return "UNKNOWN";
        }
        
        if (AbstractDungeon.isScreenUp) {
            return "SCREEN_" + AbstractDungeon.screen.toString();
        }
        
        return AbstractDungeon.getCurrRoom().phase.toString();
    }
    
    /**
     * Determine if we can process the game state
     */
    private boolean canProcessGameState() {
        try {
            if (AbstractDungeon.player == null || AbstractDungeon.getCurrRoom() == null) {
                logger.debug("Cannot process state: player or room is null");
                return false;
            }
            
            // 特殊处理奖励界面，即使屏幕打开也允许处理
            if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE) {
                // 确保有奖励可选
                if (AbstractDungeon.getCurrRoom().rewards != null && 
                    !AbstractDungeon.getCurrRoom().rewards.isEmpty()) {
                    // 如果有弹出式屏幕，暂不处理
                    if (AbstractDungeon.isScreenUp && AbstractDungeon.screen != AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
                        logger.debug("Other screen is open during rewards, skipping AI action");
                        return false;
                    }
                    return true;
                }
                return false;
            }
            
            // Skip if the game is paused or in a special mode
            if (AbstractDungeon.isScreenUp) {
                logger.debug("Cannot process state: screen is up");
                return false;
            }
            
            // Skip if the dungeon is still initializing
            if (!AbstractDungeon.isPlayerInDungeon()) {
                logger.debug("Cannot process state: player not in dungeon");
                return false;
            }
            
            // Skip if the room phase is null (transitioning between rooms)
            if (AbstractDungeon.getCurrRoom().phase == null) {
                logger.debug("Cannot process state: room phase is null");
                return false;
            }
            
            // For combat, additional checks to ensure it's player's turn
            if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
                // Skip if it's not player's turn
                if (AbstractDungeon.actionManager.turnHasEnded) {
                    logger.debug("Cannot process state: turn has ended");
                    return false;
                }
                
                // 检查是否有动作正在执行
                if (AbstractDungeon.actionManager.currentAction != null) {
                    logger.debug("Cannot process state: action in progress");
                    return false;
                }
                
                // 检查是否有动作队列待处理
                if (AbstractDungeon.actionManager.actions != null && 
                    !AbstractDungeon.actionManager.actions.isEmpty()) {
                    logger.debug("Cannot process state: action queue not empty");
                    return false;
                }
                
                // 检查是否是第一回合的初始阶段(抽牌中)
                int currentTurn = AbstractDungeon.actionManager.turn;
                if ((currentTurn == 0 || currentTurn == 1) && 
                    (AbstractDungeon.player.hand == null || 
                     AbstractDungeon.player.hand.isEmpty() || 
                     AbstractDungeon.player.drawPile.size() > AbstractDungeon.player.masterHandSize)) {
                    logger.debug("Cannot process state: first turn initialization in progress");
                    return false;
                }
                
                // Skip if there are pending animations or actions
                if (AbstractDungeon.actionManager.hasControl) {
                    logger.debug("Cannot process state: action manager has control");
                    return false;
                }
            }
            
            // Only autoplay in combat, events, rewards, or Neow event for now
            boolean canProcess = AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT || 
                  AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.EVENT ||
                  AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE ||
                  (AbstractDungeon.getCurrRoom() instanceof EventRoom && 
                   AbstractDungeon.getCurrRoom().event != null && 
                   AbstractDungeon.getCurrRoom().event.getClass().getSimpleName().equals("NeowEvent"));
                  
            if (canProcess) {
                logger.debug("Can process state: " + AbstractDungeon.getCurrRoom().phase);
            } else {
                logger.debug("Cannot process state: not in supported phase " + AbstractDungeon.getCurrRoom().phase);
            }
            
            return canProcess;
        } catch (Exception e) {
            logger.error("Error in canProcessGameState: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Process the current game state and make a decision
     */
    private void processGameState() {
        if (!processing.compareAndSet(false, true)) {
            return; // Another thread is already processing
        }
        
        lastProcessTime = System.currentTimeMillis();
        
        try {
            // In combat, only proceed if it's player's turn
            if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
                if (AbstractDungeon.actionManager.turnHasEnded) {
                    logger.debug("Not player's turn, skipping AI action");
                    processing.set(false);
                    return;
                }
                
                // Removed checks for empty hand and inability to play cards
                // to allow ending turn when appropriate
            }
            
            // Capture current game state
            GameState gameState = null;
            try {
                gameState = new GameState();
            } catch (Exception e) {
                logger.error("Failed to create GameState object: " + e.getMessage(), e);
                processing.set(false);
                return;
            }
            
            // Skip if there are no actions available
            if (gameState == null || gameState.availableActions == null || gameState.availableActions.isEmpty()) {
                logger.debug("No available actions in current game state");
                processing.set(false);
                return;
            }
            
            logger.info("Processing game state: " + gameState.stage.name() + 
                     ", available actions: " + gameState.availableActions.size());
            
            // Log the current game state
            String gameStateJson = gameState.toString();
            logger.debug("Game state: " + gameStateJson);
            if (conversationLogger != null) {
                conversationLogger.logGameState(gameStateJson);
            }
            
            // Request action from LLM
            if (llmService == null) {
                logger.error("LLM service is null");
                processing.set(false);
                return;
            }
            
            // Create final references for use in lambda
            final GameState finalGameState = gameState;
            
            // 记录请求开始的时间，用于性能监控
            long requestStartTime = System.currentTimeMillis();
            logger.info("Sending request to LLM service at " + requestStartTime);
            
            CompletableFuture<String> futureAction = llmService.requestAction(finalGameState);
            
            // Handle the response
            futureAction.thenAccept(action -> {
                long responseTime = System.currentTimeMillis() - requestStartTime;
                logger.info("Received LLM response after " + responseTime + "ms");
                
                try {
                    if (action == null || action.isEmpty()) {
                        logger.error("LLM returned empty action");
                        if (conversationLogger != null) {
                            conversationLogger.logAction("ERROR: Empty action returned", "No action available");
                        }
                        processing.set(false);
                        return;
                    }
                    
                    // Log the complete AI response first
                    if (conversationLogger != null) {
                        conversationLogger.logRawResponse(action);
                    }
                    
                    // Extract reason and action from the formatted response
                    String reasoning = "";
                    String actionCommand = "";
                    
                    String[] lines = action.split("\n");
                    for (String line : lines) {
                        if (line.trim().startsWith("REASON:")) {
                            reasoning = line.substring("REASON:".length()).trim();
                        } else if (line.trim().startsWith("ACTION:")) {
                            actionCommand = line.substring("ACTION:".length()).trim();
                        }
                    }
                    
                    // If no ACTION: line was found, try to use the whole response
                    if (actionCommand.isEmpty()) {
                        logger.warn("Could not find ACTION: line in response, using full response as action");
                        actionCommand = action.trim();
                    }
                    
                    logger.info("LLM suggested action: " + actionCommand);
                    if (!reasoning.isEmpty()) {
                        logger.info("LLM reasoning: " + reasoning);
                    }
                    
                    if (conversationLogger != null) {
                        conversationLogger.logAction(actionCommand, reasoning);
                    }
                    
                    // Execute the action
                    if (actionExecutor != null && finalGameState != null) {
                        boolean success = actionExecutor.executeAction(actionCommand, finalGameState);
                        logger.info("Action execution " + (success ? "successful" : "failed"));
                        if (conversationLogger != null) {
                            conversationLogger.logResult(success ? "SUCCESS" : "FAILED");
                        }
                    } else {
                        logger.error("ActionExecutor or GameState is null");
                        if (conversationLogger != null) {
                            conversationLogger.logResult("ERROR: Internal controller error");
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error executing LLM action: " + e.getMessage(), e);
                    if (conversationLogger != null) {
                        conversationLogger.logResult("ERROR: " + e.getMessage());
                    }
                } finally {
                    // Reset processing flag with a slight delay to prevent rapid re-processing
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    processing.set(false);
                }
            }).exceptionally(ex -> {
                logger.error("Error getting LLM action: " + ex.getMessage(), ex);
                if (conversationLogger != null) {
                    conversationLogger.logAction("ERROR: " + ex.getMessage(), "Exception occurred during processing");
                    conversationLogger.logResult("FAILED: API request error");
                }
                
                // 打印详细的异常堆栈，帮助诊断问题
                ex.printStackTrace();
                
                // 尝试使用默认动作（通常是结束回合）以避免游戏卡住
                if (finalGameState != null && finalGameState.stage == GameStageType.BATTLE) {
                    try {
                        logger.info("Trying fallback action: END_TURN due to API failure");
                        actionExecutor.executeAction("END_TURN", finalGameState);
                    } catch (Exception e) {
                        logger.error("Failed to execute fallback action", e);
                    }
                }
                
                processing.set(false);
                return null;
            });
        } catch (Exception e) {
            logger.error("Error in processGameState: " + e.getMessage(), e);
            processing.set(false);
        }
    }
    
    /**
     * Shutdown the controller and its components
     */
    public void shutdown() {
        enabled.set(false);
        
        // Allow any in-progress operations to complete
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Shutdown components
        if (llmService != null) {
            llmService.shutdown();
        }
    }
    
    /**
     * Logger for AI conversations and game state
     */
    private class ConversationLogger {
        private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
        private final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
        private String sessionTimestamp;
        private File logFile;
        
        /**
         * Start a new conversation logging session
         */
        public void startNewSession() {
            LLMConfig config = LLMConfig.getInstance();
            
            if (!config.isSaveConversations()) {
                return;
            }
            
            try {
                // Create session timestamp and log file
                sessionTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String filename = "llm_session_" + sessionTimestamp + ".log";
                
                // Ensure conversation directory exists
                File dir = new File(config.getConversationDirectory());
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                
                logFile = new File(dir, filename);
                
                // Create and initialize log file
                try (FileWriter writer = new FileWriter(logFile)) {
                    writer.write("=== LLM Autoplay Session Started at " + 
                               new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ===\n");
                    writer.write("Using API: " + config.getApiType() + ", Model: " + config.getModel() + "\n");
                    writer.write("System Prompt: " + config.getSystemPrompt() + "\n\n");
                }
                
                logger.info("Started conversation logging to: " + logFile.getPath());
            } catch (IOException e) {
                logger.error("Failed to create conversation log file", e);
            }
        }
        
        /**
         * Log the current game state
         */
        public void logGameState(String gameState) {
            if (logFile == null) {
                return;
            }
            
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write("[" + TIMESTAMP_FORMAT.format(new Date()) + "] GAME STATE:\n");
                writer.write(gameState + "\n\n");
            } catch (IOException e) {
                logger.error("Failed to log game state", e);
            }
        }
        
        /**
         * Log the LLM suggested action
         */
        public void logAction(String action, String reasoning) {
            if (logFile == null) {
                return;
            }
            
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write("[" + TIMESTAMP_FORMAT.format(new Date()) + "] LLM ACTION:\n");
                
                // Format and write reasoning with proper indentation if available
                if (reasoning != null && !reasoning.isEmpty()) {
                    writer.write("REASONING:\n");
                    
                    // Wrap long reasoning text for better readability
                    String[] reasoningLines = reasoning.split("\n");
                    for (String line : reasoningLines) {
                        // Format and indent reasoning
                        String formattedLine = formatTextWithIndent(line, 2);
                        writer.write(formattedLine + "\n");
                    }
                }
                
                // Write the action command
                writer.write("ACTION: " + action + "\n\n");
            } catch (IOException e) {
                logger.error("Failed to log LLM action", e);
            }
        }
        
        /**
         * Format text with indentation
         */
        private String formatTextWithIndent(String text, int indent) {
            StringBuilder indentation = new StringBuilder();
            for (int i = 0; i < indent; i++) {
                indentation.append("  "); // Two spaces per indent level
            }
            
            return indentation.toString() + text;
        }
        
        /**
         * Log the result of executing the action
         */
        public void logResult(String result) {
            if (logFile == null) {
                return;
            }
            
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write("[" + TIMESTAMP_FORMAT.format(new Date()) + "] RESULT:\n");
                writer.write(result + "\n\n");
                writer.write("-------------------------------------\n\n");
            } catch (IOException e) {
                logger.error("Failed to log action result", e);
            }
        }
        
        /**
         * Log the complete AI response
         */
        public void logRawResponse(String response) {
            if (logFile == null) {
                return;
            }
            
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write("[" + TIMESTAMP_FORMAT.format(new Date()) + "] RAW RESPONSE:\n");
                writer.write(response + "\n\n");
            } catch (IOException e) {
                logger.error("Failed to log raw response", e);
            }
        }
    }
    
    /**
     * Update the last processed game state
     */
    private void updateLastProcessedState() {
        try {
            // 检查当前房间
            if (AbstractDungeon.getCurrRoom() != null) {
                lastProcessedPhase = AbstractDungeon.getCurrRoom().phase;
            }
            
            // 检查玩家
            if (AbstractDungeon.player != null) {
                lastProcessedPlayerHp = AbstractDungeon.player.currentHealth;
                lastProcessedHandSize = AbstractDungeon.player.hand != null ? AbstractDungeon.player.hand.size() : 0;
            }
            
            // 检查怪物
            if (AbstractDungeon.getMonsters() != null) {
                lastProcessedMonsterCount = AbstractDungeon.getMonsters().monsters != null ? 
                    AbstractDungeon.getMonsters().monsters.size() : 0;
            }
            
            // 检查动作管理器
            if (AbstractDungeon.actionManager != null) {
                lastProcessedTurn = AbstractDungeon.actionManager.turn;
            }
            
            // 检查能量面板
            lastProcessedEnergy = EnergyPanel.totalCount;
            lastProcessedTime = System.currentTimeMillis();
            
            // 更新旧变量以保持兼容性
            lastGamePhase = getCurrentGamePhase();
            lastPlayerHealth = lastProcessedPlayerHp;
            lastEnemyCount = lastProcessedMonsterCount;
            lastHandSize = lastProcessedHandSize;
            lastTurnNumber = lastProcessedTurn;
            lastEnergy = lastProcessedEnergy;
            lastProcessTime = lastProcessedTime;
        } catch (Exception e) {
            logger.error("Error updating last processed state: " + e.getMessage(), e);
            // 设置一些默认值以避免后续的空指针异常
            lastProcessedPhase = null;
            lastProcessedPlayerHp = -1;
            lastProcessedMonsterCount = -1;
            lastProcessedHandSize = -1;
            lastProcessedTurn = -1;
            lastProcessedEnergy = -1;
            lastProcessedTime = System.currentTimeMillis();
            
            lastGamePhase = "ERROR";
            lastPlayerHealth = -1;
            lastEnemyCount = -1;
            lastHandSize = -1;
            lastTurnNumber = -1;
            lastEnergy = -1;
            lastProcessTime = System.currentTimeMillis();
        }
    }
} 