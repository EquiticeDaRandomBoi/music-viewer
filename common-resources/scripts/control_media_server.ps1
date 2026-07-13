# Long-lived process: reads one command per line from stdin, dispatches SMTC immediately.
# Format: "toggle" | "play" | "pause" | "next" | "previous" | "seek <ms>"
param()

Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

function Await { param($WinRtTask, $ResultType)
    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
    $netTask = $asTask.Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    $netTask.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]

$manager = $null
try {
    $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
                     ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch { exit 1 }

while ($true) {
    $line = [Console]::In.ReadLine()
    if ($null -eq $line) { break }
    $line = $line.Trim()
    if ($line.Length -eq 0) { continue }

    try {
        $session = $manager.GetCurrentSession()
        if ($null -eq $session) { continue }

        $parts = $line -split '\s+', 2
        switch ($parts[0].ToLower()) {
            'toggle'   { $session.TryTogglePlayPauseAsync() | Out-Null }
            'play'     { $session.TryPlayAsync()            | Out-Null }
            'pause'    { $session.TryPauseAsync()           | Out-Null }
            'next'     { $session.TrySkipNextAsync()        | Out-Null }
            'previous' { $session.TrySkipPreviousAsync()    | Out-Null }
            'seek' {
                if ($parts.Length -ge 2) {
                    $ts = [System.TimeSpan]::FromMilliseconds([long]$parts[1])
                    $session.TryChangePlaybackPositionAsync($ts.Ticks) | Out-Null
                }
            }
        }
    } catch { }
}
