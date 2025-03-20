# IntelliJ AI Prompt Generator

An IntelliJ IDEA plugin that helps you create context-rich prompts for AI assistants by easily including code from your project.

<img width="754" alt="Screenshot_2" src="https://github.com/user-attachments/assets/bb406dd9-6ed6-493b-ad2c-84418ee3ec14" />

## Features

- **Smart Context Building**: Select Java classes and other files from your project to include in AI prompts
- **Multiple Context Sections**: Separate "Project Context" and "Additional Context" for better organization
- **Class-Only Mode**: Toggle between class-only browsing and general file selection
- **Multi-Selection Support**: Add multiple files at once, including entire directories recursively
- **File Type Icons**: Visual indicators to easily identify different file types
- **Convenient Access**: Right-click in any file to open the prompt generator
- **Persistent Settings**: Your selections and preferences are saved across IDE sessions
- **Customizable Headers**: Configure section headers to match your preferred AI prompt format

## Installation

### Manual Installation
1. Download the latest release (.zip file) from the [Releases page](https://github.com/yourusername/intellij-ai-prompt-generator/releases)
2. In IntelliJ IDEA, go to Settings/Preferences → Plugins
3. Click the gear icon and select "Install Plugin from Disk..."
4. Navigate to the downloaded .zip file and click "OK"
5. Restart IntelliJ IDEA when prompted

## Usage

### Creating a Prompt

1. **Open the Generator**: Right-click in any file in your project and select "Generate AI Prompt with Context"
2. **Add Files**:
   - Use the "Add File" button to select general files from your project
   - Toggle "Java Classes Only" to use the class browser for easier navigation of Java classes
   - Select directories to automatically add all files within them
3. **Customize Prompt**:
   - Enter your initial prompt text in the "Prompt Head" field
   - Organize files between "Project Context" and "Additional Context" sections
4. **Generate Prompt**: Click "Generate and Copy to Clipboard" to create your formatted prompt
5. **Paste into AI**: The formatted prompt is now ready to paste into your AI assistant of choice

### Smart Selection Tips

- **Project Files**: Add core files that are central to your question
- **Additional Context**: Include supporting files or dependencies for additional context
- **Class-Only Mode**: Perfect for navigating Java class hierarchies and library dependencies
- **Directory Selection**: Add all files in a package or module with a single selection

## Configuration

Access plugin settings via the gear icon in the prompt generator dialog:

- **Prompt Section Headers**: Customize the prompt section headers for project files and additional context
- **Default Prompt**: Set a default prompt head that appears each time you open the generator

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
