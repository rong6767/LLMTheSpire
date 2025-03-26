package llmthespire.game;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.EndTurnAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes game actions based on LLM decisions
 */
public class ActionExecutor {
    private static final Logger logger = LogManager.getLogger(ActionExecutor.class.getName());
    
    // Pattern matchers for different action types
    private static final Pattern PLAY_CARD_PATTERN = Pattern.compile("PLAY_CARD\\s*\\[(\\d+)\\]\\s*(?:\\[(\\d+)\\])?");
    private static final Pattern USE_POTION_PATTERN = Pattern.compile("USE_POTION\\s*\\[(\\d+)\\]\\s*(?:\\[(\\d+)\\])?");
    private static final Pattern END_TURN_PATTERN = Pattern.compile("END_TURN");
    private static final Pattern CHOOSE_OPTION_PATTERN = Pattern.compile("CHOOSE_OPTION\\s*\\[(\\d+)\\]");
    private static final Pattern PROCEED_ON_MAP_PATTERN = Pattern.compile("PROCEED_ON_MAP\\s*\\[(\\d+)\\]");
    private static final Pattern SELECT_CARD_PATTERN = Pattern.compile("SELECT_CARD\\s*\\[(\\d+)\\]");
    private static final Pattern TAKE_REWARD_PATTERN = Pattern.compile("TAKE_REWARD\\s*\\[(\\d+)\\]");
    private static final Pattern SHOP_PATTERNS = Pattern.compile("(BUY_CARD|BUY_RELIC|BUY_POTION|PURGE_CARD)\\s*\\[(\\d+)\\]");
    private static final Pattern REST_PATTERNS = Pattern.compile("(REST|SMITH|LIFT|DIG|RECALL)");
    private static final Pattern SKIP_PATTERN = Pattern.compile("SKIP_REWARD");
    private static final Pattern CANCEL_PATTERN = Pattern.compile("CANCEL");
    
    /**
     * Execute the action specified by the LLM
     * @param action The action string from the LLM
     * @param gameState The current game state
     * @return True if action was executed successfully
     */
    public boolean executeAction(String action, GameState gameState) {
        if (action == null || action.isEmpty()) {
            logger.warn("Empty action, nothing to execute");
            return false;
        }
        
        // Standardize action format
        action = action.trim().toUpperCase();
        
        try {
            // Match against different action patterns
            Matcher cardMatcher = PLAY_CARD_PATTERN.matcher(action);
            Matcher potionMatcher = USE_POTION_PATTERN.matcher(action);
            Matcher endTurnMatcher = END_TURN_PATTERN.matcher(action);
            Matcher optionMatcher = CHOOSE_OPTION_PATTERN.matcher(action);
            Matcher proceedMatcher = PROCEED_ON_MAP_PATTERN.matcher(action);
            Matcher selectCardMatcher = SELECT_CARD_PATTERN.matcher(action);
            Matcher takeRewardMatcher = TAKE_REWARD_PATTERN.matcher(action);
            Matcher shopMatcher = SHOP_PATTERNS.matcher(action);
            Matcher restMatcher = REST_PATTERNS.matcher(action);
            Matcher skipMatcher = SKIP_PATTERN.matcher(action);
            
            // Execute the appropriate action
            if (cardMatcher.find()) {
                return playCard(cardMatcher);
            } else if (potionMatcher.find()) {
                return usePotion(potionMatcher);
            } else if (endTurnMatcher.find()) {
                return endTurn();
            } else if (optionMatcher.find()) {
                return chooseOption(optionMatcher);
            } else if (proceedMatcher.find()) {
                return proceedOnMap(proceedMatcher);
            } else if (selectCardMatcher.find()) {
                return selectCard(selectCardMatcher);
            } else if (takeRewardMatcher.find()) {
                return takeReward(takeRewardMatcher);
            } else if (shopMatcher.find()) {
                return performShopAction(shopMatcher);
            } else if (restMatcher.find()) {
                return performRestAction(restMatcher);
            } else if (skipMatcher.find()) {
                return skipRewards();
            } else if (action.equals("LEAVE_SHOP")) {
                return leaveShop();
            } else if (action.equals("CANCEL_SELECTION")) {
                return cancelSelection();
            } else {
                logger.warn("Unknown action format: " + action);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error executing action '" + action + "': " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Play a card from the player's hand
     */
    private boolean playCard(Matcher matcher) {
        int cardIndex = Integer.parseInt(matcher.group(1));
        int targetIndex = -1;
        
        if (matcher.groupCount() > 1 && matcher.group(2) != null) {
            targetIndex = Integer.parseInt(matcher.group(2));
        }
        
        if (AbstractDungeon.player == null || AbstractDungeon.getCurrRoom() == null) {
            logger.error("Cannot play card: player or room is null");
            return false;
        }
        
        if (AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) {
            logger.error("Cannot play card: not in combat");
            return false;
        }
        
        if (cardIndex < 0 || cardIndex >= AbstractDungeon.player.hand.size()) {
            logger.error("Invalid card index: " + cardIndex + ", hand size: " + AbstractDungeon.player.hand.size());
            logHandContent();
            return false;
        }
        
        AbstractCard card = AbstractDungeon.player.hand.group.get(cardIndex);
        AbstractMonster target = null;
        
        if (targetIndex >= 0 && AbstractDungeon.getMonsters() != null) {
            List<AbstractMonster> aliveMonsters = new ArrayList<>();
            for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                if (!m.isDead && !m.isDying && !m.escaped) {
                    aliveMonsters.add(m);
                }
            }
            
            if (targetIndex < aliveMonsters.size()) {
                target = aliveMonsters.get(targetIndex);
            } else {
                logger.error("Invalid monster target index: " + targetIndex + ", alive monsters: " + aliveMonsters.size());
                logMonsterContent();
                return false;
            }
        } else if (card.target == AbstractCard.CardTarget.ENEMY || 
                   card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
            // For cards that need a target but none was specified, try to find a valid one
            List<AbstractMonster> validTargets = new ArrayList<>();
            for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                if (!m.isDead && !m.isDying && !m.escaped && card.canUse(AbstractDungeon.player, m)) {
                    validTargets.add(m);
                }
            }
            
            if (!validTargets.isEmpty()) {
                target = validTargets.get(0); // Use first valid target
                logger.info("Auto-selecting target for card: " + card.name + ", target: " + target.name);
            }
        }
        
        // Check if the card can be played
        if (card.canUse(AbstractDungeon.player, target)) {
            logger.info("Playing card: " + card.name + " (index: " + cardIndex + "), energy: " + 
                       EnergyPanel.getCurrentEnergy() + "/" + card.costForTurn);
            
            if (target != null) {
                logger.info("Targeting: " + target.name + " (HP: " + target.currentHealth + "/" + target.maxHealth + ")");
            }
            
            // Use the card
            AbstractDungeon.player.useCard(card, target, card.costForTurn);
            
            logger.info("Card played, remaining energy: " + EnergyPanel.getCurrentEnergy());
            return true;
        } else {
            logger.error("Cannot use card: " + card.name + ", cost: " + card.costForTurn + 
                       ", energy: " + EnergyPanel.getCurrentEnergy() + 
                       ", target required: " + card.target + 
                       ", target provided: " + (target != null ? target.name : "none"));
            return false;
        }
    }
    
    /**
     * Use a potion
     */
    private boolean usePotion(Matcher matcher) {
        int potionIndex = Integer.parseInt(matcher.group(1));
        int targetIndex = -1;
        
        if (matcher.groupCount() > 1 && matcher.group(2) != null) {
            targetIndex = Integer.parseInt(matcher.group(2));
        }
        
        if (AbstractDungeon.player == null) {
            logger.error("Cannot use potion: player is null");
            return false;
        }
        
        if (potionIndex < 0 || potionIndex >= AbstractDungeon.player.potions.size()) {
            logger.error("Invalid potion index: " + potionIndex);
            return false;
        }
        
        AbstractPotion potion = AbstractDungeon.player.potions.get(potionIndex);
        
        if (!potion.canUse()) {
            logger.error("Potion cannot be used: " + potion.name);
            return false;
        }
        
        AbstractMonster target = null;
        // Check if this potion needs a target (by examining its properties)
        boolean needsTarget = false;
        try {
            // Different versions of the game have different ways to check if a potion needs a target
            needsTarget = potion.targetRequired;
        } catch (Exception e) {
            // If the method doesn't exist, check if the potion has any methods with "target" in the name
            needsTarget = potion.getClass().getName().toLowerCase().contains("target");
            logger.debug("Guessing if potion requires target: " + needsTarget);
        }
        
        if (needsTarget) {
            if (targetIndex >= 0 && AbstractDungeon.getMonsters() != null) {
                List<AbstractMonster> aliveMonsters = new ArrayList<>();
                for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                    if (!m.isDead && !m.isDying && !m.escaped) {
                        aliveMonsters.add(m);
                    }
                }
                
                if (targetIndex < aliveMonsters.size()) {
                    target = aliveMonsters.get(targetIndex);
                } else {
                    logger.error("Invalid monster target index for potion: " + targetIndex);
                    return false;
                }
            } else {
                // Auto-select first alive monster as target
                for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                    if (!m.isDead && !m.isDying && !m.escaped) {
                        target = m;
                        break;
                    }
                }
                
                if (target == null) {
                    logger.error("No valid target found for potion: " + potion.name);
                    return false;
                }
            }
        }
        
        logger.info("Using potion: " + potion.name + " (index: " + potionIndex + ")");
        if (target != null) {
            logger.info("Targeting: " + target.name);
        }
        
        // Use the potion
        potion.use(target);
        
        return true;
    }
    
    /**
     * End the current turn
     */
    private boolean endTurn() {
        if (AbstractDungeon.player == null || AbstractDungeon.actionManager == null) {
            logger.error("Cannot end turn: player or action manager is null");
            return false;
        }
        
        if (AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) {
            logger.error("Cannot end turn: not in combat");
            return false;
        }
        
        logger.info("Ending turn");
        
        // End the turn with a short delay to allow animations to complete
        AbstractDungeon.actionManager.addToBottom(new WaitAction(0.1f));
        AbstractDungeon.actionManager.addToBottom(new AbstractGameAction() {
            @Override
            public void update() {
                AbstractDungeon.overlayMenu.endTurnButton.disable(true);
                this.isDone = true;
            }
        });
        
        return true;
    }
    
    /**
     * Choose an option in an event
     */
    private boolean chooseOption(Matcher matcher) {
        int optionIndex = Integer.parseInt(matcher.group(1));
        
        if (AbstractDungeon.getCurrRoom() == null || 
            AbstractDungeon.getCurrRoom().event == null) {
            logger.error("Cannot choose option: not in an event");
            return false;
        }
        
        logger.info("Choosing event option: " + optionIndex);
        
        // This is tricky because events are implemented in different ways
        // A general approach for now is to add a click action for the appropriate button
        // This would need to be expanded for different event types
        AbstractDungeon.actionManager.addToBottom(new AbstractGameAction() {
            @Override
            public void update() {
                try {
                    AbstractDungeon.getCurrRoom().event.imageEventText.optionList.get(optionIndex).pressed = true;
                } catch (Exception e) {
                    logger.error("Failed to select event option: " + e.getMessage(), e);
                }
                this.isDone = true;
            }
        });
        
        return true;
    }
    
    /**
     * Proceed to a node on the map
     */
    private boolean proceedOnMap(Matcher matcher) {
        int nodeIndex = Integer.parseInt(matcher.group(1));
        
        if (AbstractDungeon.dungeonMapScreen == null) {
            logger.error("Cannot proceed on map: dungeon map screen is null");
            return false;
        }
        
        logger.info("Proceeding to map node: " + nodeIndex);
        
        // This needs map screen interaction which is complex
        // A general approach would be to click on available nodes
        // This is a placeholder that would need actual implementation
        return false;
    }
    
    /**
     * Select a card from a grid or similar selection screen
     */
    private boolean selectCard(Matcher matcher) {
        int cardIndex = Integer.parseInt(matcher.group(1));
        
        if (AbstractDungeon.isScreenUp) {
            if (AbstractDungeon.gridSelectScreen != null && AbstractDungeon.gridSelectScreen.selectedCards != null) {
                logger.info("Selecting card from grid: " + cardIndex);
                
                // Would need to handle grid card selection
                // This is a placeholder
                return false;
            } else if (AbstractDungeon.handCardSelectScreen != null && AbstractDungeon.handCardSelectScreen.selectedCards != null) {
                logger.info("Selecting card from hand selection: " + cardIndex);
                
                // Would need to handle hand card selection
                // This is a placeholder
                return false;
            }
        }
        
        logger.error("No card selection screen is open");
        return false;
    }
    
    /**
     * Take a reward after combat
     */
    private boolean takeReward(Matcher matcher) {
        int rewardIndex = Integer.parseInt(matcher.group(1));
        
        if (AbstractDungeon.getCurrRoom() == null || 
            AbstractDungeon.getCurrRoom().rewards == null || 
            AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMPLETE) {
            logger.error("Cannot take reward: not in reward phase");
            return false;
        }
        
        if (rewardIndex < 0 || rewardIndex >= AbstractDungeon.getCurrRoom().rewards.size()) {
            logger.error("Invalid reward index: " + rewardIndex);
            return false;
        }
        
        logger.info("Taking reward: " + rewardIndex);
        
        // 使用AbstractGameAction来确保动作在游戏线程中执行
        AbstractDungeon.actionManager.addToBottom(new AbstractGameAction() {
            @Override
            public void update() {
                try {
                    AbstractDungeon.getCurrRoom().rewards.get(rewardIndex).claimReward();
                } catch (Exception e) {
                    logger.error("Error claiming reward: " + e.getMessage(), e);
                }
                this.isDone = true;
            }
        });
        
        return true;
    }
    
    /**
     * Perform an action in the shop
     */
    private boolean performShopAction(Matcher matcher) {
        String actionType = matcher.group(1);
        int index = Integer.parseInt(matcher.group(2));
        
        if (AbstractDungeon.shopScreen == null) {
            logger.error("Cannot perform shop action: shop screen is null");
            return false;
        }
        
        logger.info("Performing shop action: " + actionType + " " + index);
        
        // This would need to interact with the shop screen
        // This is a placeholder
        return false;
    }
    
    /**
     * Perform an action at a rest site
     */
    private boolean performRestAction(Matcher matcher) {
        String actionType = matcher.group(1);
        
        if (AbstractDungeon.getCurrRoom() == null || 
            AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.INCOMPLETE || 
            !AbstractDungeon.getCurrRoom().event.getClass().getSimpleName().contains("Rest")) {
            logger.error("Cannot perform rest action: not at a rest site");
            return false;
        }
        
        logger.info("Performing rest action: " + actionType);
        
        // This would need to interact with the rest buttons
        // This is a placeholder
        return false;
    }
    
    /**
     * Skip all current rewards
     */
    private boolean skipRewards() {
        if (AbstractDungeon.getCurrRoom() == null || 
            AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMPLETE) {
            logger.error("Cannot skip rewards: not in reward phase");
            return false;
        }
        
        logger.info("Skipping rewards");
        
        // 使用AbstractGameAction来确保动作在游戏线程中执行
        AbstractDungeon.actionManager.addToBottom(new AbstractGameAction() {
            @Override
            public void update() {
                try {
                    // 关闭奖励屏幕，进入地图
                    AbstractDungeon.closeCurrentScreen();
                    
                    // 额外检查，确保屏幕被关闭
                    if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
                        AbstractDungeon.closeCurrentScreen();
                    }
                } catch (Exception e) {
                    logger.error("Error skipping rewards: " + e.getMessage(), e);
                }
                this.isDone = true;
            }
        });
        
        return true;
    }
    
    /**
     * Leave the shop
     */
    private boolean leaveShop() {
        if (AbstractDungeon.shopScreen == null) {
            logger.error("Cannot leave shop: shop screen is null");
            return false;
        }
        
        logger.info("Leaving shop");
        
        AbstractDungeon.closeCurrentScreen();
        
        return true;
    }
    
    /**
     * Cancel a current selection (grid, hand, etc.)
     */
    private boolean cancelSelection() {
        if (!AbstractDungeon.isScreenUp) {
            logger.error("Cannot cancel selection: no screen is open");
            return false;
        }
        
        logger.info("Canceling selection");
        
        AbstractDungeon.closeCurrentScreen();
        
        return true;
    }
    
    /**
     * Log the contents of the player's hand for debugging
     */
    private void logHandContent() {
        if (AbstractDungeon.player == null) {
            return;
        }
        
        StringBuilder sb = new StringBuilder("Hand contents: ");
        for (int i = 0; i < AbstractDungeon.player.hand.size(); i++) {
            AbstractCard card = AbstractDungeon.player.hand.group.get(i);
            sb.append(i).append(": ").append(card.name)
              .append(" (cost: ").append(card.costForTurn)
              .append(", type: ").append(card.type)
              .append(", target: ").append(card.target)
              .append(")");
            
            if (i < AbstractDungeon.player.hand.size() - 1) {
                sb.append(", ");
            }
        }
        
        logger.info(sb.toString());
    }
    
    /**
     * Log the contents of the current monsters for debugging
     */
    private void logMonsterContent() {
        if (AbstractDungeon.getMonsters() == null) {
            return;
        }
        
        StringBuilder sb = new StringBuilder("Monster contents: ");
        List<AbstractMonster> aliveMonsters = new ArrayList<>();
        for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
            if (!m.isDead && !m.isDying && !m.escaped) {
                aliveMonsters.add(m);
            }
        }
        
        for (int i = 0; i < aliveMonsters.size(); i++) {
            AbstractMonster monster = aliveMonsters.get(i);
            sb.append(i).append(": ").append(monster.name)
              .append(" (HP: ").append(monster.currentHealth).append("/").append(monster.maxHealth)
              .append(", intent: ").append(monster.getIntentDmg())
              .append(")");
            
            if (i < aliveMonsters.size() - 1) {
                sb.append(", ");
            }
        }
        
        logger.info(sb.toString());
    }
} 