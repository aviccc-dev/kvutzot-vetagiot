; קובץ ההתקנה לחלונות (Inno Setup).
; בודק אם במחשב יש Java בגרסה 11 ומעלה (בעזרת המפעיל, במצב בדיקה):
;   יש — מתקין רק את התוכנה (כ-0.2 מגה). אין — מוריד פעם אחת את סביבת ההרצה (כ-25 מגה) לתיקיית התוכנה.
; הוחלט 11.11.2026: "אני מעדיף בקובץ ההתקנה להכניס את כל זה."
; נבנה ב-GitHub Actions: ISCC /DAppVersion=X /DRuntimeSha=Y kvutzot.iss

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef RuntimeSha
  #define RuntimeSha ""
#endif
#define RuntimeUrl "https://github.com/aviccc-dev/kvutzot-vetagiot/releases/download/desktop-v" + AppVersion + "/runtime-windows-x64.zip"

[Setup]
AppId={{B6E1C9A4-3F2D-4E5B-9C71-2A8D4F6E1B30}
AppName=קבוצות ותגיות
AppVersion={#AppVersion}
AppVerName=קבוצות ותגיות {#AppVersion}
AppPublisher=aviccc
AppPublisherURL=https://github.com/aviccc-dev/kvutzot-vetagiot
DefaultDirName={localappdata}\Programs\KvutzotVeTagiot
DisableProgramGroupPage=yes
DisableDirPage=auto
PrivilegesRequired=lowest
OutputDir=..\..\build
OutputBaseFilename=KvutzotVeTagiot-{#AppVersion}-setup
SetupIconFile=..\res\icon.ico
UninstallDisplayIcon={app}\KvutzotVeTagiot.exe
UninstallDisplayName=קבוצות ותגיות
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "he"; MessagesFile: "compiler:Languages\Hebrew.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\..\build\small\KvutzotVeTagiot\KvutzotVeTagiot.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\build\small\KvutzotVeTagiot\kvutzot.jar"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\קבוצות ותגיות"; Filename: "{app}\KvutzotVeTagiot.exe"
Name: "{autodesktop}\קבוצות ותגיות"; Filename: "{app}\KvutzotVeTagiot.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\KvutzotVeTagiot.exe"; Description: "{cm:LaunchProgram,קבוצות ותגיות}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}\runtime"

[Code]
var
  FoundJava: String;
  NeedRuntime: Boolean;
  DownloadPage: TDownloadWizardPage;

function OnDownloadProgress(const Url, FileName: String; const Progress, ProgressMax: Int64): Boolean;
begin
  Result := True;
end;

function RuntimeInstalled(): Boolean;
begin
  Result := FileExists(AddBackslash(WizardDirValue) + 'runtime\bin\javaw.exe');
end;

{ המפעיל עצמו, במצב בדיקה, מחפש Java במחשב — אותו חיפוש שהוא עושה בכל הפעלה. }
procedure DetectJava();
var
  R: Integer;
  S: AnsiString;
  F: String;
begin
  FoundJava := '';
  try
    ExtractTemporaryFile('KvutzotVeTagiot.exe');
    F := ExpandConstant('{tmp}\found.txt');
    if Exec(ExpandConstant('{tmp}\KvutzotVeTagiot.exe'), '--test "' + F + '"', '', SW_HIDE, ewWaitUntilTerminated, R) then
      if LoadStringFromFile(F, S) then
        if Pos('java=', String(S)) = 1 then FoundJava := String(S);
  except
    FoundJava := '';
  end;
  Log('Java: ' + FoundJava);
end;

procedure InitializeWizard();
begin
  DetectJava();
  DownloadPage := CreateDownloadPage(SetupMessage(msgWizardPreparing), 'מוריד את סביבת ההרצה של Java (פעם אחת בלבד)…', @OnDownloadProgress);
end;

function UpdateReadyMemo(Space, NewLine, MemoUserInfoInfo, MemoDirInfo, MemoTypeInfo, MemoComponentsInfo, MemoGroupInfo, MemoTasksInfo: String): String;
var
  J: String;
begin
  if FoundJava <> '' then
    J := 'Java:' + NewLine + Space + 'נמצאה במחשב — לא יורד שום דבר נוסף.'
  else if RuntimeInstalled() then
    J := 'Java:' + NewLine + Space + 'כבר הותקנה עם התוכנה — לא יורד שום דבר נוסף.'
  else
    J := 'Java:' + NewLine + Space + 'לא נמצאה במחשב Java בגרסה 11 ומעלה.' + NewLine + Space
       + 'תורד עכשיו פעם אחת סביבת הרצה (כ-25 מגה), ותותקן בתוך תיקיית התוכנה בלבד.';
  Result := MemoDirInfo + NewLine + NewLine;
  if MemoTasksInfo <> '' then Result := Result + MemoTasksInfo + NewLine + NewLine;
  Result := Result + J;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if CurPageID <> wpReady then Exit;
  NeedRuntime := ((FoundJava = '') and (not RuntimeInstalled())) or (ExpandConstant('{param:forceruntime|0}') = '1');
  if not NeedRuntime then Exit;
  if WizardSilent then begin
    try
      DownloadTemporaryFile('{#RuntimeUrl}', 'runtime.zip', '{#RuntimeSha}', @OnDownloadProgress);
    except
      Log('download failed: ' + GetExceptionMessage);
      Result := False;
    end;
    Exit;
  end;
  DownloadPage.Clear;
  DownloadPage.Add('{#RuntimeUrl}', 'runtime.zip', '{#RuntimeSha}');
  DownloadPage.Show;
  try
    try
      DownloadPage.Download;
    except
      if not DownloadPage.AbortedByUser then
        SuppressibleMsgBox('ההורדה של סביבת ההרצה נכשלה. כדאי לבדוק את החיבור לאינטרנט ולנסות שוב.' + #13#10#13#10 + GetExceptionMessage,
          mbCriticalError, MB_OK, IDOK);
      Result := False;
    end;
  finally
    DownloadPage.Hide;
  end;
end;

{ פריסת סביבת ההרצה לתוך תיקיית התוכנה: ב-tar של חלונות, ואם לא הצליח — ב-PowerShell. }
procedure CurStepChanged(CurStep: TSetupStep);
var
  R: Integer;
  Zip, Dest, Cmd: String;
begin
  if (CurStep <> ssPostInstall) or (not NeedRuntime) then Exit;
  Zip := ExpandConstant('{tmp}\runtime.zip');
  Dest := ExpandConstant('{app}\runtime');
  WizardForm.StatusLabel.Caption := 'מתקין את סביבת ההרצה של Java…';
  DelTree(Dest, True, True, True);
  ForceDirectories(Dest);
  if (not Exec(ExpandConstant('{sys}\tar.exe'), '-xf "' + Zip + '" -C "' + Dest + '"', '', SW_HIDE, ewWaitUntilTerminated, R))
     or (R <> 0) or (not FileExists(Dest + '\bin\javaw.exe')) then
  begin
    Cmd := '-NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath ' + #39 + Zip + #39
         + ' -DestinationPath ' + #39 + Dest + #39 + ' -Force"';
    Exec('powershell.exe', Cmd, '', SW_HIDE, ewWaitUntilTerminated, R);
  end;
  if not FileExists(Dest + '\bin\javaw.exe') then
    SuppressibleMsgBox('ההתקנה של סביבת ההרצה נכשלה. אפשר להריץ שוב את קובץ ההתקנה.', mbError, MB_OK, IDOK);
end;
