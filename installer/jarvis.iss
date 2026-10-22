; ==============================================================================
; JARVIS Dynamic Island — Inno Setup Script
; Builds a production Windows Setup Wizard (.exe)
; ==============================================================================

#define MyAppName "JARVIS Dynamic Island"
#define MyAppVersion "1.4.0"
#define MyAppPublisher "JARVIS Team"
#define MyAppURL "https://github.com/kovvurinandivardhanreddy2007/JARVIS"
#define MyAppExeName "jarvis.exe"

[Setup]
AppId={{8B91FA30-7452-4752-BF01-8A977BEFA3E2}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} v{#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\JARVIS
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=..\build-artifacts
OutputBaseFilename=JARVIS-Island-Setup
SetupIconFile=..\app\src-tauri\icons\icon.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequiredOverridesAllowed=commandline dialog
CloseApplications=force
RestartApplications=no
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName} {#MyAppVersion}
DisableDirPage=no
DisableProgramGroupPage=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "startupicon"; Description: "Launch JARVIS automatically on Windows startup"; GroupDescription: "System Integration:"

[Files]
Source: "..\app\src-tauri\target\release\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startupicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent
