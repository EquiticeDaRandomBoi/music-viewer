# common-resources/scripts/control_media.ps1
# Usage: control_media.ps1 -Action <play|pause|toggle|next|previous|seek> [-SeekMs <ms>]
param(
    [Parameter(Mandatory)][string]$Action,
    [long]$SeekMs = 0
)

Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

function Await($WinRtTask, $ResultType) {
    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
    $netTask = $asTask.Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    $netTask.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]

try {
    $manager = Await `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    $session = $manager.GetCurrentSession()
    if ($null -eq $session) { exit }

    switch ($Action.ToLower()) {
        'play'     { $session.TryPlayAsync()             | Out-Null }
        'pause'    { $session.TryPauseAsync()            | Out-Null }
        'toggle'   { $session.TryTogglePlayPauseAsync()  | Out-Null }
        'next'     { $session.TrySkipNextAsync()         | Out-Null }
        'previous' { $session.TrySkipPreviousAsync()     | Out-Null }
        'seek'     {
            $ts = [System.TimeSpan]::FromMilliseconds($SeekMs)
            $session.TryChangePlaybackPositionAsync($ts.Ticks) | Out-Null
        }
    }
} catch { }
