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
import llmthespire.game.CommandParser.Command;
import llmthespire.game.CommandParser.CommandType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes game actions based on LLM decisions
 */
public class ActionExecutor {
    private static final Logger logger = LogManager.getLogger(ActionExecutor.class.getName());
    
    /**
     * Execute the action specified by the LLM
     * @param actionString The action string from the LLM
     * @param gameState The current game state
     * @return True if action was executed successfully
     */
    public boolean executeAction(String actionString, GameState gameState) {
        if (actionString == null || actionString.isEmpty()) {
            logger.warn("Empty action, nothing to execute");
            return false;
        }
        
        logger.debug("Executing action: " + actionString);
        
        try {
            // Parse the command using the CommandParser
            Command command = CommandParser.parse(actionString);
            
            // Handle unknown commands
            if (command.getType() == CommandType.UNKNOWN) {
                logger.warn("Unknown command: " + actionString);
                return false;
            }
            
            // Execute the appropriate action based on command type
            return executeCommand(command);
        } catch (Exception e) {
            logger.error("Error executing action '" + actionString + "': " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Execute a parsed command
     * @param command The command to execute
     * @return True if the command was executed successfully
     */
    private boolean executeCommand(Command command) {
        CommandType type = command.getType();
        
        switch (type) {
            case PLAY_CARD:
                return playCard(command.getParameter(0), command.getParameterCount() > 1 ? command.getParameter(1) : -1);
            case USE_POTION:
                return usePotion(command.getParameter(0), command.getParameterCount() > 1 ? command.getParameter(1) : -1);
            case END_TURN:
                return endTurn();
            case CHOOSE_OPTION:
                return chooseOption(command.getParameter(0));
            case PROCEED_ON_MAP:
                return proceedOnMap(command.getParameter(0));
            case SELECT_CARD:
                return selectCard(command.getParameter(0));
            case TAKE_REWARD:
                return takeReward(command.getParameter(0));
            case BUY_CARD:
            case BUY_RELIC:
            case BUY_POTION:
            case PURGE_CARD:
                return performShopAction(type.name(), command.getParameter(0));
            case REST:
            case SMITH:
            case LIFT:
            case DIG:
            case RECALL:
                return performRestAction(type.name());
            case SKIP_REWARD:
                return skipRewards();
            case LEAVE_SHOP:
                return leaveShop();
            case CANCEL:
                return cancelSelection();
            default:
                logger.warn("Unsupported command type: " + type);
                return false;
        }
    }
    
    /**
     * Play a card from the player's hand
     */
    private boolean playCard(int cardIndex, int targetIndex) {
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
    private boolean usePotion(int potionIndex, int targetIndex) {
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
    private boolean chooseOption(int optionIndex) {
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
    private boolean proceedOnMap(int nodeIndex) {
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
    private boolean selectCard(int cardIndex) {
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
    private boolean takeReward(int rewardIndex) {
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
    private boolean performShopAction(String actionType, int index) {
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
    private boolean performRestAction(String actionType) {
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