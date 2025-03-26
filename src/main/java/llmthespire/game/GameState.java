package llmthespire.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class representing the game state, used to provide data to LLM
 */
public class GameState {
    private static final Logger logger = LogManager.getLogger(GameState.class.getName());
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // Stage information
    @Expose public GameStageType stage;
    
    // Basic game information
    @Expose public int currentHp;
    @Expose public int maxHp;
    @Expose public int gold;
    @Expose public int floor;
    @Expose public String playerClass;
    
    // Available actions
    @Expose public List<String> availableActions = new ArrayList<>();
    
    // Other detailed information, which varies depending on the game stage
    @Expose public StageSpecificInfo stageInfo;
    
    // Player deck, potions and relics information
    @Expose public List<CardInfo> drawPile = new ArrayList<>();
    @Expose public List<CardInfo> hand = new ArrayList<>();
    @Expose public List<CardInfo> discardPile = new ArrayList<>();
    @Expose public List<CardInfo> exhaustPile = new ArrayList<>();
    @Expose public List<PotionInfo> potions = new ArrayList<>();
    @Expose public List<RelicInfo> relics = new ArrayList<>();
    
    /**
     * Create game state
     */
    public GameState() {
        updateBasicInfo();
        determineGameStage();
        populateStageSpecificInfo();
        generateAvailableActions();
    }
    
    /**
     * Update basic game information
     */
    private void updateBasicInfo() {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) return;
        
        // Basic info
        currentHp = player.currentHealth;
        maxHp = player.maxHealth;
        gold = player.gold;
        floor = AbstractDungeon.floorNum;
        playerClass = player.getClass().getSimpleName();
        
        // Cards
        updateCardPiles();
        
        // Potions
        updatePotions();
        
        // Relics
        updateRelics();
    }
    
    /**
     * Update card pile information
     */
    private void updateCardPiles() {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) return;
        
        // Hand
        hand.clear();
        for (AbstractCard card : player.hand.group) {
            hand.add(createCardInfo(card));
        }
        
        // Draw pile
        drawPile.clear();
        for (AbstractCard card : player.drawPile.group) {
            drawPile.add(createCardInfo(card));
        }
        
        // Discard pile
        discardPile.clear();
        for (AbstractCard card : player.discardPile.group) {
            discardPile.add(createCardInfo(card));
        }
        
        // Exhaust pile
        exhaustPile.clear();
        for (AbstractCard card : player.exhaustPile.group) {
            exhaustPile.add(createCardInfo(card));
        }
    }
    
    /**
     * Create CardInfo from AbstractCard
     */
    private CardInfo createCardInfo(AbstractCard card) {
        CardInfo cardInfo = new CardInfo();
        cardInfo.id = card.cardID;
        cardInfo.name = card.name;
        cardInfo.type = card.type.toString();
        cardInfo.rarity = card.rarity.toString();
        cardInfo.description = card.rawDescription;
        cardInfo.cost = card.cost;
        cardInfo.upgraded = card.upgraded;
        cardInfo.canUse = card.canUse(AbstractDungeon.player, null);
        return cardInfo;
    }

    private PotionInfo createPotionInfo(AbstractPotion potion) {
        PotionInfo potionInfo = new PotionInfo();
        potionInfo.name = potion.name;
        potionInfo.description = potion.description;
        potionInfo.id = potion.ID;
        potionInfo.rarity = potion.rarity.toString();
        return potionInfo;
    }

    private RelicInfo createRelicInfo(AbstractRelic relic) {
        RelicInfo relicInfo = new RelicInfo();
        relicInfo.name = relic.name;
        relicInfo.description = relic.description;
        relicInfo.id = relic.relicId;
        return relicInfo;
    }
    
    /**
     * Update potion information
     */
    private void updatePotions() {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) return;
        
        potions.clear();
        for (AbstractPotion potion : player.potions) {
            if (potion != null) {
                PotionInfo potionInfo = new PotionInfo();
                potionInfo.id = potion.ID;
                potionInfo.name = potion.name;
                potionInfo.description = potion.description;
                potionInfo.rarity = potion.rarity.toString();
                potions.add(potionInfo);
            }
        }
    }
    
    /**
     * Update relic information
     */
    private void updateRelics() {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) return;
        
        relics.clear();
        for (AbstractRelic relic : player.relics) {
            RelicInfo relicInfo = new RelicInfo();
            relicInfo.id = relic.relicId;
            relicInfo.name = relic.name;
            relicInfo.description = relic.description;
            relicInfo.tier = relic.tier.toString();
            if (relic.counter >= 0) {
                relicInfo.counter = relic.counter;
            }
            relics.add(relicInfo);
        }
    }
    
    /**
     * Determine current game stage
     */
    private void determineGameStage() {
        if (AbstractDungeon.getCurrRoom() == null) {
            stage = GameStageType.MAP;
            return;
        }
        
        AbstractRoom currentRoom = AbstractDungeon.getCurrRoom();
        
        // Check current screen
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
            stage = GameStageType.MAP;
        } else if (currentRoom instanceof MonsterRoom && !currentRoom.isBattleOver) {
            stage = GameStageType.BATTLE;
        } else if (currentRoom instanceof RestRoom) {
            stage = GameStageType.CAMPFIRE;
        } else if (currentRoom instanceof ShopRoom) {
            stage = GameStageType.SHOP;
        } else if (currentRoom instanceof TreasureRoom) {
            stage = GameStageType.CHEST;
        } else if (currentRoom instanceof EventRoom) {
            stage = GameStageType.EVENT;
        }else if(currentRoom instanceof NeowRoom){
            stage = GameStageType.NEOW;
        } else if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
            stage = GameStageType.REWARD;
        } else if (AbstractDungeon.gridSelectScreen != null && AbstractDungeon.gridSelectScreen.selectedCards != null) {
            stage = GameStageType.CARD_SELECT;
        } else {
            // Default to map stage
            stage = GameStageType.MAP;
        }
    }
    
    /**
     * Populate stage-specific information based on current stage
     */
    private void populateStageSpecificInfo() {
        stageInfo = populateStageInfo();
    }
    
    /**
     * Populate stage-specific information based on current stage
     */
    private StageSpecificInfo populateStageInfo() {
        switch (stage) {
            case BATTLE:
                return populateBattleInfo();
            case MAP:
                return populateMapInfo();
            case CAMPFIRE:
                return populateRestSiteInfo();
            case SHOP:
                return populateShopInfo();
            case CHEST:
                return populateChestInfo();
            case EVENT:
                return populateEventInfo();
            case REWARD:
                return populateRewardInfo();
            case CARD_SELECT:
                return populateCardSelectInfo();
            case NEOW:
                return populateNeowInfo();
            default:
                return null;
        }
    }
    
    /**
     * Populate battle stage information
     */
    private BattleStageInfo populateBattleInfo() {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) return null;
        
        BattleStageInfo info = new BattleStageInfo();
        info.energy = EnergyPanel.totalCount;
        info.turn = AbstractDungeon.actionManager.turn;
        
        // Monster information
        for (AbstractMonster monster : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (monster != null && !monster.isDead && !monster.isDying) {
                CustomMonsterInfo monsterInfo = new CustomMonsterInfo();
                monsterInfo.name = monster.name;
                monsterInfo.currentHp = monster.currentHealth;
                monsterInfo.maxHp = monster.maxHealth;
                monsterInfo.intent = monster.intent.name();
                
                // Add all monster powers
                for (com.megacrit.cardcrawl.powers.AbstractPower power : monster.powers) {
                    String powerDesc = power.name + ": " + power.amount;
                    if (power.type == com.megacrit.cardcrawl.powers.AbstractPower.PowerType.DEBUFF) {
                        powerDesc += " (Debuff)";
                    } else if (power.type == com.megacrit.cardcrawl.powers.AbstractPower.PowerType.BUFF) {
                        powerDesc += " (Buff)";
                    }
                    monsterInfo.powers.add(powerDesc);
                }
                
                info.monsters.add(monsterInfo);
            }
        }
        
        return info;
    }
    
    /**
     * Populate map stage information
     */
    private MapStageInfo populateMapInfo() {
        MapStageInfo info = new MapStageInfo();
        info.currentPosition = AbstractDungeon.floorNum;
        info.act = AbstractDungeon.actNum;
        
        // Simplified map path processing
        info.availablePaths = new ArrayList<>();
        PathInfo pathInfo = new PathInfo();
        pathInfo.pathId = 0;
        pathInfo.rooms = new ArrayList<>();
        
        // Add a basic room info
        RoomInfo roomInfo = new RoomInfo();
        roomInfo.type = "UNKNOWN";
        roomInfo.floor = AbstractDungeon.floorNum + 1;
        pathInfo.rooms.add(roomInfo);
        
        info.availablePaths.add(pathInfo);
        
        return info;
    }
    
    /**
     * Look ahead at rooms on the path - simplified implementation
     */
    private void lookAheadPath(MapRoomNode startNode, List<RoomInfo> rooms, int depth) {
        if (depth <= 0 || startNode == null) return;
        
        // Add current node info
        String roomType = getRoomTypeString(startNode);
        RoomInfo roomInfo = new RoomInfo();
        roomInfo.type = roomType;
        roomInfo.floor = startNode.y;
        rooms.add(roomInfo);
    }
    
    /**
     * Get string representation of room type
     */
    private String getRoomTypeString(MapRoomNode node) {
        if (node.room == null) return "UNKNOWN";
        
        if (node.room instanceof MonsterRoom) {
            if (node.room.getClass().getSimpleName().equals("EliteMonsterRoom")) {
                return "ELITE";
            } else if (node.room.getClass().getSimpleName().equals("BossRoom")) {
                return "BOSS";
            } else {
                return "MONSTER";
            }
        } else if (node.room instanceof RestRoom) {
            return "REST";
        } else if (node.room instanceof ShopRoom) {
            return "SHOP";
        } else if (node.room instanceof TreasureRoom) {
            return "TREASURE";
        } else if (node.room instanceof EventRoom) {
            return "EVENT";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * Populate rest site stage information
     */
    private RestSiteStageInfo populateRestSiteInfo() {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) return null;
        
        RestSiteStageInfo info = new RestSiteStageInfo();
        
        // Determine if options are available
        info.canRest = player.currentHealth < player.maxHealth;
        info.canSmith = player.masterDeck.getUpgradableCards().size() > 0;
        
        // List of upgradable cards
        for (AbstractCard card : player.masterDeck.getUpgradableCards().group) {
            info.upgradableCards.add(createCardInfo(card));
        }
        
        // Other options availability depends on specific relics and game state
        info.canLift = player.hasRelic("Girya");
        info.canDig = player.hasRelic("Shovel");
        info.canRecall = player.hasRelic("Dream Catcher");
        
        return info;
    }
    
    /**
     * Populate shop stage information
     */
    private ShopStageInfo populateShopInfo() {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) return null;
        
        ShopStageInfo info = new ShopStageInfo();
        
        // Direct access to shop items requires additional processing
        // This is a simplified implementation
        
        info.canPurge = true;  // Shop generally allows card purging
        info.purgeCost = 75;   // Base purge cost, actual cost increases with each purge
        
        return info;
    }
    
    /**
     * Populate chest stage information
     */
    private StageSpecificInfo populateChestInfo() {
        // Chest stage usually just provides rewards, then enters reward stage
        return null;
    }
    
    /**
     * Populate event stage information
     */
    private EventStageInfo populateEventInfo() {
        EventStageInfo info = new EventStageInfo();
        
        // Get current event info, need to access current room's event object
        // This is a simplified version
        
        if (AbstractDungeon.getCurrRoom() instanceof EventRoom && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.EVENT) {
            EventRoom room = (EventRoom) AbstractDungeon.getCurrRoom();
            if (room.event != null) {
                info.eventName = room.event.NAME;
                info.options = Arrays.asList(room.event.OPTIONS);
            }
        }
        
        return info;
    }
    
    /**
     * Populate reward stage information
     */
    private RewardStageInfo populateRewardInfo() {
        RewardStageInfo info = new RewardStageInfo();

        return info;
    }
    
    /**
     * Populate card selection stage information
     */
    private CardSelectStageInfo populateCardSelectInfo() {
        CardSelectStageInfo info = new CardSelectStageInfo();
        
        if ((AbstractDungeon.gridSelectScreen != null && AbstractDungeon.gridSelectScreen.selectedCards != null) || AbstractDungeon.cardRewardScreen != null) {
            // Grid selection mode
            info.selectionType = "GRID_SELECT";
            for(AbstractCard card : AbstractDungeon.cardRewardScreen.rewardGroup) {
                info.selectableCards.add(createCardInfo(card));
            }
        } else {
            // Simplified hand selection handling
            info.selectionType = "HAND_SELECT";
            if (AbstractDungeon.player != null) {
                for (AbstractCard card : AbstractDungeon.player.hand.group) {
                    info.selectableCards.add(createCardInfo(card));
                }
            }
        }
        
        return info;
    }
    
    /**
     * Populate Neow event information
     */
    private NeowStageInfo populateNeowInfo() {
        NeowStageInfo info = new NeowStageInfo();
        
        if (AbstractDungeon.getCurrRoom() instanceof EventRoom) {
            EventRoom room = (EventRoom) AbstractDungeon.getCurrRoom();
            if (room.event != null && room.event.getClass().getSimpleName().equals("NeowEvent")) {
                try {
                    // Get the event options
                    if (room.event.imageEventText != null && room.event.imageEventText.optionList != null) {
                        for (int i = 0; i < room.event.imageEventText.optionList.size(); i++) {
                            Object option = room.event.imageEventText.optionList.get(i);
                            String optionText = (String) option.getClass().getMethod("getText").invoke(option);
                            info.options.add(optionText);
                        }
                    }
                    
                    // Get the event description
                    if (room.event.imageEventText != null) {
                        info.description = (String) room.event.imageEventText.getClass().getMethod("getBodyText").invoke(room.event.imageEventText);
                    }
                } catch (Exception e) {
                    logger.error("Failed to get Neow event text: " + e.getMessage(), e);
                }
            }
        }
        
        return info;
    }
    
    /**
     * Generate available actions for the current stage
     */
    private void generateAvailableActions() {
        availableActions.clear();
        
        switch (stage) {
            case BATTLE:
                generateBattleActions();
                break;
            case MAP:
                generateMapActions();
                break;
            case CAMPFIRE:
                generateRestSiteActions();
                break;
            case SHOP:
                generateShopActions();
                break;
            case CHEST:
                generateChestActions();
                break;
            case EVENT:
                generateEventActions();
                break;
            case REWARD:
                generateRewardActions();
                break;
            case CARD_SELECT:
                generateCardSelectActions();
                break;
        }
    }
    
    /**
     * Generate battle stage available actions
     */
    private void generateBattleActions() {
        // Add some basic battle actions
        availableActions.add("END_TURN");
        availableActions.add("PLAY_CARD [index]");
        availableActions.add("USE_POTION [index]");
    }
    
    /**
     * Generate map stage available actions
     */
    private void generateMapActions() {
        // Add map actions
        availableActions.add("CHOOSE_PATH [direction]");
    }
    
    /**
     * Generate rest site stage available actions
     */
    private void generateRestSiteActions() {
        // Add rest site actions
        availableActions.add("REST");
        availableActions.add("SMITH");
        
        AbstractPlayer player = AbstractDungeon.player;
        if (player != null) {
            if (player.hasRelic("Girya")) {
                availableActions.add("LIFT");
            }
            if (player.hasRelic("Shovel")) {
                availableActions.add("DIG");
            }
            if (player.hasRelic("Dream Catcher")) {
                availableActions.add("DREAM");
            }
        }
    }
    
    /**
     * Generate shop stage available actions
     */
    private void generateShopActions() {
        // Add shop actions
        availableActions.add("BUY_CARD [index]");
        availableActions.add("BUY_RELIC [index]");
        availableActions.add("BUY_POTION [index]");
        availableActions.add("PURGE_CARD");
        availableActions.add("LEAVE");
    }
    
    /**
     * Generate chest stage available actions
     */
    private void generateChestActions() {
        // Add chest actions
        availableActions.add("OPEN");
        availableActions.add("LEAVE");
    }
    
    /**
     * Generate event stage available actions
     */
    private void generateEventActions() {
        // Add basic event actions
        availableActions.add("CHOOSE [option]");
    }
    
    /**
     * Generate reward stage available actions
     */
    private void generateRewardActions() {
        if (AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().rewards != null) {
            // Reward processing requires additional logic, this is simplified
            ArrayList<RewardItem> rewardItems =  AbstractDungeon.getCurrRoom().rewards;
            for(RewardItem item : rewardItems) {
                item.claimReward();
            }
        }
        availableActions.add("SKIP");
    }
    
    /**
     * Generate card selection stage available actions
     */
    private void generateCardSelectActions() {
        // Add card selection actions
        availableActions.add("SELECT_CARD [index]");
        availableActions.add("CANCEL");
    }
    
    @Override
    public String toString() {
        return gson.toJson(this);
    }
    
    /**
     * Base class for stage-specific information
     */
    public abstract static class StageSpecificInfo {
        // Base class for unifying information across different stages
    }
    
    /**
     * Battle stage specific information
     */
    public static class BattleStageInfo extends StageSpecificInfo {
        @Expose public int energy;
        @Expose public int turn;
        @Expose public List<CustomMonsterInfo> monsters = new ArrayList<>();
        @Expose public List<String> intents = new ArrayList<>();
        @Expose public List<String> powers = new ArrayList<>();
    }
    
    /**
     * Monster information
     */
    public static class CustomMonsterInfo {
        @Expose public String name;
        @Expose public int currentHp;
        @Expose public int maxHp;
        @Expose public String intent;
        @Expose public List<String> powers = new ArrayList<>();
    }
    
    /**
     * Map selection stage specific information
     */
    public static class MapStageInfo extends StageSpecificInfo {
        @Expose public int currentPosition;
        @Expose public int act;
        @Expose public List<PathInfo> availablePaths = new ArrayList<>();
    }
    
    /**
     * Path information on the map
     */
    public static class PathInfo {
        @Expose public int pathId;
        @Expose public List<RoomInfo> rooms = new ArrayList<>();
    }
    
    /**
     * Room information on the map
     */
    public static class RoomInfo {
        @Expose public String type;  // "MONSTER", "ELITE", "REST", "SHOP", "EVENT", "BOSS"
        @Expose public int floor;
    }
    
    /**
     * Rest site stage specific information
     */
    public static class RestSiteStageInfo extends StageSpecificInfo {
        @Expose public boolean canRest;
        @Expose public boolean canSmith;
        @Expose public boolean canLift;
        @Expose public boolean canDig;
        @Expose public boolean canRecall;
        @Expose public List<CardInfo> upgradableCards = new ArrayList<>();
    }
    
    /**
     * Shop stage specific information
     */
    public static class ShopStageInfo extends StageSpecificInfo {
        @Expose public List<ShopItemInfo> cards = new ArrayList<>();
        @Expose public List<ShopItemInfo> relics = new ArrayList<>();
        @Expose public List<ShopItemInfo> potions = new ArrayList<>();
        @Expose public boolean canPurge;
        @Expose public int purgeCost;
    }
    
    /**
     * Shop item information
     */
    public static class ShopItemInfo {
        @Expose public String name;
        @Expose public String description;
        @Expose public int cost;
        @Expose public boolean canAfford;
    }
    
    /**
     * Event stage specific information
     */
    public static class EventStageInfo extends StageSpecificInfo {
        @Expose public String eventName;
        @Expose public String currentDescription;
        @Expose public List<String> options = new ArrayList<>();
    }
    
    /**
     * Reward selection stage specific information
     */
    public static class RewardStageInfo extends StageSpecificInfo {
        @Expose public List<CardInfo> cardRewards = new ArrayList<>();
        @Expose public List<RelicInfo> relicRewards = new ArrayList<>();
        @Expose public List<PotionInfo> potionRewards = new ArrayList<>();
        @Expose public int goldReward;
    }
    
    /**
     * Card selection stage specific information
     */
    public static class CardSelectStageInfo extends StageSpecificInfo {
        @Expose public String selectionType; // "UPGRADE", "TRANSFORM", "REMOVE", "DUPLICATE"
        @Expose public List<CardInfo> selectableCards = new ArrayList<>();
    }
    
    /**
     * Card information
     */
    public static class CardInfo {
        @Expose public String id;
        @Expose public String name;
        @Expose public String type;
        @Expose public String rarity;
        @Expose public String description;
        @Expose public int cost;
        @Expose public boolean upgraded;
        @Expose public boolean canUse;
    }
    
    /**
     * Potion information
     */
    public static class PotionInfo {
        @Expose public String id;
        @Expose public String name;
        @Expose public String description;
        @Expose public String rarity;
    }
    
    /**
     * Relic information
     */
    public static class RelicInfo {
        @Expose public String id;
        @Expose public String name;
        @Expose public String description;
        @Expose public String tier;
        @Expose public int counter;
    }
    
    /**
     * Neow event stage specific information
     */
    public static class NeowStageInfo extends StageSpecificInfo {
        @Expose public String description;
        @Expose public List<String> options = new ArrayList<>();
    }
}
