; ==============================================================================
; GLOOM Dynamic Island — Inno Setup Script
; Builds a production Windows Setup Wizard (.exe)
; ==============================================================================

#define MyAppName "GLOOM Dynamic Island"
#define MyAppVersion "1.9.6"
#define MyAppPublisher "GLOOM Team"
#define MyAppURL "https://github.com/NandiVardhan2007/GLOOM"
#define MyAppExeName "gloom.exe"

[Setup]
AppId={{A786F351-4E89-4B52-9E11-23F1C45DE9A8}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} v{#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\GLOOM
UsePreviousAppDir=no
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=..\build-artifacts
OutputBaseFilename=GLOOM-Island-Setup
SetupIconFile=..\Desktop\src-tauri\icons\icon.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
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
Name: "startupicon"; Description: "Launch GLOOM automatically on Windows startup"; GroupDescription: "System Integration:"; Flags: unchecked

[Files]
Source: "..\Desktop\src-tauri\target\release\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startupicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall
