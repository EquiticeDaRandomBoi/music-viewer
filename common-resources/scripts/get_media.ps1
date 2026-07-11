# common-resources/scripts/get_media.ps1
# Returns JSON with current SMTC media session info, or {} if nothing playing.

Add-Type -AssemblyName System.Runtime.WindowsRuntime

# Helper to await a WinRT IAsyncOperation<T>
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

# Load WinRT type
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]

try {
    $manager = Await `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch {
    Write-Output '{}'
    exit
}

$session = $manager.GetCurrentSession()
if ($null -eq $session) {
    Write-Output '{}'
    exit
}

try {
    $props    = Await ($session.TryGetMediaPropertiesAsync()) `
                      ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $timeline = $session.GetTimelineProperties()
    $playback = $session.GetPlaybackInfo()
} catch {
    Write-Output '{}'
    exit
}

$thumbPath = ''
if ($null -ne $props.Thumbnail) {
    try {
        [void][Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage,ContentType=WindowsRuntime]
        $stream    = Await ($props.Thumbnail.OpenReadAsync()) `
                           ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        $tempDir   = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), 'musicplayer')
        [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
        $tempPath  = [System.IO.Path]::Combine($tempDir, 'thumb.png')
        $netStream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
        $file      = [System.IO.File]::OpenWrite($tempPath)
        $netStream.CopyTo($file)
        $file.Close()
        $thumbPath = $tempPath
    } catch { }
}

$status = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]
$isPlaying = ($playback.PlaybackStatus -eq $status::Playing)

@{
    title         = [string]$props.Title
    artist        = [string]$props.Artist
    isPlaying     = $isPlaying
    positionMs    = [long]$timeline.Position.TotalMilliseconds
    durationMs    = [long]$timeline.EndTime.TotalMilliseconds
    thumbnailPath = $thumbPath
    sourceApp     = [string]$session.SourceAppUserModelId
} | ConvertTo-Json -Compress
