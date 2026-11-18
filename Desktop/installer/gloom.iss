; ==============================================================================
; GLOOM Dynamic Island — Inno Setup Script
; Builds a production Windows Setup Wizard (.exe)
; ==============================================================================

#define MyAppName "GLOOM Dynamic Island"
#define MyAppVersion "2.7.0"
#define MyAppPublisher "GLOOM Team"
#define MyAppURL "https://github.com/NandiVardhan2007/GLOOM"
#define MyAppExeName "gloom.exe"
#define MyAppId "{A786F351-4E89-4B52-9E11-23F1C45DE9A8}"

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
UsePreviousAppDir=yes
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
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent
Filename: "{app}\{#MyAppExeName}"; Flags: nowait; Check: WizardSilent

[Code]
// Helper to retrieve previous uninstall string
function GetUninstallString(): String;
var
  sUninstPath: String;
begin
  sUninstPath := '';
  // Check HKCU (CurrentUser) first - default for lowest privileges
  if not RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppId}_is1', 'UninstallString', sUninstPath) then
  begin
    // Check HKLM (LocalMachine) fallback
    RegQueryStringValue(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#MyAppId}_is1', 'UninstallString', sUninstPath);
  end;
  Result := sUninstPath;
end;

// Kill any running gloom.exe and execute previous uninstaller silently
function UnInstallOldVersion(): Integer;
var
  sUninstPath: String;
  iResultCode: Integer;
begin
  Result := 0;
  // Ensure any running gloom process is terminated
  Exec('taskkill.exe', '/F /IM {#MyAppExeName}', '', SW_HIDE, ewWaitUntilTerminated, iResultCode);
  Sleep(250);

  sUninstPath := GetUninstallString();
  if sUninstPath <> '' then
  begin
    sUninstPath := RemoveQuotes(sUninstPath);
    if FileExists(sUninstPath) then
    begin
      // Execute uninstaller silently with no restart and suppressed message boxes
      Exec(sUninstPath, '/VERYSILENT /NORESTART /SUPPRESSMSGBOXES', '', SW_HIDE, ewWaitUntilTerminated, iResultCode);
      Result := iResultCode;
      Sleep(500);
    end;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  appDir: String;
begin
  if CurStep = ssInstall then
  begin
    // Step 1: Remove old version completely via its uninstaller
    UnInstallOldVersion();

    // Step 2: Clean up any lingering leftovers in {app} before installing the new files
    appDir := ExpandConstant('{app}');
    if DirExists(appDir) then
    begin
      DelTree(appDir + '\*', False, True, True);
    end;
  end;
end;
