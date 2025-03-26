# LLM-Powered Slay the Spire Bot

This mod uses OpenAI's language models via LangChain4j to enable AI-powered autoplay for Slay the Spire.

## Features

- **AI-Powered Gameplay**: Let the LLM analyze the game state and make strategic decisions about card plays
- **Configurable Settings**: Choose which LLM model to use, adjust temperature and more
- **Simple Toggle**: Easily enable or disable AI assistance during combat

## Installation

1. Install [ModTheSpire](https://github.com/kiooeht/ModTheSpire)
2. Install [BaseMod](https://github.com/daviscook477/BaseMod)
3. Install this mod
4. Launch the game via ModTheSpire

## Configuration

Before using the mod, you need to configure your OpenAI API key:

1. From the main menu, click "Mods"
2. Click on the "LLM-Powered Autoplay" mod
3. Enter your OpenAI API key
4. Configure model settings as desired
5. Enable LLM features using the toggle

## Usage

Once configured:

1. During combat, you'll see an "AI OFF" button near the end turn button
2. Click it to toggle AI assistance (it will change to "AI ON")
3. The AI will automatically analyze the game state and play cards strategically
4. You can take control back at any time by clicking the button again to turn it off

## How It Works

The mod captures the current game state including:
- Your hand, draw pile, and discard pile
- Enemy types, health, and intents
- Your character's stats and current energy
- Various effects and powers in play

This information is sent to the LLM, which analyzes it and decides the optimal card plays in sequence.

## Requirements

- OpenAI API key (paid service)
- Internet connection for API calls
- ModTheSpire and BaseMod

## Contributing

Feel free to contribute to this project by opening issues or submitting pull requests on GitHub.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
