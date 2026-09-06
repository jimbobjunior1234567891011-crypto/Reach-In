# Drives a running Minecraft client from the terminal: screenshots, key presses,
# mouse moves and clicks. Used to smoke test Reach-In without a human at the keyboard.
#
# CopyFromScreen returns black for the GL window, so screenshots go through
# PrintWindow with PW_RENDERFULLCONTENT (2). The process must be DPI aware or every
# coordinate is scaled wrong.

Add-Type -AssemblyName System.Drawing, System.Windows.Forms

if (-not ("Native" -as [type])) {
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Native {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hwnd, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hwnd, out RECT r);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hwnd, ref POINT p);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hwnd, int cmd);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint x, uint y, uint d, IntPtr e);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, IntPtr e);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
}
"@
}

[void][Native]::SetProcessDPIAware()

$MOUSE_LEFT_DOWN = 0x0002
$MOUSE_LEFT_UP = 0x0004
$MOUSE_RIGHT_DOWN = 0x0008
$MOUSE_RIGHT_UP = 0x0010
$KEY_UP = 0x0002

function Get-McWindow {
    $proc = Get-Process | Where-Object { $_.MainWindowTitle -like "Minecraft*" } | Select-Object -First 1
    if (-not $proc) { throw "no Minecraft window found" }
    return $proc.MainWindowHandle
}

function Focus-Mc {
    $hwnd = Get-McWindow
    [void][Native]::ShowWindow($hwnd, 9)
    [void][Native]::SetForegroundWindow($hwnd)
    Start-Sleep -Milliseconds 400
    return $hwnd
}

# Captures the window, then crops to the client area so image pixels line up
# one-to-one with the coordinates Click-Mc takes.
function Save-Shot([string]$Path) {
    $hwnd = Get-McWindow
    $win = New-Object Native+RECT
    [void][Native]::GetWindowRect($hwnd, [ref]$win)
    $w = $win.Right - $win.Left
    $h = $win.Bottom - $win.Top

    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $hdc = $g.GetHdc()
    [void][Native]::PrintWindow($hwnd, $hdc, 2)
    $g.ReleaseHdc($hdc)
    $g.Dispose()

    $client = New-Object Native+RECT
    [void][Native]::GetClientRect($hwnd, [ref]$client)
    $origin = New-Object Native+POINT
    [void][Native]::ClientToScreen($hwnd, [ref]$origin)
    $offsetX = $origin.X - $win.Left
    $offsetY = $origin.Y - $win.Top
    $cw = $client.Right - $client.Left
    $ch = $client.Bottom - $client.Top

    $crop = New-Object System.Drawing.Rectangle $offsetX, $offsetY, $cw, $ch
    $out = $bmp.Clone($crop, $bmp.PixelFormat)
    $out.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    $bmp.Dispose()
    return $Path
}

# Client-area size, which is what Minecraft's own coordinates are relative to.
function Get-ClientInfo {
    $hwnd = Get-McWindow
    $rect = New-Object Native+RECT
    [void][Native]::GetClientRect($hwnd, [ref]$rect)
    $origin = New-Object Native+POINT
    [void][Native]::ClientToScreen($hwnd, [ref]$origin)
    return [pscustomobject]@{
        Width  = $rect.Right - $rect.Left
        Height = $rect.Bottom - $rect.Top
        ScreenX = $origin.X
        ScreenY = $origin.Y
    }
}

function Move-McMouse([int]$X, [int]$Y) {
    $info = Get-ClientInfo
    [void][Native]::SetCursorPos($info.ScreenX + $X, $info.ScreenY + $Y)
    Start-Sleep -Milliseconds 120
}

function Click-Mc([int]$X, [int]$Y, [string]$Button = "left") {
    Move-McMouse $X $Y
    if ($Button -eq "right") {
        [Native]::mouse_event($MOUSE_RIGHT_DOWN, 0, 0, 0, [IntPtr]::Zero)
        Start-Sleep -Milliseconds 60
        [Native]::mouse_event($MOUSE_RIGHT_UP, 0, 0, 0, [IntPtr]::Zero)
    } else {
        [Native]::mouse_event($MOUSE_LEFT_DOWN, 0, 0, 0, [IntPtr]::Zero)
        Start-Sleep -Milliseconds 60
        [Native]::mouse_event($MOUSE_LEFT_UP, 0, 0, 0, [IntPtr]::Zero)
    }
    Start-Sleep -Milliseconds 250
}

function Press-Key([byte]$Vk, [int]$HoldMs = 60) {
    [Native]::keybd_event($Vk, 0, 0, [IntPtr]::Zero)
    Start-Sleep -Milliseconds $HoldMs
    [Native]::keybd_event($Vk, 0, $KEY_UP, [IntPtr]::Zero)
    Start-Sleep -Milliseconds 150
}

# Types printable ASCII through the shift-aware virtual key table.
function Type-Text([string]$Text) {
    $shifted = @{
        '!' = '1'; '@' = '2'; '#' = '3'; '$' = '4'; '%' = '5'; '^' = '6'; '&' = '7'
        '*' = '8'; '(' = '9'; ')' = '0'; '_' = '-'; '+' = '='; '{' = '['; '}' = ']'
        ':' = ';'; '"' = "'"; '<' = ','; '>' = '.'; '?' = '/'; '~' = '`'; '|' = '\'
    }
    foreach ($ch in $Text.ToCharArray()) {
        $needShift = $false
        $c = $ch
        if ($shifted.ContainsKey([string]$ch)) { $needShift = $true; $c = $shifted[[string]$ch][0] }
        elseif ([char]::IsUpper($ch)) { $needShift = $true }

        $vk = switch -regex ([string]$c) {
            '^[a-zA-Z]$' { [byte][char]([string]$c).ToUpper() }
            '^[0-9]$'    { [byte][char]$c }
            '^ $'        { [byte]0x20 }
            '^-$'        { [byte]0xBD }
            '^=$'        { [byte]0xBB }
            '^\[$'       { [byte]0xDB }
            '^\]$'       { [byte]0xDD }
            '^;$'        { [byte]0xBA }
            "^'$"        { [byte]0xDE }
            '^,$'        { [byte]0xBC }
            '^\.$'       { [byte]0xBE }
            '^/$'        { [byte]0xBF }
            '^`$'        { [byte]0xC0 }
            '^\\$'       { [byte]0xDC }
            default      { [byte]0 }
        }
        if ($vk -eq 0) { continue }
        if ($needShift) { [Native]::keybd_event(0x10, 0, 0, [IntPtr]::Zero) }
        [Native]::keybd_event($vk, 0, 0, [IntPtr]::Zero)
        Start-Sleep -Milliseconds 25
        [Native]::keybd_event($vk, 0, $KEY_UP, [IntPtr]::Zero)
        if ($needShift) { [Native]::keybd_event(0x10, 0, $KEY_UP, [IntPtr]::Zero) }
        Start-Sleep -Milliseconds 25
    }
}

$VK_RETURN = 0x0D
$VK_ESCAPE = 0x1B
$VK_T = 0x54
$VK_E = 0x45
$VK_SHIFT = 0x10

# Opens chat with T, types the line, sends it. Pass the leading slash yourself.
function Send-Command([string]$Command) {
    Press-Key $VK_T 80
    Start-Sleep -Milliseconds 400
    Type-Text $Command
    Start-Sleep -Milliseconds 250
    Press-Key $VK_RETURN 80
    Start-Sleep -Milliseconds 500
}

function Shift-Click([int]$X, [int]$Y) {
    Move-McMouse $X $Y
    [Native]::keybd_event($VK_SHIFT, 0, 0, [IntPtr]::Zero)
    Start-Sleep -Milliseconds 60
    [Native]::mouse_event(0x0002, 0, 0, 0, [IntPtr]::Zero)
    Start-Sleep -Milliseconds 60
    [Native]::mouse_event(0x0004, 0, 0, 0, [IntPtr]::Zero)
    Start-Sleep -Milliseconds 60
    [Native]::keybd_event($VK_SHIFT, 0, 0x0002, [IntPtr]::Zero)
    Start-Sleep -Milliseconds 250
}
