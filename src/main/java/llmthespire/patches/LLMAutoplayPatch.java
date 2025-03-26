package llmthespire.patches;

import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.TopPanel;
import llmthespire.LLMAutoplayController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/**
 * Patches into the game to implement LLM autoplay functionality
 */
public class LLMAutoplayPatch {
    private static final Logger logger = LogManager.getLogger(LLMAutoplayPatch.class.getName());
    
    // Button for toggling autoplay
    private static Hitbox autoplayButton;
    private static boolean autoplayEnabled = false;
    private static float pulseTimer = 0f; // Timer for pulsing effect
    private static final float PULSE_PERIOD = 1.0f; // One full pulse per second
    private static final float BUTTON_X = Settings.WIDTH * 0.5f - 75.0f * Settings.scale; // Center position
    private static final float BUTTON_Y = Settings.HEIGHT - 60.0f * Settings.scale; // Placed at top, but lower for visibility
    private static final float BUTTON_WIDTH = 150.0f * Settings.scale; // Wider button
    private static final float BUTTON_HEIGHT = 45.0f * Settings.scale; // Taller button
    private static final Color BUTTON_COLOR_ENABLED = new Color(0.2f, 0.9f, 0.2f, 0.9f); // More opaque
    private static final Color BUTTON_COLOR_DISABLED = new Color(0.7f, 0.3f, 0.3f, 0.8f); // More distinct disabled color
    private static final String TOOLTIP_TITLE = "LLM AI Autoplay";
    private static final String TOOLTIP_BODY = "Toggle AI assistance for playing the game automatically. When enabled, the AI will make decisions and execute actions for you.";
    
    /**
     * Initialize the autoplay button
     */
    static {
        autoplayButton = new Hitbox(BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        autoplayEnabled = false;
    }
    
    /**
     * Patch into TopPanel.update to update our button
     */
    @SpirePatch(clz = TopPanel.class, method = "update")
    public static class TopPanelUpdatePatch {
        @SpirePostfixPatch
        public static void Postfix(TopPanel __instance) {
            try {
                // Only show when in a dungeon
                if (CardCrawlGame.isInARun() && AbstractDungeon.player != null) {
                    autoplayButton.update();
                    
                    // Update pulse timer if autoplay is enabled
                    if (autoplayEnabled) {
                        pulseTimer += com.badlogic.gdx.Gdx.graphics.getDeltaTime();
                        if (pulseTimer > PULSE_PERIOD) {
                            pulseTimer -= PULSE_PERIOD;
                        }
                    }
                    
                    if (autoplayButton.hovered && InputHelper.justClickedLeft) {
                        autoplayButton.clickStarted = true;
                    }
                    
                    if (autoplayButton.clicked) {
                        // Toggle the autoplay state
                        autoplayEnabled = LLMAutoplayController.getInstance().toggleEnabled();
                        autoplayButton.clicked = false;
                    }
                }
            } catch (Exception e) {
                logger.error("Error in TopPanelUpdatePatch: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Patch into TopPanel.render to draw our button
     */
    @SpirePatch(clz = TopPanel.class, method = "renderDungeonInfo")
    public static class TopPanelRenderPatch {
        @SpirePostfixPatch
        public static void Postfix(TopPanel __instance, SpriteBatch sb) {
            try {
                // Only show when in a dungeon
                if (CardCrawlGame.isInARun() && AbstractDungeon.player != null) {
                    // Draw button background
                    Color buttonColor = autoplayEnabled ? BUTTON_COLOR_ENABLED : BUTTON_COLOR_DISABLED;
                    
                    // Apply pulsing effect to enabled button
                    if (autoplayEnabled) {
                        // Calculate pulse brightness based on sine wave
                        float pulseIntensity = (float) (0.7f + 0.3f * Math.sin(pulseTimer * 2 * Math.PI));
                        buttonColor = buttonColor.cpy().mul(pulseIntensity, pulseIntensity, pulseIntensity, 1.0f);
                    }
                    
                    if (autoplayButton.hovered) {
                        buttonColor = buttonColor.cpy();
                        buttonColor.a = 1.0f;
                    }
                    
                    // Draw button background with border
                    sb.setColor(buttonColor);
                    sb.draw(ImageMaster.WHITE_SQUARE_IMG, 
                            autoplayButton.x, autoplayButton.y, 
                            autoplayButton.width, autoplayButton.height);
                    
                    // Draw a border around the button
                    sb.setColor(Color.WHITE);
                    float borderThickness = 2.0f * Settings.scale;
                    sb.draw(ImageMaster.WHITE_SQUARE_IMG, 
                            autoplayButton.x - borderThickness, autoplayButton.y - borderThickness, 
                            autoplayButton.width + 2 * borderThickness, borderThickness); // Bottom
                    sb.draw(ImageMaster.WHITE_SQUARE_IMG, 
                            autoplayButton.x - borderThickness, autoplayButton.y + autoplayButton.height, 
                            autoplayButton.width + 2 * borderThickness, borderThickness); // Top
                    sb.draw(ImageMaster.WHITE_SQUARE_IMG, 
                            autoplayButton.x - borderThickness, autoplayButton.y, 
                            borderThickness, autoplayButton.height); // Left
                    sb.draw(ImageMaster.WHITE_SQUARE_IMG, 
                            autoplayButton.x + autoplayButton.width, autoplayButton.y, 
                            borderThickness, autoplayButton.height); // Right
                    
                    // Draw button text
                    FontHelper.renderFontCentered(sb, FontHelper.topPanelInfoFont,
                            autoplayEnabled ? "AI PLAYING" : "AI DISABLED",
                            autoplayButton.x + autoplayButton.width / 2.0f,
                            autoplayButton.y + autoplayButton.height / 2.0f,
                            Color.WHITE);
                    
                    // Show tooltip on hover
                    if (autoplayButton.hovered) {
                        TipHelper.renderGenericTip(autoplayButton.x, autoplayButton.y - 50.0f * Settings.scale,
                                TOOLTIP_TITLE, TOOLTIP_BODY);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in TopPanelRenderPatch: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Patch into AbstractDungeon.update to handle our controller update
     */
    @SpirePatch(clz = AbstractDungeon.class, method = "update")
    public static class DungeonUpdatePatch {
        @SpirePostfixPatch
        public static void Postfix(AbstractDungeon __instance) {
            try {
                // 强制启用AI自动游玩功能
                if (!autoplayEnabled && AbstractDungeon.player != null && AbstractDungeon.getCurrRoom() != null) {
                    logger.info("Auto-enabling AI autoplay feature");
                    autoplayEnabled = LLMAutoplayController.getInstance().toggleEnabled();
                }
                
                // Update the controller if autoplay is enabled
                if (autoplayEnabled && 
                    AbstractDungeon.player != null && 
                    AbstractDungeon.getCurrRoom() != null) {
                    
                    try {
                        LLMAutoplayController.getInstance().update();
                    } catch (Exception e) {
                        logger.error("Critical error in LLMAutoplayController.update()", e);
                        
                        // 在发生严重错误时自动禁用自动游玩功能，防止游戏崩溃
                        if (e.getMessage() != null && e.getMessage().contains("Critical")) {
                            logger.error("Disabling autoplay due to critical error");
                            autoplayEnabled = false;
                            // 通知玩家自动游玩功能已被禁用
                            com.megacrit.cardcrawl.core.CardCrawlGame.sound.play("POWER_ENTANGLED");
                        }
                    } catch (Error err) {
                        // 处理严重错误如OutOfMemoryError或StackOverflowError
                        logger.error("Fatal error in LLMAutoplayController.update()", err);
                        logger.error("Disabling autoplay due to fatal error");
                        autoplayEnabled = false;
                        // 通知玩家自动游玩功能已被禁用
                        com.megacrit.cardcrawl.core.CardCrawlGame.sound.play("POWER_ENTANGLED");
                    }
                }
            } catch (Exception e) {
                logger.error("Error in DungeonUpdatePatch: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Patch into AbstractRoom.update to handle combat updates
     */
    @SpirePatch(clz = AbstractRoom.class, method = "update")
    public static class RoomUpdatePatch {
        @SpirePostfixPatch
        public static void Postfix(AbstractRoom __instance) {
            try {
                // Additional room-specific updates could go here
            } catch (Exception e) {
                logger.error("Error in RoomUpdatePatch: " + e.getMessage(), e);
            }
        }
    }
} 