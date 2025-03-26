package llmthespire.game;

/**
 * Enumeration of different game stages
 */
public enum GameStageType {
    MAP("Map Selection"),
    BATTLE("Combat"),
    CAMPFIRE("Rest Site"),
    SHOP("Shop"),
    CHEST("Treasure"),
    EVENT("Event"),
    REWARD("Reward"),
    CARD_SELECT("Card Selection"),
    NEOW("Neow's Lament");
    
    private final String displayName;
    
    GameStageType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
} 