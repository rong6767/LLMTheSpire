# LLM-Powered Slay the Spire Bot

This mod uses OpenAI's language models via LangChain4j to enable AI-powered autoplay for Slay the Spire. The AI analyzes the game state and makes strategic decisions about card plays, potion usage, and other game actions.

## Features

- **AI-Powered Gameplay**: Let the LLM analyze the game state and make strategic decisions about:
  - Card plays and targeting
  - Potion usage
  - Map navigation
  - Event choices
  - Shop purchases
  - Rest site decisions
  - Reward selection
- **Configurable Settings**: 
  - Choose between different LLM models (GPT-3.5, GPT-4, etc.)
  - Adjust temperature and other model parameters
  - Configure system prompts
  - Set conversation saving options
- **Conversation Logging**: Save AI decision-making history for analysis
- **Multi-Stage Support**: AI can handle all game phases including:
  - Combat
  - Map navigation
  - Events
  - Shops
  - Rest sites
  - Card rewards
  - Neow's blessing

## Installation

1. Install [ModTheSpire](https://github.com/kiooeht/ModTheSpire)
2. Install [BaseMod](https://github.com/daviscook477/BaseMod)
3. Install this mod
4. Launch the game via ModTheSpire

## Configuration

The mod will automatically create a default configuration file when you first launch it. You can find the configuration file at:
   - Windows: `%USERPROFILE%\AppData\Roaming\SlayTheSpire\llm_conversations\llm_config.json`
   - macOS: `~/Library/Preferences/SlayTheSpire/llm_conversations/llm_config.json`
   - Linux: `~/.config/SlayTheSpire/llm_conversations/llm_config.json`

The default configuration includes:
```json
{
  "apiConfigs": {
    "OpenAI": {
      "name": "OpenAI",
      "enabled": true,
      "apiType": "OPENAI",
      "apiKey": "",
      "apiEndpoint": "https://api.openai.com/v1/chat/completions",
      "model": "gpt-4",
      "temperature": 0.7,
      "maxTokens": 1024
    }
  },
  "activeApiName": "OpenAI",
  "saveConversations": true,
  "systemPrompt": "You are an AI assistant playing Slay the Spire...",
  "extraParams": {}
}
```

To use the mod:
1. Edit the `llm_config.json` file and add your API key to the desired provider
2. The mod supports multiple API providers:
   - OpenAI (GPT-3.5, GPT-4)
   - Anthropic (Claude)
   - DeepSeek
   - Azure OpenAI
3. You can switch between providers by changing the `activeApiName` in the config file

## Usage

Once configured:

1. During any game phase, you'll see an "AI ON" in the middle of the top panel
2. The AI will automatically analyze the game state and make decisions

## How It Works

The mod captures the current game state including:
- Your hand, draw pile, discard pile, and exhaust pile
- Enemy types, health, intents, and powers
- Your character's stats, energy, and relics
- Potions and their effects
- Current game phase and available actions
- Map information and available paths
- Event options and descriptions
- Shop inventory and prices
- Reward choices

This information is sent to the LLM, which analyzes it and decides the optimal actions to take. The AI's reasoning and chosen actions are logged for review.

## Requirements

- OpenAI API key (paid service) or other supported LLM provider
- Internet connection for API calls
- ModTheSpire and BaseMod
- Slay the Spire (duh!)

## Contributing

Feel free to contribute to this project by:
1. Opening issues for bugs or feature requests
2. Submitting pull requests with improvements
3. Suggesting better prompts or configurations
4. Adding support for additional LLM providers

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Thanks to the ModTheSpire and BaseMod teams for making this possible
- OpenAI for providing the language models
- The Slay the Spire community for their support and feedback
