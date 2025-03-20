# IntelliJ AI Prompt Generator

An IntelliJ IDEA plugin that helps you create context-rich prompts for AI assistants by easily including code from your project.

<img width="793" alt="Screenshot_3" src="https://github.com/user-attachments/assets/2a244cc6-c93d-47f1-941e-3f524f045111" />

## Features

- **Smart Context Building**: Select Java classes and other files from your project to include in AI prompts
- **Multiple Context Sections**: Separate "Project Context" and "Additional Context" for better organization
- **Class-Only Mode**: Toggle between class-only browsing and general file selection
- **Multi-Selection Support**: Add multiple files at once, including entire directories recursively
- **File Type Icons**: Visual indicators to easily identify different file types
- **Template System**: Save and load prompt templates to reuse configurations across different projects
- **Convenient Access**: Right-click in any file to open the prompt generator
- **Persistent Settings**: Your selections, preferences, and templates are saved across IDE sessions
- **Customizable Headers**: Configure section headers to match your preferred AI prompt format

## Installation

1. Download the latest release (.zip file) from the [Releases page](https://github.com/Keksuccino/IntelliJ-AI-Prompt-Generator/releases)
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

### Using Templates

Templates allow you to save and reuse your prompt configurations:

1. **Save a Template**:
   - Set up your prompt with the desired files and settings
   - Click "Save Current as Template"
   - Enter a name for your template
   
2. **Load a Template**:
   - Select a previously saved template from the dropdown
   - All settings and files will be restored automatically
   
3. **Delete a Template**:
   - Select a template from the dropdown
   - Click "Delete Template" to remove it

Templates are perfect for different scenarios like "Bug Fixing", "Code Review", "Feature Implementation", etc.

### Smart Selection Tips

- **Project Files**: Add core files that are central to your question
- **Additional Context**: Include supporting files or dependencies for additional context
- **Class-Only Mode**: Perfect for navigating Java class hierarchies and library dependencies
- **Directory Selection**: Add all files in a package or module with a single selection

## Configuration

Access plugin settings via the gear icon in the prompt generator dialog:

- **Prompt Section Headers**: Customize the prompt section headers for project files and additional context

## Copyright & License

Copyright © 2025 Keksuccino.

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
