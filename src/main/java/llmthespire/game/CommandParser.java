package llmthespire.game;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parser for game action commands
 * Provides a more robust parsing mechanism than regex
 */
public class CommandParser {
    private static final Logger logger = LogManager.getLogger(CommandParser.class.getName());
    
    /**
     * Command type enum
     */
    public enum CommandType {
        PLAY_CARD,
        USE_POTION,
        END_TURN,
        CHOOSE_OPTION,
        PROCEED_ON_MAP,
        SELECT_CARD,
        TAKE_REWARD,
        BUY_CARD,
        BUY_RELIC,
        BUY_POTION,
        PURGE_CARD,
        REST,
        SMITH, 
        LIFT,
        DIG,
        RECALL,
        SKIP_REWARD,
        LEAVE_SHOP,
        CANCEL,
        UNKNOWN
    }
    
    /**
     * Command class to hold parsed information
     */
    public static class Command {
        private CommandType type;
        private List<Integer> parameters = new ArrayList<>();
        private String rawCommand;
        
        public Command(CommandType type, String rawCommand) {
            this.type = type;
            this.rawCommand = rawCommand;
        }
        
        public CommandType getType() {
            return type;
        }
        
        public void addParameter(int param) {
            parameters.add(param);
        }
        
        public int getParameterCount() {
            return parameters.size();
        }
        
        public int getParameter(int index) {
            if (index < 0 || index >= parameters.size()) {
                return -1;
            }
            return parameters.get(index);
        }
        
        public String getRawCommand() {
            return rawCommand;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(type.name());
            for (int param : parameters) {
                sb.append(" ").append(param);
            }
            return sb.toString();
        }
    }
    
    /**
     * Parse a command string into a structured Command object
     * @param commandStr The command string to parse
     * @return A Command object with type and parameters
     */
    public static Command parse(String commandStr) {
        if (commandStr == null || commandStr.isEmpty()) {
            return new Command(CommandType.UNKNOWN, "");
        }
        
        // Standardize format: remove brackets, extra spaces, and convert to uppercase
        String normalized = commandStr.trim().toUpperCase();
        normalized = normalized.replaceAll("\\s*\\[(\\d+)\\]", " $1"); // Replace [n] with space-n
        normalized = normalized.replaceAll("\\s+", " "); // Normalize spaces
        
        logger.debug("Normalized command: " + normalized);
        
        String[] parts = normalized.split(" ");
        if (parts.length == 0) {
            return new Command(CommandType.UNKNOWN, commandStr);
        }
        
        // Determine command type from first part
        CommandType type;
        try {
            // Try to match command exactly
            type = CommandType.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            // If it fails, try to find a match with combined words
            // For example, "PLAY_CARD" might be passed as CommandType.PLAY_CARD
            String combined = parts[0];
            if (parts.length > 1) {
                combined = parts[0] + "_" + parts[1];
                try {
                    type = CommandType.valueOf(combined);
                    // If we matched a combined command, adjust the parts array
                    parts = Arrays.copyOfRange(parts, 2, parts.length);
                } catch (IllegalArgumentException e2) {
                    logger.warn("Unknown command type: " + parts[0]);
                    type = CommandType.UNKNOWN;
                    parts = Arrays.copyOfRange(parts, 1, parts.length);
                }
            } else {
                logger.warn("Unknown command type: " + parts[0]);
                type = CommandType.UNKNOWN;
                parts = new String[0];
            }
        }
        
        Command command = new Command(type, commandStr);
        
        // Parse parameters (should be integers)
        for (String part : parts) {
            try {
                int param = Integer.parseInt(part);
                command.addParameter(param);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse parameter: " + part);
            }
        }
        
        logger.debug("Parsed command: " + command);
        return command;
    }
} 